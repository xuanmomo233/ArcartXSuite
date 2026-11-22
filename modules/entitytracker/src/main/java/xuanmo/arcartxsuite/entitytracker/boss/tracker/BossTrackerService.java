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
import java.util.HashMap;
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
import java.util.logging.Logger;

/**
 * Boss 追踪服务，属于 entitytracker 模块的核心服务。
 * <p>
 * 监听 MythicMob 生成/死亡/消失事件和实体伤害事件，管理 Boss 会话生命周期，
 * 定时刷新观察者 HUD 数据包，处理混合服务端的实体重扫描，
 * 并协调伤害结算、跨服排名和击杀记录等子服务。
 */
public final class BossTrackerService implements Listener {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");
    private static final Comparator<TrackedBossSnapshot> SNAPSHOT_ORDER = Comparator
        .comparingLong((TrackedBossSnapshot snapshot) -> snapshot.renderState().spawnOrder())
        .thenComparingLong(snapshot -> snapshot.renderState().spawnedAt())
        .thenComparing(snapshot -> snapshot.session().getEntityUuid().toString());
    private static final long HYBRID_WARMUP_RESCAN_INTERVAL_TICKS = 20L;
    private static final int HYBRID_WARMUP_RESCAN_ATTEMPTS = 6;
    private static final long HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS = 100L;
    private static final int HYBRID_PERSISTENT_EMPTY_THRESHOLD = 10; // 连续 N 次未发现遗漏则降频

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PluginConfiguration configuration;
    private final PacketBridgeAPI arcartXBridge;
    private final List<String> runtimeUiIds;
    private final java.util.function.BiConsumer<String, Player> signalDispatcher;
    private final xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform;
    private final BossDamageSettlementService settlementService;
    private final EntityTrackerCrossServerService crossServerService;
    private final BossKillRecordingService killRecordingService;
    private java.util.function.Consumer<BossDamageSettlementRecord> periodScoreRecorder = record -> {};

