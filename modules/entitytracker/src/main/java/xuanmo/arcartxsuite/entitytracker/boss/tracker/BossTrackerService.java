package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossSortMode;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.crossserver.EntityTrackerCrossServerService;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.api.event.TaczGunDamageEvent;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

public final class BossTrackerService implements Listener {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");
    private static final Comparator<TrackedBossSnapshot> SNAPSHOT_ORDER = Comparator
        .comparingLong((TrackedBossSnapshot snapshot) -> snapshot.renderState().spawnOrder())
        .thenComparingLong(snapshot -> snapshot.renderState().spawnedAt())
        .thenComparing(snapshot -> snapshot.session().getEntityUuid().toString());
    private static final long HYBRID_WARMUP_RESCAN_INTERVAL_TICKS = 20L;
    private static final int HYBRID_WARMUP_RESCAN_ATTEMPTS = 6;
    private static final long HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS = 100L;

    private final JavaPlugin plugin;
    private final PluginConfiguration configuration;
    private final PacketBridgeAPI arcartXBridge;
    private final List<String> runtimeUiIds;
    private final java.util.function.BiConsumer<String, Player> signalDispatcher;
    private final xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform;
    private final BossDamageSettlementService settlementService;
    private final EntityTrackerCrossServerService crossServerService;
    private final BossKillRecordingService killRecordingService;
    private final xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge;
    private final Map<UUID, BossSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;
    private BukkitTask queuedRefreshTask;
    private BukkitTask hybridWarmupRescanTask;
    private BukkitTask hybridPersistentRescanTask;
    private int hybridWarmupRescanAttempts;
    private long nextSpawnOrder = 0L;
    private xuanmo.arcartxsuite.api.attribute.AttributeDamageListener attributeDamageListener;

