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
    private final Map<UUID, Boolean> openViewers = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

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
            this::refreshAll,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (UUID viewerId : new ArrayList<>(openViewers.keySet())) {
            closeViewer(viewerId, Bukkit.getPlayer(viewerId));
        }
        trackedTargets.clear();
        openViewers.clear();
        HandlerList.unregisterAll(this);
    }

    public int activeTargetCount() {
        return trackedTargets.size();
    }

    public int activeViewerCount() {
        return openViewers.size();
    }

    /**
     * 只读地解析某个玩家当前正在追踪的目标快照，供 PlaceholderAPI 使用。
     * 不会修改追踪状态或发送数据包，目标不可用时返回空。
     */
    public Optional<EntityTargetSnapshot> resolveViewerTargetSnapshot(Player viewer) {
        if (viewer == null) {
            return Optional.empty();
        }
        TrackedTarget trackedTarget = trackedTargets.get(viewer.getUniqueId());
        if (trackedTarget == null || !viewer.isOnline() || viewer.isDead()) {
            return Optional.empty();
        }
        Entity entity = plugin.getServer().getEntity(trackedTarget.targetUuid());
        if (!(entity instanceof LivingEntity target)) {
            return Optional.empty();
        }
        String mythicMobId = EntityCombatMetadata.resolveMythicMobId(target);
        String entityType = EntityCombatMetadata.resolveEntityType(target);
        if (shouldIgnoreTarget(
            configuration,
            bossConfigurationProvider.get(),
            target.getUniqueId(),
            trackedBossLookup,
            mythicMobId,
            entityType
        )) {
            return Optional.empty();
        }
        return EntityTargetStateResolver.resolve(
            viewer,
            target,
            configuration,
            trackedTarget.lastHitAtMillis(),
            System.currentTimeMillis()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Player attacker = CombatEventSupport.resolvePlayerAttacker(event);
        if (attacker == null || attacker.equals(target) || !attacker.isOnline() || attacker.isDead()) {
            return;
        }

        String mythicMobId = EntityCombatMetadata.resolveMythicMobId(target);
        String entityType = EntityCombatMetadata.resolveEntityType(target);
        if (shouldIgnoreTarget(
            configuration,
            bossConfigurationProvider.get(),
            target.getUniqueId(),
            trackedBossLookup,
            mythicMobId,
            entityType
        )) {
            clearViewer(attacker);
            return;
        }

        trackedTargets.put(attacker.getUniqueId(), new TrackedTarget(target.getUniqueId(), System.currentTimeMillis()));
        refreshViewer(attacker.getUniqueId());
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

        String mythicMobId = EntityCombatMetadata.resolveMythicMobId(target);
        String entityType = EntityCombatMetadata.resolveEntityType(target);
        if (shouldIgnoreTarget(
            configuration,
            bossConfigurationProvider.get(),
            target.getUniqueId(),
            trackedBossLookup,
            mythicMobId,
            entityType
        )) {
            trackedTargets.remove(viewerId);
            closeViewer(viewerId, attacker);
            return;
        }

        Optional<EntityTargetSnapshot> snapshot = EntityTargetStateResolver.resolve(
            attacker,
            target,
            configuration,
            trackedTarget.lastHitAtMillis(),
            System.currentTimeMillis()
        );
        if (snapshot.isEmpty()) {
            trackedTargets.remove(viewerId);
            closeViewer(viewerId, attacker);
            return;
        }

        Map<String, Object> payload = snapshot.get().toPacket();
        if (!openViewers.containsKey(viewerId)) {
            if (!bridge.openUiAll(attacker, uiIds)) {
                return;
            }
            boolean success = bridge.sendPacketToAll(attacker, uiIds, "init", payload);
            if (success) {
                openViewers.put(viewerId, Boolean.TRUE);
            } else {
                bridge.closeUiAll(attacker, uiIds);
            }
            logPacket("init", attacker, success, payload);
            return;
        }

        boolean success = bridge.sendPacketToAll(attacker, uiIds, "update", payload);
        if (!success) {
            openViewers.remove(viewerId);
            bridge.closeUiAll(attacker, uiIds);
        }
        logPacket("update", attacker, success, payload);
    }

    private void clearViewer(Player player) {
        if (player == null) {
            return;
        }
        trackedTargets.remove(player.getUniqueId());
        closeViewer(player.getUniqueId(), player);
    }

    private void closeViewer(UUID viewerId, Player player) {
        if (openViewers.remove(viewerId) == null) {
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
}