    public void setPeriodScoreRecorder(java.util.function.Consumer<BossDamageSettlementRecord> recorder) {
        periodScoreRecorder = recorder;
    }
    private final xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge;
    private final Map<UUID, BossSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;
    private volatile boolean closed;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback refresh =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::refreshViewers);
    private BukkitTask queuedRefreshTask;
    private BukkitTask hybridWarmupRescanTask;
    private BukkitTask hybridPersistentRescanTask;
    private int hybridWarmupRescanAttempts;
    private int hybridPersistentEmptyStreak;
    private long hybridPersistentCurrentInterval = HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS;
    private long nextSpawnOrder = 0L;
    private xuanmo.arcartxsuite.api.attribute.AttributeDamageListener attributeDamageListener;

    public BossTrackerService(
        JavaPlugin plugin,
        Logger logger,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this(plugin, logger, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, null, null, null, null, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        Logger logger,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        EntityTrackerCrossServerService crossServerService
    ) {
        this(plugin, logger, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, crossServerService, null, null, null, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        Logger logger,
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
        this(plugin, logger, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            () -> null, itemSourceRegistry, attributeBridge, crossServerService, killRecordingService, null, null, null);
    }

    public BossTrackerService(
        JavaPlugin plugin,
        Logger logger,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.mail.AxsMailService> mailProvider,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        EntityTrackerCrossServerService crossServerService,
        BossKillRecordingService killRecordingService,
        PlaceholderResolverAPI placeholderResolver,
        xuanmo.arcartxsuite.api.message.MessageProvider messages,
        xuanmo.arcartxsuite.api.item.PendingRewardService pendingRewardService
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.arcartXBridge = arcartXBridge;
        this.runtimeUiIds = runtimeUiIds;
        this.serverPlatform = serverPlatform;
        this.signalDispatcher = signalDispatcher;
        this.attributeBridge = attributeBridge;
        this.crossServerService = crossServerService;
        this.killRecordingService = killRecordingService;
        this.settlementService = new BossDamageSettlementService(
            plugin, logger, mailProvider, signalDispatcher, itemSourceRegistry,
            placeholderResolver, messages, pendingRewardService
        );
    }

    public BossTrackerService(
        JavaPlugin plugin,
        Logger logger,
        PluginConfiguration configuration,
        PacketBridgeAPI arcartXBridge,
        List<String> runtimeUiIds,
        xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform serverPlatform,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.mail.AxsMailService> mailProvider,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this(plugin, logger, configuration, arcartXBridge, runtimeUiIds, serverPlatform, signalDispatcher,
            mailProvider, itemSourceRegistry, attributeBridge, null, null, null, null, null);
    }

    public Map<UUID, BossSession> captureSessionsForReload() {
        return Map.copyOf(sessions);
    }

    public void start(Map<UUID, BossSession> previousSessions) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (BossSession session : previousSessions.values()) {
            nextSpawnOrder = Math.max(nextSpawnOrder, session.getSpawnOrder());
        }
        restoreTrackedBosses("启动扫描", true, previousSessions);
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            refresh,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
        if (attributeBridge != null && attributeBridge.hasDamageSource()) {
            attributeDamageListener = event -> {
                if (closed) return;
                if (!(event.target() instanceof LivingEntity target)) return;
                BossSession session = sessions.get(target.getUniqueId());
                if (session == null) return;
                Player attacker = event.attacker();
                if (attacker == null) return;
                double effectiveDamage = resolveEffectiveDamage(target, event.damage());
                if (effectiveDamage <= 0.0D) return;
                session.recordDamage(attacker, effectiveDamage);
            };
            attributeBridge.registerDamageListener(attributeDamageListener);
        }
        scheduleHybridRescansIfNeeded();
        requestRefresh();
    }

    public void shutdown() {
        closed = true;
        refresh.close();
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

    public java.util.concurrent.CompletionStage<Void> databaseStopped() {
        return java.util.concurrent.CompletableFuture.allOf(refresh.stopped().toCompletableFuture(),
            settlementService.databaseStopped().toCompletableFuture());
    }

    public boolean isTrackedConfiguredBoss(UUID entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        BossSession session = sessions.get(entityUuid);
        return session != null
            && session.getDefinition() != null
            && session.getDefinition().enabled();
    }

    public int getActiveViewerCount() {
        return (int) viewerStates.values().stream().filter(ViewerState::open).count();
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

    public boolean settlementIssuing(String settlementId) { return settlementService.issuing(settlementId); }

    public java.util.concurrent.CompletionStage<BossDamageRewardDispatchResult> reissueSettlementReward(String settlementId, int rank, OfflinePlayer overridePlayer) {
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
        return getViewerSnapshotById(player == null ? null : player.getUniqueId());
    }

    public PlayerBossViewSnapshot getViewerSnapshotById(UUID viewerId) {
        ViewerState state = viewerId == null ? null : viewerStates.get(viewerId);
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
        if (record != null) periodScoreRecorder.accept(record);
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
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            viewerStates.clear();
            pruneSessions();
            return;
        }

        List<TrackedBossSnapshot> activeSnapshots = collectActiveSnapshots();
        if (activeSnapshots.isEmpty()) {
            if (viewerStates.isEmpty()) {
                return;
            }
            for (Player player : players) {
                closeBossHud(player);
            }
            return;
        }

        var snapshotsByWorld = indexSnapshotsByWorld(activeSnapshots);
        if (players.size() >= 8) snapshotsByWorld.values().forEach(WorldBossSnapshots::indexCells);

        for (Player player : players) {
            WorldBossSnapshots world = snapshotsByWorld.get(player.getWorld().getUID());
            if (world == null || !player.isOnline() || player.isDead()) {
                closeBossHud(player);
                continue;
            }
            Location playerLocation = player.getLocation();
            VisibleBossView visibleBossView = collectVisibleSessions(playerLocation, world,
                configuration.sortMode(), configuration.maxVisibleBars());
            if (visibleBossView.totalVisibleCount() == 0) {
                closeBossHud(player);
                continue;
            }

            PlayerBossViewSnapshot snapshot = buildViewSnapshot(player, playerLocation, visibleBossView);
            UUID viewerId = player.getUniqueId();
            ViewerState previous = viewerStates.get(viewerId);
            if (runtimeUiIds.isEmpty()) {
                viewerStates.put(viewerId, new ViewerState(snapshot, false, null));
                continue;
            }
            boolean open = previous != null && previous.open();
            if (open && previous.snapshot().equals(snapshot)) {
                continue;
            }
            Map<String, Object> packet = buildCompactUiPacket(snapshot);
            if (open && packet.equals(previous.compactPacket())) {
                viewerStates.put(viewerId, new ViewerState(snapshot, true, previous.compactPacket()));
                continue;
            }
            if (!open && !arcartXBridge.openUiAll(player, runtimeUiIds)) {
                viewerStates.put(viewerId, new ViewerState(snapshot, false, null));
                continue;
            }
            boolean sent = arcartXBridge.sendPacketToAll(player, runtimeUiIds, open ? "update" : "init", packet);
            if (!sent) {
                arcartXBridge.closeUiAll(player, runtimeUiIds);
            }
            viewerStates.put(viewerId, new ViewerState(snapshot, sent, sent ? packet : null));
        }
    }

    private void requestRefresh() {
        if (closed || queuedRefreshTask != null) {
            return;
        }
        queuedRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            queuedRefreshTask = null;
            refresh.run();
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
                if (closed) return;
                hybridWarmupRescanAttempts++;
                int restoredCount = restoreTrackedBosses("Mohist 启动补扫", false);
                if (restoredCount > 0) {
                    this.logger.info("Mohist 启动补扫恢复了 " + restoredCount + " 个遗漏的 MythicMobs Boss。");
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

        hybridPersistentEmptyStreak = 0;
        hybridPersistentCurrentInterval = HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS;
        hybridPersistentRescanTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                if (closed) return;
                int restoredCount = restoreTrackedBosses("Mohist 持续补扫", false);
                if (restoredCount > 0) {
                    this.logger.info("Mohist 持续补扫恢复了 " + restoredCount + " 个遗漏的 MythicMobs Boss。");
                    requestRefresh();
                    hybridPersistentEmptyStreak = 0;
                } else {
                    hybridPersistentEmptyStreak++;
                    if (hybridPersistentEmptyStreak == HYBRID_PERSISTENT_EMPTY_THRESHOLD) {
                        // 连续 N 次未发现遗漏，降低补扫频率到 5 倍间隔
                        long newInterval = HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS * 5;
                        if (newInterval != hybridPersistentCurrentInterval) {
                            hybridPersistentCurrentInterval = newInterval;
                            if (hybridPersistentRescanTask != null) {
                                hybridPersistentRescanTask.cancel();
                                hybridPersistentRescanTask = Bukkit.getScheduler().runTaskTimer(
                                    plugin,
                                    this::runPersistentRescan,
                                    newInterval,
                                    newInterval
                                );
                            }
                            this.logger.info("Mohist 持续补扫：连续 " + HYBRID_PERSISTENT_EMPTY_THRESHOLD
                                + " 次未发现遗漏，降频至 " + newInterval + " ticks。");
                        }
                    }
                }
            },
            HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS,
            HYBRID_PERSISTENT_RESCAN_INTERVAL_TICKS
        );
    }

    private void runPersistentRescan() {
        if (closed) return;
        int restoredCount = restoreTrackedBosses("Mohist 持续补扫", false);
        if (restoredCount > 0) {
            this.logger.info("Mohist 持续补扫恢复了 " + restoredCount + " 个遗漏的 MythicMobs Boss。");
            requestRefresh();
            hybridPersistentEmptyStreak = 0;
        }
    }

    private int restoreTrackedBosses(String reason, boolean logWhenEmpty) {
        return restoreTrackedBosses(reason, logWhenEmpty, Map.of());
    }

    private int restoreTrackedBosses(String reason, boolean logWhenEmpty, Map<UUID, BossSession> previousSessions) {
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
                    activeMob.getDisplayName(),
                    previousSessions.get(livingEntity.getUniqueId())
                );
                if (registration.created()) {
                    restoredCount++;
                }
            }
        } catch (Exception | LinkageError exception) {
            this.logger.warning(reason + "已存在 MythicMobs Boss 失败: " + exception.getMessage());
            return 0;
        }

        if (restoredCount > 0) {
            this.logger.info(reason + "已恢复跟踪 " + restoredCount + " 个已存在的 MythicMobs Boss。");
        } else if (logWhenEmpty) {
            this.logger.fine(reason + "未发现需要恢复的已存在 MythicMobs Boss。");
        }
        return restoredCount;
    }

    private SessionRegistration registerTrackedBoss(LivingEntity livingEntity, String mythicMobId, String displayName) {
        return registerTrackedBoss(livingEntity, mythicMobId, displayName, null);
    }

    private SessionRegistration registerTrackedBoss(LivingEntity livingEntity, String mythicMobId, String displayName,
                                                     BossSession previous) {
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
        BossSession session;
        if (previous != null && previous.matchesMob(mythicMobId)) {
            session = new BossSession(previous, definition);
            session.rememberDisplayName(resolvedDisplayName);
        } else {
            session = new BossSession(livingEntity.getUniqueId(), definition, mythicMobId, resolvedDisplayName, ++nextSpawnOrder);
        }
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

    private Map<UUID, WorldBossSnapshots> indexSnapshotsByWorld(List<TrackedBossSnapshot> activeSnapshots) {
        Map<UUID, WorldBossSnapshots> snapshotsByWorld = new HashMap<>();
        for (TrackedBossSnapshot snapshot : activeSnapshots) {
            World world = snapshot.renderState().location().getWorld();
            if (world == null) {
                continue;
            }
            snapshotsByWorld.computeIfAbsent(world.getUID(), unused -> new WorldBossSnapshots(new ArrayList<>())).all.add(snapshot);
        }
        return snapshotsByWorld;
    }

    static VisibleBossView collectVisibleSessions(Location playerLocation, WorldBossSnapshots world, BossSortMode sortMode, int maxBars) {
        List<TrackedBossSnapshot> nearby = world.nearby(playerLocation);
        if (nearby.isEmpty() && world.wide.isEmpty()) {
            return VisibleBossView.EMPTY;
        }

        List<TrackedBossSnapshot> visibleSnapshots = new ArrayList<>(Math.min(maxBars, nearby.size() + world.wide.size()));
        addVisibleSnapshots(playerLocation, nearby, visibleSnapshots);
        int nearbyCount = visibleSnapshots.size();
        addVisibleSnapshots(playerLocation, world.wide, visibleSnapshots);

        if (visibleSnapshots.isEmpty()) {
            return VisibleBossView.EMPTY;
        }

        // Both candidate lists retain spawn order. Restore it when combining them.
        if (sortMode != BossSortMode.SPAWN_ORDER || nearbyCount > 0 && nearbyCount < visibleSnapshots.size()) {
            visibleSnapshots.sort(comparatorFor(playerLocation, sortMode));
        }
        List<TrackedBossSnapshot> limitedSnapshots = visibleSnapshots.size() > maxBars
            ? List.copyOf(visibleSnapshots.subList(0, maxBars)) : List.copyOf(visibleSnapshots);
        return new VisibleBossView(limitedSnapshots, visibleSnapshots.size());
    }

    private static void addVisibleSnapshots(Location location, List<TrackedBossSnapshot> candidates, List<TrackedBossSnapshot> visible) {
        for (TrackedBossSnapshot candidate : candidates) {
            var state = candidate.renderState();
            if (location.distanceSquared(state.location()) <= state.viewerRangeSquared()) {
                visible.add(candidate);
            }
        }
    }

    private PlayerBossViewSnapshot buildViewSnapshot(Player player, Location playerLocation, VisibleBossView visibleBossView) {
        List<BossViewSlot> slots = new ArrayList<>(visibleBossView.visibleSnapshots().size());
        for (TrackedBossSnapshot snapshot : visibleBossView.visibleSnapshots()) {
            slots.add(snapshot.session().toViewSlot(player, playerLocation, snapshot.renderState()));
        }
        return new PlayerBossViewSnapshot(
            slots.size(),
            visibleBossView.totalVisibleCount(),
            configuration.maxVisibleBars(),
            configuration.sortMode(),
            List.copyOf(slots)
        );
    }

    private static Map<String, Object> buildCompactUiPacket(PlayerBossViewSnapshot snapshot) {
        var rows = new ArrayList<Map<String, Object>>(snapshot.visibleSlots().size());
        for (BossViewSlot slot : snapshot.visibleSlots()) {
            var context = slot.context();
            rows.add(Map.of("id", context.entityUuid(), "title", slot.title(), "distance", context.distanceText(),
                "rank", context.viewerRankText(), "damage", context.viewerDamageText(), "progress", context.progress(),
                "health", context.healthText(), "maxHealth", context.maxHealthText()));
        }
        return Map.of("rows", rows, "totalBossCount", snapshot.totalBossCount());
    }

    private void closeBossHud(Player player) {
        UUID playerId = player.getUniqueId();
        ViewerState previous = viewerStates.remove(playerId);
        if (previous == null || !previous.open()) {
            return;
        }
        arcartXBridge.sendPacketToAll(player, runtimeUiIds, "close", Map.of("bossCount", 0, "totalBossCount", 0));
        arcartXBridge.closeUiAll(player, runtimeUiIds);
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

    private static Comparator<TrackedBossSnapshot> comparatorFor(Location playerLocation, BossSortMode sortMode) {
        Comparator<TrackedBossSnapshot> fallback = SNAPSHOT_ORDER;
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

    record TrackedBossSnapshot(BossSession session, BossSession.BossRenderState renderState) {
    }

    static final class WorldBossSnapshots {
        private static final int CELL_SHIFT = 7;
        private static final double MAX_INDEXED_RANGE_SQUARED = 64.0D * 64.0D;
        private static final int MIN_INDEXED_BOSSES = 16;

        private final List<TrackedBossSnapshot> all;
        private Map<Long, List<TrackedBossSnapshot>> cells = Map.of();
        private List<TrackedBossSnapshot> wide = List.of();

        WorldBossSnapshots(List<TrackedBossSnapshot> snapshots) {
            all = snapshots;
        }

        void indexCells() {
            if (all.size() < MIN_INDEXED_BOSSES) return;

            var byCell = new HashMap<Long, List<TrackedBossSnapshot>>();
            var wideRange = new ArrayList<TrackedBossSnapshot>();
            for (var snapshot : all) {
                var state = snapshot.renderState();
                if (!(state.viewerRangeSquared() <= MAX_INDEXED_RANGE_SQUARED)) {
                    wideRange.add(snapshot);
                    continue;
                }

                // Round outward at cell boundaries. Visibility still uses the exact 3D distance.
                double radius = Math.nextUp(Math.sqrt(state.viewerRangeSquared()));
                double x = state.location().getX();
                double z = state.location().getZ();
                int minX = cell(Math.nextDown(x - radius));
                int maxX = cell(Math.nextUp(x + radius));
                int minZ = cell(Math.nextDown(z - radius));
                int maxZ = cell(Math.nextUp(z + radius));
                for (int cellX = minX; cellX <= maxX; cellX++) {
                    for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                        byCell.computeIfAbsent(cellKey(cellX, cellZ), key -> new ArrayList<>()).add(snapshot);
                    }
                }
            }
            if (!byCell.isEmpty()) {
                cells = byCell;
                wide = wideRange;
            }
        }

        List<TrackedBossSnapshot> nearby(Location location) {
            if (cells.isEmpty()) return all;
            return cells.getOrDefault(cellKey(cell(location.getX()), cell(location.getZ())), List.of());
        }

        private static int cell(double coordinate) {
            return Location.locToBlock(coordinate) >> CELL_SHIFT;
        }

        private static long cellKey(int x, int z) {
            return ((long) x << Integer.SIZE) | Integer.toUnsignedLong(z);
        }
    }

    record VisibleBossView(List<TrackedBossSnapshot> visibleSnapshots, long totalVisibleCount) {
        private static final VisibleBossView EMPTY = new VisibleBossView(List.of(), 0L);
    }

    private record ViewerState(PlayerBossViewSnapshot snapshot, boolean open, Map<String, Object> compactPacket) {
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
