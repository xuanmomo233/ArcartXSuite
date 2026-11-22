package xuanmo.arcartxsuite.combateffect.chronos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.packet.CombatEffectPacketService;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;

/** Owns the UIs and pending packets of one Chronos skill action per player. */
public final class SkillEffectService implements Listener {
    public static final String HUD_ID = "AXS:skill_effect_hud";
    private static final String HANDLER_ID = "axs:play_effect";

    private record Action(UUID id, String profession, String slot, String skill, int level) {
        Map<String, String> variables() {
            return Map.of("action_instance_id", id.toString(), "profession_id", Objects.toString(profession, ""),
                "slot_id", Objects.toString(slot, ""), "skill_id", Objects.toString(skill, ""),
                "skill_level", Integer.toString(level));
        }
    }

    private record Rule(String skill, String profession, String slot, List<String> start,
                        String hudText, List<String> cleanup) {
        Rule(ConfigurationSection section) {
            this(section.getString("skill", "").trim(), section.getString("profession", "").trim(),
                section.getString("slot", "").trim(), packetIds(section, "start-packets"),
                section.getString("hud-text", ""), packetIds(section, "cleanup-packets"));
        }

        boolean matches(Action action) {
            return matches(skill, action.skill()) && matches(profession, action.profession()) && matches(slot, action.slot());
        }

        private static boolean matches(String configured, String actual) {
            return configured.isEmpty() || "*".equals(configured) || configured.equals(actual);
        }

        private static List<String> packetIds(ConfigurationSection section, String key) {
            return section.getStringList(key).stream().map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
        }
    }

    private static final class Session {
        final Player player;
        final Action action;
        final Rule rule;
        final Map<String, View> views = new LinkedHashMap<>();
        final Map<String, Long> lastTimelinePacket = new HashMap<>();
        final Map<String, Integer> executionIndices = new HashMap<>();
        String state;
        UUID executionState;
        boolean entered;
        long stateEpoch;

        Session(Player player, Action action, Rule rule) {
            this.player = player;
            this.action = action;
            this.rule = rule;
        }
    }

    private static final class View {
        final List<BooleanSupplier> pending = new ArrayList<>();
        BukkitTask mount;
        boolean ready;
    }

    private final JavaPlugin plugin;
    private final CombatEffectPacketService packets;
    private final CombatEffectPacketConfiguration definitions;
    private final PacketBridgeAPI bridge;
    private final Logger logger;
    private final List<Rule> rules = new ArrayList<>();
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private AutoCloseable registration;
    private boolean active;

    public SkillEffectService(JavaPlugin plugin, CombatEffectPacketService packets,
                              CombatEffectPacketConfiguration definitions, PacketBridgeAPI bridge,
                              ConfigurationSection config, Logger logger) {
        this.plugin = plugin;
        this.packets = packets;
        this.definitions = definitions;
        this.bridge = bridge;
        this.logger = logger;
        ConfigurationSection entries = config.getConfigurationSection("rules");
        if (entries != null) {
            for (String id : entries.getKeys(false)) {
                ConfigurationSection section = entries.getConfigurationSection(id);
                if (section != null && section.getBoolean("enabled", true)) rules.add(new Rule(section));
            }
        }
    }

    public boolean start(ChronosAccess chronos) {
        if (chronos == null || bridge == null || !bridge.isAvailable()) return false;
        try {
            registerActionEvents(chronos);
            ChronosHook.listen(plugin, chronos, this, "PlayerEnterStateEvent", "getStateId", this::enter);
            ChronosHook.listen(plugin, chronos, this, "PlayerLeaveStateEvent", "getStateId", this::leave);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registration = chronos.registerExecutionHandler(plugin, HANDLER_ID, this::execute);
            active = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            shutdown();
            logger.warning("Chronos 技能特效挂载失败: " + e);
            return false;
        }
    }

