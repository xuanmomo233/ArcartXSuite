package xuanmo.arcartxsuite.eventpacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/** Owns the local event sources for one rule configuration on the server thread. */
final class LocalTriggerRuntime implements Listener, AutoCloseable {
    enum CleanupRequest { QUEUED, BUSY, DISABLED }
    private record Counter(LocalTriggerSettings.KillRule rule, NamespacedKey key) {}
    private record Samples(UUID connection, long generation, String[] values) {}

    private final LocalTriggerSettings settings;
    private final RealtimeRules realtime;
    private final PlaceholderResolverAPI placeholders;
    private final ScriptConditionEvaluator scripts;
    private final Logger logger;
    private final Map<EntityType, List<Counter>> byType = new EnumMap<>(EntityType.class);
    private final List<Counter> byMythicId = new ArrayList<>();
    private final Map<UUID, Samples> previous = new HashMap<>();
    private final EntityCleanup cleanup;
    private List<UUID> cycle = List.of();
    private int playerIndex;
    private int sourceIndex;
    private long clock;
    private long nextCycle;
    private long errors;
    private ScheduledTask tickTask;
    private ChemdahTriggers chemdah;

    LocalTriggerRuntime(LocalTriggerSettings settings, RealtimeRules realtime, PlaceholderResolverAPI placeholders,
                        ScriptConditionEvaluator scripts, Logger logger) {
        this.settings = settings;
        this.realtime = realtime;
        this.placeholders = placeholders;
        this.scripts = scripts;
        this.logger = logger;
        cleanup = settings.cleanup() == null ? null : new EntityCleanup(settings.cleanup());
    }

