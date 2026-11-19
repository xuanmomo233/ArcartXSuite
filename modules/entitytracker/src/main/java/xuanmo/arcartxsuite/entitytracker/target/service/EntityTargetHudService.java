package xuanmo.arcartxsuite.entitytracker.target.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.target.config.EntityTargetHudConfiguration;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;
import java.util.logging.Logger;

public final class EntityTargetHudService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final EntityTargetHudConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final List<String> uiIds;
    private final Map<UUID, TrackedTarget> trackedTargets = new ConcurrentHashMap<>();
    private final Map<UUID, ViewerSnapshot> viewerSnapshots = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile boolean closed;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback refresh =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::refreshAll);

    private final java.util.function.Supplier<xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration> bossConfigurationProvider;
    private final Predicate<UUID> trackedBossLookup;

    public EntityTargetHudService(
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration> bossConfigurationProvider,
        EntityTargetHudConfiguration configuration,
        PacketBridgeAPI bridge,
        List<String> uiIds,
        Predicate<UUID> trackedBossLookup
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.bossConfigurationProvider = bossConfigurationProvider;
        this.configuration = configuration;
        this.bridge = bridge;
        this.uiIds = uiIds;
        this.trackedBossLookup = trackedBossLookup;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            refresh,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
    }

    public void shutdown() {
        closed = true;
        refresh.close();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (UUID viewerId : new ArrayList<>(viewerSnapshots.keySet())) {
            closeViewer(viewerId, Bukkit.getPlayer(viewerId));
        }
        trackedTargets.clear();
        viewerSnapshots.clear();
        HandlerList.unregisterAll(this);
    }

    public int activeTargetCount() {
        return trackedTargets.size();
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped() { return refresh.stopped(); }

    public int activeViewerCount() {
        return (int) viewerSnapshots.values().stream().filter(ViewerSnapshot::open).count();
    }

    /**
     * 读取主线程发布的不可变快照，允许 PlaceholderAPI 从后台线程读取。
     */
    public Optional<EntityTargetSnapshot> resolveViewerTargetSnapshot(Player viewer) {
        return resolveViewerTargetSnapshotById(viewer == null ? null : viewer.getUniqueId());
    }

    public Optional<EntityTargetSnapshot> resolveViewerTargetSnapshotById(UUID viewerId) {
        ViewerSnapshot snapshot = viewerId == null ? null : viewerSnapshots.get(viewerId);
        TrackedTarget tracked = viewerId == null ? null : trackedTargets.get(viewerId);
        if (snapshot == null || tracked == null || !tracked.targetUuid().equals(snapshot.targetUuid())
            || System.currentTimeMillis() - tracked.lastHitAtMillis() > configuration.targetTimeoutMs()) {
            return Optional.empty();
        }
        return Optional.of(snapshot.snapshot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (closed) return;
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Player attacker = CombatEventSupport.resolvePlayerAttacker(event);
        if (attacker == null || attacker.equals(target) || !attacker.isOnline() || attacker.isDead()) {
            return;
        }

        UUID viewerId = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        var current = new TrackedTarget(target.getUniqueId(), now);
        var previous = trackedTargets.put(viewerId, current);
        var snapshot = viewerSnapshots.get(viewerId);
        // Keep the timeout current on every hit. An already visible target can use the periodic refresh.
        if (previous != null && previous.targetUuid().equals(current.targetUuid())
            && now - previous.lastHitAtMillis() <= configuration.targetTimeoutMs()
            && snapshot != null && (snapshot.open() || uiIds.isEmpty())) {
            return;
        }
        refreshViewer(attacker, target, current, now);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        clearViewer(event.getEntity());
    }

    private void refreshAll() {
        for (UUID viewerId : new ArrayList<>(trackedTargets.keySet())) {
            refreshViewer(viewerId);
        }
    }

    private void refreshViewer(UUID viewerId) {
        TrackedTarget trackedTarget = trackedTargets.get(viewerId);
        if (trackedTarget == null) {
            closeViewer(viewerId, Bukkit.getPlayer(viewerId));
            return;
        }

        Player attacker = Bukkit.getPlayer(viewerId);
        if (attacker == null || !attacker.isOnline() || attacker.isDead()) {
            trackedTargets.remove(viewerId);
            closeViewer(viewerId, attacker);
            return;
        }

        Entity entity = plugin.getServer().getEntity(trackedTarget.targetUuid());
        if (!(entity instanceof LivingEntity target)) {
            trackedTargets.remove(viewerId);
            closeViewer(viewerId, attacker);
            return;
        }

        refreshViewer(attacker, target, trackedTarget, System.currentTimeMillis());
    }

    private void refreshViewer(Player attacker, LivingEntity target, TrackedTarget trackedTarget, long now) {
        UUID viewerId = attacker.getUniqueId();
        var snapshot = resolveSnapshot(attacker, target, trackedTarget, now);
        if (snapshot.isEmpty()) {
            trackedTargets.remove(viewerId);
            closeViewer(viewerId, attacker);
            return;
        }

        EntityTargetSnapshot current = snapshot.get();
        if (uiIds.isEmpty()) {
            viewerSnapshots.put(viewerId, new ViewerSnapshot(trackedTarget, current, false, null));
            return;
        }

        ViewerSnapshot previous = viewerSnapshots.get(viewerId);
        boolean open = previous != null && previous.open();
        Map<String, Object> compact = compactPacket(current);
        if (open && trackedTarget.equals(previous.tracked()) && compact.equals(previous.compactPacket())) {
            viewerSnapshots.put(viewerId, new ViewerSnapshot(trackedTarget, current, true, previous.compactPacket()));
            return;
        }
        if (!open && !bridge.openUiAll(attacker, uiIds)) {
            viewerSnapshots.put(viewerId, new ViewerSnapshot(trackedTarget, current, false, null));
            return;
        }
        String handler = open ? "update" : "init";
        Map<String, Object> payload = new java.util.LinkedHashMap<>(compact);
        payload.put("lastHitAgoMs", current.lastHitAgoMs());
        boolean sent = bridge.sendPacketToAll(attacker, uiIds, handler, payload);
        if (!sent) {
            bridge.closeUiAll(attacker, uiIds);
        }
        viewerSnapshots.put(viewerId, new ViewerSnapshot(trackedTarget, current, sent, sent ? compact : null));
        logPacket(handler, attacker, sent, payload);
    }

    private static Map<String, Object> compactPacket(EntityTargetSnapshot snapshot) {
        return Map.of("title", snapshot.title(), "healthText", snapshot.healthText(),
            "maxHealthText", snapshot.maxHealthText(), "healthPercentText", snapshot.healthPercentText(),
            "progress", snapshot.progress(), "distanceText", snapshot.distanceText(),
            "timeoutMs", snapshot.timeoutMs());
    }

    private Optional<EntityTargetSnapshot> resolveSnapshot(Player attacker, LivingEntity target, TrackedTarget trackedTarget, long now) {
        if (now - trackedTarget.lastHitAtMillis() > configuration.targetTimeoutMs()) {
            return Optional.empty();
        }
        var activeMob = EntityCombatMetadata.resolveActiveMob(target);
        String mythicMobId = EntityCombatMetadata.resolveMythicMobIdFrom(activeMob);
        String entityType = EntityCombatMetadata.resolveEntityType(target);
        if (shouldIgnoreTarget(configuration, bossConfigurationProvider.get(), trackedTarget.targetUuid(), trackedBossLookup,
            mythicMobId, entityType)) {
            return Optional.empty();
        }
        return EntityTargetStateResolver.resolve(attacker, target, configuration, trackedTarget.lastHitAtMillis(), now,
            activeMob, mythicMobId, entityType);
    }

    private void clearViewer(Player player) {
        if (player == null) {
            return;
        }
        trackedTargets.remove(player.getUniqueId());
        closeViewer(player.getUniqueId(), player);
    }

    private void closeViewer(UUID viewerId, Player player) {
        var snapshot = viewerSnapshots.remove(viewerId);
        if (snapshot == null || !snapshot.open()) {
            return;
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Object> payload = Map.of();
        boolean success = bridge.sendPacketToAll(player, uiIds, "close", payload);
        logPacket("close", player, success, payload);
        bridge.closeUiAll(player, uiIds);
    }

    private void logPacket(String handler, Player player, boolean success, Map<String, Object> payload) {
        if (!configuration.debug()) {
            return;
        }
        this.logger.info(
            "EntityTracker target-hud 发包 -> player="
                + player.getName()
                + " | handler="
                + handler
                + " | success="
                + success
                + " | payload="
            + payload
        );
    }

    static boolean shouldIgnoreTarget(
        EntityTargetHudConfiguration configuration,
        PluginConfiguration entityTrackerConfiguration,
        UUID entityUuid,
        Predicate<UUID> trackedBossLookup,
        String mythicMobId,
        String entityType
    ) {
        if (configuration == null) {
            return false;
        }
        if (configuration.isBlacklisted(mythicMobId, entityType)) {
            return true;
        }
        if (!configuration.ignoreTrackedBosses()) {
            return false;
        }
        if (entityUuid != null
            && trackedBossLookup != null
            && trackedBossLookup.test(entityUuid)) {
            return true;
        }
        String normalizedMythicMobId = EntityCombatMetadata.normalizeMythicMobId(mythicMobId);
        if (normalizedMythicMobId.isBlank() || entityTrackerConfiguration == null) {
            return false;
        }
        BossDefinition definition = entityTrackerConfiguration.findBoss(normalizedMythicMobId);
        return definition != null && definition.enabled();
    }

    private record TrackedTarget(UUID targetUuid, long lastHitAtMillis) {
    }

    private record ViewerSnapshot(TrackedTarget tracked, EntityTargetSnapshot snapshot, boolean open, Map<String, Object> compactPacket) {
        UUID targetUuid() {
            return tracked.targetUuid();
        }
    }
}