    private void registerActionEvents(ChronosAccess chronos) throws ReflectiveOperationException {
        var start = chronos.eventType("PlayerSkillActionStartEvent");
        var startPlayer = start.getMethod("getPlayer");
        var startId = start.getMethod("getActionInstanceId");
        var profession = start.getMethod("getProfessionId");
        var slot = start.getMethod("getSlotId");
        var skill = start.getMethod("getSkillId");
        var level = start.getMethod("getSkillLevel");
        ChronosHook.listen(plugin, chronos, this, start, EventPriority.LOWEST, (listener, event) -> {
            try {
                begin((Player) startPlayer.invoke(event), new Action((UUID) startId.invoke(event),
                    (String) profession.invoke(event), (String) slot.invoke(event),
                    (String) skill.invoke(event), (Integer) level.invoke(event)));
            } catch (ReflectiveOperationException e) {
                throw new EventException(e);
            }
        });
        var end = chronos.eventType("PlayerSkillActionEndEvent");
        var endPlayer = end.getMethod("getPlayer");
        var endId = end.getMethod("getActionInstanceId");
        var reason = end.getMethod("getReason");
        ChronosHook.listen(plugin, chronos, this, end, EventPriority.MONITOR, (listener, event) -> {
            try {
                Player player = (Player) endPlayer.invoke(event);
                Session session = sessions.get(player.getUniqueId());
                if (session != null && session.action.id().equals(endId.invoke(event))) {
                    finish(player, (String) reason.invoke(event));
                }
            } catch (ReflectiveOperationException e) {
                throw new EventException(e);
            }
        });
    }