    void start(JavaPlugin plugin, SchedulerAPI scheduler) {
        for (var rule : settings.kills()) {
            var counter = new Counter(rule, new NamespacedKey(plugin, "eventpacket/kills/" + rule.id()));
            if (rule.type() == null) byMythicId.add(counter);
            else byType.computeIfAbsent(rule.type(), ignored -> new ArrayList<>()).add(counter);
        }
        if (settings.quit() || !settings.kills().isEmpty() || !settings.samples().isEmpty()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        if (!settings.chemdahEvents().isEmpty()) {
            chemdah = new ChemdahTriggers(plugin, logger, settings.chemdahEvents(), (event, player, values) ->
                realtime.offer(LocalTriggerSettings.chemdahTopic(event), AuthAccess.snapshot(player.getUniqueId()), values));
        }
        if (cleanup != null || !settings.samples().isEmpty()) {
            tickTask = scheduler.scheduleAtFixedRate(plugin, this::tick, 1L, 1L);
        }
    }

    CleanupRequest requestCleanup(IntConsumer result) {
        if (cleanup == null || tickTask == null) return CleanupRequest.DISABLED;
        return cleanup.request(clock, result) ? CleanupRequest.QUEUED : CleanupRequest.BUSY;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        previous.remove(id);
        if (settings.quit()) {
            realtime.offer(LocalTriggerSettings.QUIT, AuthAccess.snapshot(id),
                Map.of("player_name", player.getName(), "player_uuid", id.toString(), "world", player.getWorld().getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        var entity = event.getEntity();
        var candidates = byType.getOrDefault(entity.getType(), List.of());
        if (candidates.isEmpty() && byMythicId.isEmpty()) return;
        Player player = entity.getKiller();
        if (player == null || entity instanceof Player) return;
        var session = AuthAccess.snapshot(player.getUniqueId());
        if (!session.admitted()) return;
        String world = entity.getWorld().getName();
        String mythic = !byMythicId.isEmpty() || candidates.stream().anyMatch(entry -> !entry.rule().mythicMob().isEmpty())
            ? EntityCombatMetadata.resolveMythicMobId(entity) : "";
        for (var counter : candidates) count(player, session, counter, world, entity.getType(), mythic);
        for (var counter : byMythicId) count(player, session, counter, world, entity.getType(), mythic);
    }

    private void count(Player player, AuthSnapshot session, Counter counter, String world, EntityType type, String mythic) {
        var rule = counter.rule();
        if ((!rule.worlds().isEmpty() && !rule.worlds().contains(world))
            || (!rule.mythicMob().isEmpty() && !rule.mythicMob().equalsIgnoreCase(mythic))) return;
        var data = player.getPersistentDataContainer();
        int total = data.getOrDefault(counter.key(), PersistentDataType.INTEGER, 0);
        if (!rule.repeat() && total >= rule.count()) return;
        int next = Math.min(total + 1, rule.count());
        if (next >= rule.count()) {
            boolean queued = realtime.offer(rule.topic(), session, Map.of("world", world,
                "entity_type", type.name(), "mythic_mob", mythic == null ? "" : mythic, "kill_count", Integer.toString(next)));
            if (!queued) return;
            if (rule.repeat()) next = 0;
        }
        data.set(counter.key(), PersistentDataType.INTEGER, next);
    }

    private void tick() {
        clock++;
        try {
            if (cleanup != null) cleanup.tick(clock);
            if (settings.samples().isEmpty()) return;
            if (playerIndex == cycle.size() && clock >= nextCycle) {
                cycle = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
                playerIndex = 0;
                sourceIndex = 0;
                nextCycle = clock + settings.sampleInterval();
            }
            long start = System.nanoTime();
            for (int work = 0; playerIndex < cycle.size() && work < settings.samplesPerTick()
                    && (work == 0 || System.nanoTime() - start < 750_000L); work++) {
                sampleNext();
            }
            if (playerIndex == cycle.size()) {
                cycle = List.of();
                playerIndex = 0;
            }
        } catch (RuntimeException | LinkageError failure) {
            report(failure);
        }
    }

    private void sampleNext() {
        UUID id = cycle.get(playerIndex);
        Player player = Bukkit.getPlayer(id);
        var session = AuthAccess.snapshot(id);
        if (player == null || !player.isOnline() || !session.admitted()) {
            previous.remove(id);
            sourceIndex = 0;
            playerIndex++;
            return;
        }
        var state = previous.get(id);
        if (state == null || state.generation() != session.generation()
            || !java.util.Objects.equals(state.connection(), session.connectionId())) {
            state = new Samples(session.connectionId(), session.generation(), new String[settings.samples().size()]);
            previous.put(id, state);
        }
        int index = sourceIndex++;
        if (sourceIndex == settings.samples().size()) {
            playerIndex++;
            sourceIndex = 0;
        }
        var source = settings.samples().get(index);
        try {
            String current = sample(player, source);
            String before = state.values()[index];
            state.values()[index] = current;
            for (var rule : source.rules()) {
                if (rule.fires(before, current)) {
                    realtime.offer(rule.topic(), session, Map.of("old_value", before, "new_value", current,
                        "placeholder", source.placeholder()));
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            state.values()[index] = null;
            report(failure);
        }
    }

    private String sample(Player player, LocalTriggerSettings.SampleSource source) {
        if (source.script() != null) return Boolean.toString(scripts.passes(player, List.of(source.script())));
        if (!placeholders.available()) return null;
        String value = ChatColor.stripColor(placeholders.applyPlaceholders(player, source.placeholder()));
        if (value == null || value.isBlank() || value.length() > 1024 || value.strip().equals(source.placeholder())) return null;
        return value.strip();
    }

    private void report(Throwable failure) {
        if (++errors == 1 || errors % 100 == 0) logger.warning("EventPacket 本服事件处理失败 · " + failure.getMessage());
    }

    @Override public void close() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        if (cleanup != null) cleanup.clear(clock);
        if (chemdah != null) chemdah.close();
        if (settings.quit() || !settings.kills().isEmpty() || !settings.samples().isEmpty()) HandlerList.unregisterAll(this);
        previous.clear();
        cycle = List.of();
    }
}