    public BossTrackerService(
        JavaPlugin plugin,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this(plugin, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, null, null, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        EntityTrackerCrossServerService crossServerService
    ) {
        this(plugin, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, crossServerService, null, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        EntityTrackerCrossServerService crossServerService,
        BossKillRecordingService killRecordingService
    ) {
        this(plugin, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, crossServerService, killRecordingService, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        EntityTrackerCrossServerService crossServerService,
        BossKillRecordingService killRecordingService,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.arcartXBridge = arcartXBridge;
        this.runtimeUiIds = runtimeUiIds;
        this.serverPlatform = serverPlatform;
        this.signalDispatcher = signalDispatcher;
        this.attributeBridge = attributeBridge;
        this.crossServerService = crossServerService;
        this.killRecordingService = killRecordingService;
        this.settlementService = new BossDamageSettlementService(
            plugin, mailDispatchableProvider, signalDispatcher, itemSourceRegistry, placeholderResolver
        );
    }

    public BossTrackerService(
        JavaPlugin plugin,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this(plugin, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            mailDispatchableProvider, itemSourceRegistry, attributeBridge, null, null, null);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        restoreTrackedBosses("启动扫描", true);
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::refreshViewers,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
        if (attributeBridge != null && attributeBridge.hasDamageSource()) {
            attributeDamageListener = event -> {
                if (!(event.target() instanceof LivingEntity target)) return;
                BossSession session = sessions.get(target.getUniqueId());
                if (session == null) return;
                Player attacker = event.attacker();
                if (attacker == null) return;
                double effectiveDamage = resolveEffectiveDamage(target, event.damage());
                if (effectiveDamage <= 0.0D) return;
                session.recordDamage(attacker, effectiveDamage);
                requestRefresh();
            };
            attributeBridge.registerDamageListener(attributeDamageListener);
        }
        scheduleHybridRescansIfNeeded();
        requestRefresh();
    }

    public void shutdown() {
        if (queuedRefreshTask != null) {
            queuedRefreshTask.cancel();
            queuedRefreshTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (hybridWarmupRescanTask != null) {
            hybridWarmupRescanTask.cancel();
            hybridWarmupRescanTask = null;
        }
        if (hybridPersistentRescanTask != null) {
            hybridPersistentRescanTask.cancel();
            hybridPersistentRescanTask = null;
        }
        if (attributeBridge != null && attributeDamageListener != null) {
            attributeBridge.unregisterDamageListener(attributeDamageListener);
            attributeDamageListener = null;
        }
        settlementService.shutdown();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            arcartXBridge.closeUiAll(onlinePlayer, runtimeUiIds);
        }
        viewerStates.clear();
        sessions.clear();
        HandlerList.unregisterAll(this);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public int getActiveViewerCount() {
        return viewerStates.size();
    }

    public boolean attributePlusHooked() {
        return attributeBridge != null && attributeBridge.hasDamageSource();
    }

    public BossDamagePlayerSettlementView getLastSettlement(UUID playerUuid) {
        return settlementService.lastSettlement(playerUuid);
    }

    public List<BossDamageSettlementRecord> settlements() {
        return settlementService.settlements();
    }

    public BossDamageSettlementRecord settlement(String settlementId) {
        return settlementService.settlement(settlementId);
    }

    public BossDamageRewardDispatchResult reissueSettlementReward(String settlementId, int rank, OfflinePlayer overridePlayer) {
        return settlementService.reissueReward(settlementId, rank, overridePlayer);
    }

    public List<String> settlementIds() {
        return settlementService.settlementIds();
    }

    public List<String> sessionEntityIds() {
        List<String> entityIds = new ArrayList<>(sessions.size());
        for (UUID entityUuid : sessions.keySet()) {
            entityIds.add(entityUuid.toString());
        }
        return List.copyOf(entityIds);
    }

    public List<ActiveBossSessionView> activeSessions(String mobIdFilter) {
        List<ActiveBossSessionView> result = new ArrayList<>();
        String normalizedFilter = mobIdFilter == null ? "" : mobIdFilter.trim().toLowerCase(java.util.Locale.ROOT);
        Server server = Bukkit.getServer();
        for (TrackedBossSnapshot snapshot : collectActiveSnapshots()) {
            if (!normalizedFilter.isBlank() && !snapshot.renderState().mythicMobId().toLowerCase(java.util.Locale.ROOT).contains(normalizedFilter)) {
                continue;
            }
            org.bukkit.entity.LivingEntity entity = snapshot.session().resolveEntity(server);
            if (entity == null) {
                continue;
            }
            BossDamageRankingSnapshot ranking = snapshot.session().createSettlementSnapshot(entity).ranking();
            result.add(
                new ActiveBossSessionView(
                    snapshot.session().getEntityUuid(),
                    snapshot.renderState().mythicMobId(),
                    snapshot.renderState().displayName(),
                    snapshot.renderState().health(),
                    snapshot.renderState().maxHealth(),
                    ranking.participantCount(),
                    ranking.trackedPlayerCount(),
                    ranking.totalDamage()
                )
            );
        }
        return List.copyOf(result);
    }

    public BossSessionRankingView sessionRanking(UUID entityUuid) {
        if (entityUuid == null) {
            return null;
        }
        BossSession session = sessions.get(entityUuid);
        if (session == null) {
            return null;
        }
        org.bukkit.entity.LivingEntity entity = session.resolveEntity(Bukkit.getServer());
        if (entity == null || entity.isDead() || !entity.isValid()) {
            sessions.remove(entityUuid);
            return null;
        }
        BossSession.BossRenderState renderState = session.captureRenderState(entity, configuration.defaultViewerRange());
        BossDamageRankingSnapshot ranking = session.createSettlementSnapshot(entity).ranking();
        return new BossSessionRankingView(
            session.getEntityUuid(),
            renderState.mythicMobId(),
            renderState.displayName(),
            renderState.health(),
            renderState.maxHealth(),
            ranking.participantCount(),
            ranking.trackedPlayerCount(),
            ranking.totalDamage(),
            ranking.trackedEntries()
        );
    }

    public void handleUiRegistryReload() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            arcartXBridge.closeUiAll(onlinePlayer, runtimeUiIds);
        }
        viewerStates.clear();
        requestRefresh();
    }

    public PlayerBossViewSnapshot getViewerSnapshot(Player player) {
        if (player == null) {
            return PlayerBossViewSnapshot.empty(configuration.maxVisibleBars(), configuration.sortMode());
        }
        ViewerState state = viewerStates.get(player.getUniqueId());
        return state == null
            ? PlayerBossViewSnapshot.empty(configuration.maxVisibleBars(), configuration.sortMode())
            : state.snapshot();
    }

    @EventHandler
    public void onSpawn(MythicMobSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        SessionRegistration registration = registerTrackedBoss(
            livingEntity,
            event.getMobType().getInternalName(),
            event.getMob().getDisplayName()
        );
        if (!registration.created()) {
            return;
        }

        sendLifecycleCards(
            registration.session(),
            livingEntity,
            registration.session().getDefinition().spawnChatCard(),
            "spawn"
        );
        requestRefresh();
    }

    @EventHandler
    public void onDeath(MythicMobDeathEvent event) {
        BossSession session = sessions.get(event.getEntity().getUniqueId());
        if (session == null) {
            return;
        }
        LivingEntity entity = event.getEntity() instanceof LivingEntity livingEntity
            ? livingEntity
            : session.resolveEntity(Bukkit.getServer());
        if (entity == null) {
            sessions.remove(event.getEntity().getUniqueId());
            requestRefresh();
            return;
        }
        BossDamageSettlementRecord record = settlementService.settle(session, entity);
        if (crossServerService != null && record != null) {
            crossServerService.publishSettlement(record, entity.getLocation());
        }
        if (killRecordingService != null && record != null) {
            killRecordingService.recordBossDeath(session, entity, record, entity.getLocation());
        }
        sendLifecycleCards(session, entity, session.getDefinition().deathChatCard(), "death");
        dispatchBossSettlementSignals(session, record);
        sessions.remove(event.getEntity().getUniqueId());
        requestRefresh();
    }

    @EventHandler
    public void onDespawn(MythicMobDespawnEvent event) {
        Entity entity = event.getEntity();
        BossSession session = sessions.remove(entity.getUniqueId());
        if (session == null || !(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        sendLifecycleCards(session, livingEntity, session.getDefinition().despawnChatCard(), "despawn");
        requestRefresh();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        viewerStates.remove(playerId);
        arcartXBridge.closeUiAll(event.getPlayer(), runtimeUiIds);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        recordVanillaPlayerDamage(event);
        recordBossDamageTaken(event);
    }

    private void refreshViewers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            viewerStates.clear();
            pruneSessions();
            return;
        }

        List<TrackedBossSnapshot> activeSnapshots = collectActiveSnapshots();
        if (activeSnapshots.isEmpty()) {
            if (viewerStates.isEmpty()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                closeBossHud(player);
            }
            return;
        }

        Map<UUID, List<TrackedBossSnapshot>> snapshotsByWorld = indexSnapshotsByWorld(activeSnapshots);

        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibleBossView visibleBossView = collectVisibleSessions(player, snapshotsByWorld.get(player.getWorld().getUID()));
            if (visibleBossView.totalVisibleCount() == 0) {
                closeBossHud(player);
                continue;
            }

            PlayerBossViewSnapshot snapshot = buildViewSnapshot(player, visibleBossView);
            Map<String, Object> packet = buildUiPacket(snapshot);
            ViewerState previousState = viewerStates.get(player.getUniqueId());
            if (previousState == null) {
                if (!arcartXBridge.openUiAll(player, runtimeUiIds)) {
                    continue;
                }
                if (!arcartXBridge.sendPacketToAll(player, runtimeUiIds, "init", packet)) {
                    viewerStates.remove(player.getUniqueId());
                    arcartXBridge.closeUiAll(player, runtimeUiIds);
                    continue;
                }
                viewerStates.put(player.getUniqueId(), new ViewerState(snapshot));
                continue;
            }

            if (!arcartXBridge.sendPacketToAll(player, runtimeUiIds, "update", packet)) {
                viewerStates.remove(player.getUniqueId());
                arcartXBridge.closeUiAll(player, runtimeUiIds);
                continue;
            }
            viewerStates.put(player.getUniqueId(), new ViewerState(snapshot));
        }
    }

    private void requestRefresh() {
        if (queuedRefreshTask != null) {
            return;
        }
        queuedRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            queuedRefreshTask = null;
            refreshViewers();
        });
    }

    private void scheduleHybridRescansIfNeeded() {
        if (serverPlatform == null || !serverPlatform.hybridServer()) {
            return;
        }

        hybridWarmupRescanAttempts = 0;
        hybridWarmupRescanTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                hybridWarmupRescanAttempts++;
                int restoredCount = restoreTrackedBosses("Mohist 启动补扫", false);
                if (restoredCount > 0) {
                    plugin.getLogger().info("Mohist 启动补扫恢复了 " + restoredCount + " 个遗漏的 MythicMobs Boss。");
                    requestRefresh();
                }
                if (hybridWarmupRescanAttempts >= HYBRID_WARMUP_RESCAN_ATTEMPTS) {
                    hybridWarmupRescanTask.cancel();
                    hybridWarmupRescanTask = null;
                }
            },
            HYBRID_WARMUP_RESCAN_INTERVAL_TICKS,
            HYBRID_WARMUP_RESCAN_INTERVAL_TICKS
        );

        hybridPersistentRescanTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                int restoredCount = restoreTrackedBosses("Mohist 持续补扫", false);
                if (restoredCount > 0) {
                    plugin.getLogger().info("Mohist 持续补扫恢复了 " + restoredCount + " 个遗漏的 MythicMobs Boss。");
                    requestRefresh();
                }
            },
            HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS,
            HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS
        );
    }

    private int restoreTrackedBosses(String reason, boolean logWhenEmpty) {
        int restoredCount = 0;
        try {
            List<ActiveMob> activeMobs = new ArrayList<>(MythicBukkit.inst().getMobManager().getActiveMobs());
            activeMobs.sort(
                Comparator.comparingLong(ActiveMob::getSpawnTime)
                    .thenComparing(activeMob -> String.valueOf(activeMob.getUniqueId()))
            );

            for (ActiveMob activeMob : activeMobs) {
                if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof LivingEntity livingEntity)) {
                    continue;
                }

                SessionRegistration registration = registerTrackedBoss(
                    livingEntity,
                    resolveMobId(activeMob),
                    activeMob.getDisplayName()
                );
                if (registration.created()) {
                    restoredCount++;
                }
            }
        } catch (Exception | LinkageError exception) {
            plugin.getLogger().warning(reason + "已存在 MythicMobs Boss 失败: " + exception.getMessage());
            return 0;
        }

        if (restoredCount > 0) {
            plugin.getLogger().info(reason + "已恢复跟踪 " + restoredCount + " 个已存在的 MythicMobs Boss。");
        } else if (logWhenEmpty) {
            plugin.getLogger().fine(reason + "未发现需要恢复的已存在 MythicMobs Boss。");
        }
        return restoredCount;
    }

    private SessionRegistration registerTrackedBoss(LivingEntity livingEntity, String mythicMobId, String displayName) {
        if (livingEntity == null || livingEntity.isDead() || !livingEntity.isValid()) {
            return SessionRegistration.SKIPPED;
        }
        if (mythicMobId == null || mythicMobId.isBlank()) {
            return SessionRegistration.SKIPPED;
        }

        BossDefinition definition = configuration.findBoss(mythicMobId);
        if (definition == null || !definition.enabled()) {
            return SessionRegistration.SKIPPED;
        }

        BossSession existing = sessions.get(livingEntity.getUniqueId());
        if (existing != null) {
            existing.rememberDisplayName(displayName);
            return new SessionRegistration(existing, false);
        }

        String resolvedDisplayName = (displayName == null || displayName.isBlank()) ? livingEntity.getCustomName() : displayName;
        BossSession session = new BossSession(
            livingEntity.getUniqueId(),
            definition,
            mythicMobId,
            resolvedDisplayName,
            ++nextSpawnOrder
        );
        sessions.put(livingEntity.getUniqueId(), session);
        return new SessionRegistration(session, true);
    }

    private void pruneSessions() {
        List<UUID> stale = new ArrayList<>();
        Server server = Bukkit.getServer();
        for (Map.Entry<UUID, BossSession> entry : sessions.entrySet()) {
            LivingEntity entity = entry.getValue().resolveEntity(server);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                stale.add(entry.getKey());
            }
        }
        for (UUID entityUuid : stale) {
            sessions.remove(entityUuid);
        }
    }

    private List<TrackedBossSnapshot> collectActiveSnapshots() {
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> stale = new ArrayList<>();
        List<TrackedBossSnapshot> activeSnapshots = new ArrayList<>(sessions.size());
        Server server = Bukkit.getServer();

        for (Map.Entry<UUID, BossSession> entry : sessions.entrySet()) {
            BossSession session = entry.getValue();
            LivingEntity entity = session.resolveEntity(server);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                stale.add(entry.getKey());
                continue;
            }

            activeSnapshots.add(
                new TrackedBossSnapshot(
                    session,
                    session.captureRenderState(entity, configuration.defaultViewerRange())
                )
            );
        }

        for (UUID entityUuid : stale) {
            sessions.remove(entityUuid);
        }

        activeSnapshots.sort(SNAPSHOT_ORDER);
        return activeSnapshots;
    }

    private Map<UUID, List<TrackedBossSnapshot>> indexSnapshotsByWorld(List<TrackedBossSnapshot> activeSnapshots) {
        Map<UUID, List<TrackedBossSnapshot>> snapshotsByWorld = new LinkedHashMap<>();
        for (TrackedBossSnapshot snapshot : activeSnapshots) {
            World world = snapshot.renderState().location().getWorld();
            if (world == null) {
                continue;
            }
            snapshotsByWorld.computeIfAbsent(world.getUID(), unused -> new ArrayList<>()).add(snapshot);
        }
        return snapshotsByWorld;
    }

    private VisibleBossView collectVisibleSessions(Player player, List<TrackedBossSnapshot> worldSnapshots) {
        if (worldSnapshots == null || worldSnapshots.isEmpty()) {
            return VisibleBossView.EMPTY;
        }

        List<TrackedBossSnapshot> visibleSnapshots = new ArrayList<>(worldSnapshots.size());

        for (TrackedBossSnapshot snapshot : worldSnapshots) {
            if (!snapshot.session().canView(player, snapshot.renderState())) {
                continue;
            }
            visibleSnapshots.add(snapshot);
        }

        if (visibleSnapshots.isEmpty()) {
            return VisibleBossView.EMPTY;
        }

        visibleSnapshots.sort(comparatorFor(player, configuration.sortMode()));
        int maxBars = configuration.maxVisibleBars();
        List<TrackedBossSnapshot> limitedSnapshots = visibleSnapshots.size() > maxBars
            ? List.copyOf(visibleSnapshots.subList(0, maxBars))
            : List.copyOf(visibleSnapshots);
        return new VisibleBossView(limitedSnapshots, visibleSnapshots.size());
    }

    private PlayerBossViewSnapshot buildViewSnapshot(Player player, VisibleBossView visibleBossView) {
        List<BossViewSlot> slots = new ArrayList<>(visibleBossView.visibleSnapshots().size());
        for (TrackedBossSnapshot snapshot : visibleBossView.visibleSnapshots()) {
            slots.add(snapshot.session().toViewSlot(player, snapshot.renderState()));
        }
        return new PlayerBossViewSnapshot(
            slots.size(),
            visibleBossView.totalVisibleCount(),
            configuration.maxVisibleBars(),
            configuration.sortMode(),
            List.copyOf(slots)
        );
    }

    private Map<String, Object> buildUiPacket(PlayerBossViewSnapshot snapshot) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("bossCount", snapshot.bossCount());
        packet.put("totalBossCount", snapshot.totalBossCount());
        packet.put("maxVisibleBars", snapshot.maxVisibleBars());
        packet.put("maxDamageRankingEntries", configuration.getMaxDamageRankingEntries());
        packet.put("sortMode", snapshot.sortMode().configKey());

        int slot = 1;
        for (; slot <= snapshot.visibleSlots().size(); slot++) {
            BossViewSlot slotData = snapshot.visibleSlots().get(slot - 1);
            writeBossSlot(packet, slot, slotData);
        }
        for (; slot <= configuration.maxVisibleBars(); slot++) {
            writeEmptySlot(packet, slot);
        }
        return packet;
    }

    private void closeBossHud(Player player) {
        UUID playerId = player.getUniqueId();
        if (viewerStates.remove(playerId) == null) {
            return;
        }
        arcartXBridge.sendPacketToAll(player, runtimeUiIds, "close", Map.of("bossCount", 0, "totalBossCount", 0));
        arcartXBridge.closeUiAll(player, runtimeUiIds);
    }

    private void writeBossSlot(Map<String, Object> packet, int slot, BossViewSlot slotData) {
        String prefix = "slot" + slot + "_";
        BossPlaceholderContext context = slotData.context();
        BossDamageRankingSnapshot ranking = context.damageRanking();
        BossDamageRankingEntry viewerEntry = context.viewerDamageEntry();
        packet.put(prefix + "visible", context.visible());
        packet.put(prefix + "title", slotData.title());
        packet.put(prefix + "subtitle", slotData.subtitle());
        packet.put(prefix + "health", context.health());
        packet.put(prefix + "maxHealth", context.maxHealth());
        packet.put(prefix + "healthText", context.healthText());
        packet.put(prefix + "maxHealthText", context.maxHealthText());
        packet.put(prefix + "healthPercent", context.healthPercent());
        packet.put(prefix + "healthPercentText", context.healthPercentText());
        packet.put(prefix + "distance", context.distance());
        packet.put(prefix + "distanceText", context.distanceText());
        packet.put(prefix + "progress", context.progress());
        packet.put(prefix + "mobId", context.mythicMobId());
        packet.put(prefix + "displayName", context.displayName());
        packet.put(prefix + "entityUuid", context.entityUuid());
        packet.put(prefix + "hasTarget", context.hasTarget());
        packet.put(prefix + "targetDisplayName", context.targetDisplayName());
        packet.put(prefix + "targetUuid", context.targetUuid());
        packet.put(prefix + "targetType", context.targetType());
        packet.put(prefix + "spawnOrder", context.spawnOrder());
        packet.put(prefix + "priority", context.priority());
        packet.put(prefix + "world", context.world());
        packet.put(prefix + "x", context.x());
        packet.put(prefix + "y", context.y());
        packet.put(prefix + "z", context.z());
        packet.put(prefix + "aliveSeconds", context.aliveSeconds());
        packet.put(prefix + "aliveTime", context.aliveTime());
        packet.put(prefix + "rankingEnabled", ranking.enabled());
        packet.put(prefix + "damageParticipantCount", ranking.participantCount());
        packet.put(prefix + "damageTrackedPlayerCount", ranking.trackedPlayerCount());
        packet.put(prefix + "totalDamage", ranking.totalDamage());
        packet.put(prefix + "totalDamageText", context.totalDamageText());
        packet.put(prefix + "viewerRank", viewerEntry.rank());
        packet.put(prefix + "viewerRankText", context.viewerRankText());
        packet.put(prefix + "viewerQualified", viewerEntry.qualified());
        packet.put(prefix + "viewerDamage", viewerEntry.damage());
        packet.put(prefix + "viewerDamageText", context.viewerDamageText());
        packet.put(prefix + "viewerDamagePercent", viewerEntry.damagePercent());
        packet.put(prefix + "viewerDamagePercentText", context.viewerDamagePercentText());
        packet.put(prefix + "viewerTakenDamage", viewerEntry.takenDamage());
        packet.put(prefix + "viewerTakenDamageText", context.viewerTakenDamageText());
        writeRankingEntries(packet, prefix, ranking, configuration.getMaxDamageRankingEntries());
    }

    private void writeEmptySlot(Map<String, Object> packet, int slot) {
        String prefix = "slot" + slot + "_";
        packet.put(prefix + "visible", false);
        packet.put(prefix + "title", "");
        packet.put(prefix + "subtitle", "");
        packet.put(prefix + "health", 0.0D);
        packet.put(prefix + "maxHealth", 0.0D);
        packet.put(prefix + "healthText", "0");
        packet.put(prefix + "maxHealthText", "0");
        packet.put(prefix + "healthPercent", 0.0D);
        packet.put(prefix + "healthPercentText", "0");
        packet.put(prefix + "distance", 0);
        packet.put(prefix + "distanceText", "-");
        packet.put(prefix + "progress", 0.0D);
        packet.put(prefix + "mobId", "");
        packet.put(prefix + "displayName", "");
        packet.put(prefix + "entityUuid", "");
        packet.put(prefix + "hasTarget", false);
        packet.put(prefix + "targetDisplayName", "");
        packet.put(prefix + "targetUuid", "");
        packet.put(prefix + "targetType", "");
        packet.put(prefix + "spawnOrder", 0L);
        packet.put(prefix + "priority", 0);
        packet.put(prefix + "world", "");
        packet.put(prefix + "x", 0);
        packet.put(prefix + "y", 0);
        packet.put(prefix + "z", 0);
        packet.put(prefix + "aliveSeconds", 0L);
        packet.put(prefix + "aliveTime", "00:00");
        packet.put(prefix + "rankingEnabled", false);
        packet.put(prefix + "damageParticipantCount", 0);
        packet.put(prefix + "damageTrackedPlayerCount", 0);
        packet.put(prefix + "totalDamage", 0.0D);
        packet.put(prefix + "totalDamageText", "0");
        packet.put(prefix + "viewerRank", 0);
        packet.put(prefix + "viewerRankText", "-");
        packet.put(prefix + "viewerQualified", false);
        packet.put(prefix + "viewerDamage", 0.0D);
        packet.put(prefix + "viewerDamageText", "0");
        packet.put(prefix + "viewerDamagePercent", 0.0D);
        packet.put(prefix + "viewerDamagePercentText", "0");
        packet.put(prefix + "viewerTakenDamage", 0.0D);
        packet.put(prefix + "viewerTakenDamageText", "0");
        for (int rank = 1; rank <= configuration.getMaxDamageRankingEntries(); rank++) {
            writeRankingEntry(packet, prefix, rank, BossDamageRankingEntry.empty());
        }
    }

    private static void writeRankingEntries(
        Map<String, Object> packet,
        String prefix,
        BossDamageRankingSnapshot ranking,
        int maxEntries
    ) {
        for (int rank = 1; rank <= ranking.entryLimit(); rank++) {
            writeRankingEntry(packet, prefix, rank, ranking.entry(rank));
        }
        for (int rank = ranking.entryLimit() + 1; rank <= Math.max(ranking.entryLimit(), maxEntries); rank++) {
            writeRankingEntry(packet, prefix, rank, BossDamageRankingEntry.empty());
        }
    }

    private static void writeRankingEntry(Map<String, Object> packet, String prefix, int rank, BossDamageRankingEntry entry) {
        String rankPrefix = prefix + "top" + rank;
        boolean present = entry.rank() > 0;
        packet.put(rankPrefix + "Name", entry.playerName());
        packet.put(rankPrefix + "Uuid", present ? entry.playerUuid().toString() : "");
        packet.put(rankPrefix + "Rank", entry.rank());
        packet.put(rankPrefix + "Qualified", entry.qualified());
        packet.put(rankPrefix + "Damage", entry.damage());
        packet.put(rankPrefix + "DamageText", formatNumber(entry.damage()));
        packet.put(rankPrefix + "DamagePercent", entry.damagePercent());
        packet.put(rankPrefix + "DamagePercentText", formatNumber(entry.damagePercent()));
        packet.put(rankPrefix + "TakenDamage", entry.takenDamage());
        packet.put(rankPrefix + "TakenDamageText", formatNumber(entry.takenDamage()));
    }

    private static String formatNumber(double value) {
        return NUMBER_FORMAT.format(value);
    }

    private void sendLifecycleCards(BossSession session, LivingEntity entity, String cardId, String state) {
        if (cardId == null || cardId.isBlank()) {
            return;
        }
        for (Player player : entity.getWorld().getPlayers()) {
            if (!session.canView(player, entity, configuration)) {
                continue;
            }
            arcartXBridge.sendChatCard(player, cardId, session.toChatCardData(player, entity, state));
        }
    }

    private static String resolveMobId(ActiveMob activeMob) {
        if (activeMob.getType() != null && activeMob.getType().getInternalName() != null) {
            return activeMob.getType().getInternalName();
        }
        return activeMob.getMobType();
    }

    private void recordVanillaPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        BossSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            return;
        }

        Player attacker = CombatEventSupport.resolvePlayerAttacker(event);
        if (attacker == null) {
            return;
        }

        double damage = resolveEffectiveDamage(target, event.getFinalDamage());
        if (damage <= 0.0D) {
            return;
        }
        session.recordDamage(attacker, damage);
        requestRefresh();
    }

    private void recordBossDamageTaken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) {
            return;
        }

        BossSession session = resolveDamagerSession(event.getDamager());
        if (session == null) {
            return;
        }

        double damage = resolveEffectiveDamage(targetPlayer, event.getFinalDamage());
        if (damage <= 0.0D) {
            return;
        }
        session.recordTakenDamage(targetPlayer, damage);
        requestRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTaczGunDamage(TaczGunDamageEvent event) {
        LivingEntity target = event.getTarget();
        BossSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            return;
        }
        Player attacker = event.getAttacker();
        double damage = resolveEffectiveDamage(target, event.getDamage());
        if (damage <= 0.0D) {
            return;
        }
        session.recordDamage(attacker, damage);
        requestRefresh();
    }

    private BossSession resolveDamagerSession(Entity damager) {
        if (damager == null) {
            return null;
        }

        BossSession direct = sessions.get(damager.getUniqueId());
        if (direct != null) {
            return direct;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return sessions.get(shooter.getUniqueId());
        }
        return null;
    }

    private static double resolveEffectiveDamage(LivingEntity target, double rawDamage) {
        if (target == null || rawDamage <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(rawDamage, target.getHealth()));
    }

    private Comparator<TrackedBossSnapshot> comparatorFor(Player player, BossSortMode sortMode) {
        Comparator<TrackedBossSnapshot> fallback = SNAPSHOT_ORDER;
        Location playerLocation = player.getLocation();
        return switch (sortMode) {
            case DISTANCE -> Comparator
                .comparingDouble((TrackedBossSnapshot snapshot) -> playerLocation.distanceSquared(snapshot.renderState().location()))
                .thenComparing(fallback);
            case HEALTH_PERCENT -> Comparator
                .comparingDouble((TrackedBossSnapshot snapshot) -> snapshot.renderState().progress())
                .thenComparing(fallback);
            case PRIORITY -> Comparator
                .comparingInt((TrackedBossSnapshot snapshot) -> snapshot.session().getDefinition().priority())
                .reversed()
                .thenComparing(fallback);
            case HYBRID -> Comparator
                .comparingInt((TrackedBossSnapshot snapshot) -> snapshot.session().getDefinition().priority())
                .reversed()
                .thenComparingDouble(snapshot -> playerLocation.distanceSquared(snapshot.renderState().location()))
                .thenComparingDouble(snapshot -> snapshot.renderState().progress())
                .thenComparing(fallback);
            case SPAWN_ORDER -> fallback;
        };
    }

    private record TrackedBossSnapshot(BossSession session, BossSession.BossRenderState renderState) {
    }

    private record VisibleBossView(List<TrackedBossSnapshot> visibleSnapshots, long totalVisibleCount) {
        private static final VisibleBossView EMPTY = new VisibleBossView(List.of(), 0L);
    }

    private record ViewerState(PlayerBossViewSnapshot snapshot) {
    }

    private record SessionRegistration(BossSession session, boolean created) {
        private static final SessionRegistration SKIPPED = new SessionRegistration(null, false);
    }

    private void dispatchBossSettlementSignals(BossSession session, BossDamageSettlementRecord record) {
        if (record == null) {
            return;
        }
        // Signal for each participating player with their personal rank/damage context
        for (BossDamageSettlementEntry entry : record.trackedEntries()) {
            if (entry.rank() <= 0) {
                continue;
            }
            Player participant = Bukkit.getPlayer(entry.playerUuid());
            if (participant == null || !participant.isOnline()) {
                continue;
            }
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("boss_id", record.mythicMobId());
            variables.put("boss_name", record.bossDisplayName());
            variables.put("settlement_id", record.settlementId());
            variables.put("rank", String.valueOf(entry.rank()));
            variables.put("damage", NUMBER_FORMAT.format(entry.damage()));
            variables.put("total_damage", NUMBER_FORMAT.format(record.totalDamage()));
            variables.put("participant_count", String.valueOf(record.participantCount()));
            if (signalDispatcher != null) {
                signalDispatcher.accept("boss_settlement", participant);
            }
        }
    }
}