    private void begin(Player player, Action action) {
        if (!active || action.id() == null || !player.isOnline()) return;
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null && previous.action.id().equals(action.id())) return;
        Rule rule = rules.stream().filter(entry -> entry.matches(action)).findFirst().orElse(null);
        sessions.put(player.getUniqueId(), new Session(player, action, rule));
        if (previous != null) cleanup(previous, "replaced");
    }

    private void enter(Player player, String state) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.state = state;
        session.stateEpoch++;
        if (session.entered) return;
        session.entered = true;
        Rule rule = session.rule;
        if (rule == null) return;
        Map<String, String> variables = variables(session, state);
        for (String id : rule.start()) play(session, id, variables, false);
        if (!rule.hudText().isBlank()) {
            String text = rule.hudText().replace("{player}", player.getName());
            for (var variable : variables.entrySet()) {
                text = text.replace("{" + variable.getKey() + "}", variable.getValue());
            }
            Map<String, String> payload = Map.of("text", text);
            send(session, HUD_ID, () -> bridge.sendPacket(player, HUD_ID, "show", payload));
        }
    }

    private void leave(Player player, String state) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && Objects.equals(session.state, state)) {
            session.state = null;
            session.stateEpoch++;
        }
    }

    private boolean execute(ChronosAccess.Execution execution) {
        if (!active || !Bukkit.isPrimaryThread()) return false;
        Session session = sessions.get(execution.player().getUniqueId());
        if (session == null || !session.entered || session.state == null
                || !session.action.id().equals(execution.actionId()) || !session.state.equals(execution.stateId())) {
            return false;
        }
        if (!Objects.equals(session.executionState, execution.stateInstanceId())) {
            session.executionState = execution.stateInstanceId();
            session.executionIndices.clear();
        }
        if (execution.index() <= session.executionIndices.getOrDefault(execution.executionId(), -1)) return true;
        session.executionIndices.put(execution.executionId(), execution.index());
        String id = execution.args().getOrDefault("packet-id", "").trim();
        Map<String, String> variables = variables(session, execution.stateId());
        variables.put("state_instance_id", execution.stateInstanceId().toString());
        variables.put("execution_id", execution.executionId());
        variables.put("execution_index", Integer.toString(execution.index()));
        return play(session, id, variables, true);
    }

    private boolean play(Session session, String id, Map<String, String> variables, boolean timeline) {
        var definition = definitions.findPacketById(id);
        if (definition == null || !definition.enabled()) {
            logger.warning("Chronos 技能特效包不存在或未启用: " + id);
            return false;
        }
        long now = System.nanoTime();
        Long previous = session.lastTimelinePacket.get(id);
        if (timeline && previous != null && TimeUnit.NANOSECONDS.toMillis(now - previous)
                < definition.cooldownMs()) return true;
        long epoch = session.stateEpoch;
        boolean queued = send(session, definition.uiId(), () ->
            (!timeline || epoch == session.stateEpoch) && packets.triggerConfiguredPacket(id, session.player, variables));
        if (timeline && queued && definition.hasCooldown()) session.lastTimelinePacket.put(id, now);
        return queued;
    }

    private boolean send(Session session, String uiId, BooleanSupplier packet) {
        if (!current(session)) return false;
        View view = session.views.get(uiId);
        if (view != null) {
            if (view.ready) return packet.getAsBoolean();
            view.pending.add(packet);
            return true;
        }
        if (bridge.isUiOpen(session.player, uiId)) {
            logger.warning("Chronos 技能特效 UI 已被其他功能打开: " + uiId);
            return false;
        }
        View created = new View();
        session.views.put(uiId, created);
        if (!bridge.openUi(session.player, uiId)) {
            session.views.remove(uiId, created);
            return false;
        }
        if (!current(session)) return false;
        created.pending.add(packet);
        // Give ArcartX one tick to mount a newly opened UI. End/reload cancels this task.
        try {
            created.mount = Bukkit.getScheduler().runTask(plugin, () -> {
                created.mount = null;
                if (!current(session)) return;
                created.ready = true;
                for (BooleanSupplier pending : List.copyOf(created.pending)) {
                    if (!current(session)) break;
                    pending.getAsBoolean();
                }
                created.pending.clear();
            });
        } catch (RuntimeException e) {
            session.views.remove(uiId, created);
            close(session, uiId);
            logger.warning("Chronos 技能特效无法调度: " + e);
            return false;
        }
        return true;
    }

    private boolean current(Session session) {
        return active && session.player.isOnline() && sessions.get(session.player.getUniqueId()) == session;
    }

    private static Map<String, String> variables(Session session, String state) {
        Map<String, String> values = new LinkedHashMap<>(session.action.variables());
        values.put("state_id", Objects.toString(state, ""));
        return values;
    }

    private void finish(Player player, String reason) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) cleanup(session, reason);
    }

    private void cleanup(Session session, String reason) {
        for (View view : session.views.values()) {
            if (view.mount != null) view.mount.cancel();
            view.pending.clear();
        }
        try {
            if (session.rule != null && session.player.isOnline() && !session.views.isEmpty()) {
                Map<String, String> variables = variables(session, session.state);
                variables.put("end_reason", Objects.toString(reason, ""));
                for (String id : session.rule.cleanup()) {
                    var definition = definitions.findPacketById(id);
                    View view = definition == null ? null : session.views.get(definition.uiId());
                    if (view != null && view.ready && !claimedByNewAction(session, definition.uiId())) {
                        try {
                            packets.triggerConfiguredPacket(id, session.player, variables);
                        } catch (RuntimeException | LinkageError e) {
                            logger.warning("Chronos 技能特效清理包发送失败: " + id + " " + e);
                        }
                    }
                }
            }
        } finally {
            for (String uiId : session.views.keySet()) close(session, uiId);
            session.views.clear();
        }
    }

    private void close(Session session, String uiId) {
        if (claimedByNewAction(session, uiId)) return;
        try {
            if (!bridge.closeUi(session.player, uiId)) logger.fine("Chronos 技能特效 UI 关闭失败: " + uiId);
        } catch (RuntimeException | LinkageError e) {
            logger.warning("Chronos 技能特效 UI 关闭失败: " + uiId + " " + e);
        }
    }

    private boolean claimedByNewAction(Session previous, String uiId) {
        Session current = sessions.get(previous.player.getUniqueId());
        return current != null && current.views.containsKey(uiId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer(), "quit");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        finish(event.getEntity(), "death");
    }

    public void shutdown() {
        active = false;
        if (registration != null) {
            try {
                registration.close();
            } catch (Exception | LinkageError e) {
                logger.warning("Chronos 执行处理器注销失败: " + e);
            }
            registration = null;
        }
        try {
            HandlerList.unregisterAll(this);
        } catch (RuntimeException e) {
            logger.warning("Chronos 技能监听器注销失败: " + e);
        }
        for (Session session : List.copyOf(sessions.values())) {
            finish(session.player, "shutdown");
        }
    }
}
