package xuanmo.arcartxsuite.entitytracker;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.entitytracker.config.EntityTrackerStorageConfiguration;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import xuanmo.arcartxsuite.entitytracker.command.EntityTrackerAdminCommand;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService;
import xuanmo.arcartxsuite.entitytracker.service.CrossServerRankingCacheService;
import xuanmo.arcartxsuite.entitytracker.config.EntityTrackerNewFeaturesSettings;
import xuanmo.arcartxsuite.entitytracker.crossserver.EntityTrackerCrossServerService;
import xuanmo.arcartxsuite.entitytracker.command.RankingRewardCommand;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionExecutor;
import xuanmo.arcartxsuite.entitytracker.reward.BossRewardItems;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.boss.placeholder.EntityTrackerPlaceholderExpansion;
import xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossSession;
import xuanmo.arcartxsuite.entitytracker.target.config.EntityTargetHudConfiguration;
import xuanmo.arcartxsuite.entitytracker.target.service.EntityTargetHudService;

public final class EntityTrackerModule extends AbstractAXSModule implements ModuleCommandHandler {

    private EntityTrackerAdminCommand adminCommand;
    private RankingRewardCommand rewardCommand;

    private static final String BOSS_UI_RESOURCE_PATH = "arcartx/ui/boss_tracker.yml";
    private static final String BOSS_UI_FILE_PATH = "ui/boss_tracker.yml";
    private static final String TARGET_UI_RESOURCE_PATH = "arcartx/ui/attack_target_hud.yml";
    private static final String TARGET_UI_FILE_PATH = "ui/attack_target_hud.yml";
    private static final String REWARD_MANAGER_UI_RESOURCE_PATH = "arcartx/ui/ranking_rewards.yml";
    private static final String REWARD_MANAGER_UI_FILE_PATH = "ui/ranking_rewards.yml";
    private static final String REWARD_HISTORY_UI_RESOURCE_PATH = "arcartx/ui/reward_history.yml";
    private static final String REWARD_HISTORY_UI_FILE_PATH = "ui/reward_history.yml";

    private PluginConfiguration configuration;
    private File bossesDirectory;
    private EntityTargetHudConfiguration targetConfiguration;
    private BossTrackerService bossService;
    private Map<UUID, BossSession> bossSessionsOnReload = Map.of();
    private EntityTargetHudService targetService;
    private RankingRewardService rewardService;
    private RankingRewardScheduler rewardScheduler;
    private xuanmo.arcartxsuite.entitytracker.reward.RewardPacketHandler rewardPacketHandler;
    private String rewardManagerUiId;
    private String rewardHistoryUiId;
    private CrossServerChannelConfig crossServerChannelConfig = CrossServerChannelConfig.disabled();
    private EntityTrackerNewFeaturesSettings newFeaturesSettings = EntityTrackerNewFeaturesSettings.defaults();
    private FileConfiguration moduleYaml;
    private EntityTrackerCrossServerService crossServerService;
    private BossKillRecordingService killRecordingService;
    private CrossServerRankingCacheService rankingCacheService;
    private JdbcEntityTrackerRepository repository;
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;
    private java.util.concurrent.CompletableFuture<Void> storageInitialization;
    private xuanmo.arcartxsuite.api.scheduler.ModuleCallback runtimeCalls;
    private final List<java.util.concurrent.CompletableFuture<Void>> retiredServices = new java.util.ArrayList<>();
    private List<String> bossRuntimeUiIds = List.of();
    private List<String> targetRuntimeUiIds = List.of();

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("entitytracker")
            .name("EntityTracker")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXEntityTracker.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("boss")
                    .dynamicSection("attack-target")
                .build())
            .currentVersion(4)
            .validations(List.of(
                    // Boss追踪刷新间隔
                    ValidationRule.of("boss.update-interval-ticks", ValueType.INT)
                        .withRange(1, null),
                    // 目标HUD刷新间隔
                    ValidationRule.of("attack-target.refresh-interval-ticks", ValueType.INT)
                        .withRange(1, null)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(BOSS_UI_RESOURCE_PATH, BOSS_UI_FILE_PATH);
        mappings.put(TARGET_UI_RESOURCE_PATH, TARGET_UI_FILE_PATH);
        mappings.put(REWARD_MANAGER_UI_RESOURCE_PATH, REWARD_MANAGER_UI_FILE_PATH);
        mappings.put(REWARD_HISTORY_UI_RESOURCE_PATH, REWARD_HISTORY_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.overwriteUiFile());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        if (rewardService == null) return PacketHandlerSpec.NONE;
        rewardPacketHandler = new xuanmo.arcartxsuite.entitytracker.reward.RewardPacketHandler(
            plugin, packetBridge, packetGuard,
            rewardService, rewardScheduler,
            () -> configuration,
            rewardManagerUiId, rewardHistoryUiId,
            dataFolder, logger
        );
        return PacketHandlerSpec.of(rewardPacketHandler, 0, "AXS_ENTITY_TRACKER_REWARD", "entitytracker");
    }

    @Override
    protected List<org.bukkit.event.Listener> createListeners() {
        return List.of(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (rewardPacketHandler != null) {
                    rewardPacketHandler.clearPlayerState(event.getPlayer().getUniqueId());
                }
            }
        });
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXEntityTracker.yml 配置文件缺失");
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        moduleYaml = yaml;
        ConfigurationSection uiSection = yaml.getConfigurationSection("ui");
        ConfigurationSection bossSection = yaml.getConfigurationSection("boss");
        String bossesDirRelative = bossSection != null
            ? bossSection.getString("bosses-directory", "bosses") : "bosses";
        File bossesDirectory = new File(dataFolder, bossesDirRelative);
        ensureBossDefaults(bossesDirRelative);
        this.bossesDirectory = bossesDirectory;
        configuration = PluginConfiguration.from(bossSection, uiSection, bossesDirectory);
        targetConfiguration = EntityTargetHudConfiguration.load(yaml.getConfigurationSection("attack-target"), uiSection);
        crossServerChannelConfig = CrossServerChannelConfigs.fromSection(yaml.getConfigurationSection("cross-server"));
        newFeaturesSettings = EntityTrackerNewFeaturesSettings.load(yaml.getConfigurationSection("new-features"));
    }

    private void ensureBossDefaults(String bossesRelative) {
        File bossesDir = new File(dataFolder, bossesRelative);
        if (!bossesDir.exists()) {
            bossesDir.mkdirs();
        }
        File[] existing = bossesDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        File target = new File(bossesDir, "ExampleBoss.yml");
        if (!target.exists()) {
            exportResource("bosses/ExampleBoss.yml", target, false);
        }
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.entitytracker.boss_kill");
    }

    @Override
    protected void startService() throws Exception {
        runtimeCalls = new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});
        retiredServices.clear();
        if (storageInitialization != null && storageInitialization.isCompletedExceptionally()) retireUnavailableStorage();
        storageInitialization = null;
        initializePersistenceServices();

        // Boss tracker depends on MythicMobs/MythicBukkit. If it is absent, keep the module alive
        // and only run the generic attack-target HUD below.
        boolean mythicAvailable = hasPlugin("MythicMobs") || hasPlugin("MythicBukkit");
        if (mythicAvailable) {
            List<String> bossUiIdCandidates = configuration.uiIds();
            List<String> resolvedBossUiIds = new java.util.ArrayList<>();
            for (String candidateUiId : bossUiIdCandidates) {
                UiBinding bossBinding = registerModuleUi(
                    BOSS_UI_RESOURCE_PATH,
                    BOSS_UI_FILE_PATH,
                    candidateUiId,
                    configuration.registerUiOnEnable()
                );
                if (bossBinding.registeredUiId() != null) {
                    resolvedBossUiIds.add(bossBinding.runtimeUiId());
                }
            }
            if (configuration.registerUiOnEnable() && resolvedBossUiIds.isEmpty()) {
                throw new IllegalStateException("EntityTracker Boss UI 注册失败");
            }
            bossRuntimeUiIds = List.copyOf(resolvedBossUiIds);

            ServerPlatform platform = ServerPlatform.detect(plugin.getServer());
            bossService = new BossTrackerService(
                plugin, logger, configuration, packetBridge, bossRuntimeUiIds,
                platform, null, () -> getCapability(AxsMailService.class),
                itemSourceRegistry, attributeBridge,
                crossServerService, killRecordingService, placeholderResolver, messages(),
                pendingRewardService
            );
            bossService.start(bossSessionsOnReload);
            adminCommand = new EntityTrackerAdminCommand(() -> bossService, messages());
        } else {
            bossRuntimeUiIds = List.of();
            logger.warning("未检测到 MythicMobs/MythicBukkit，EntityTracker Boss 追踪已跳过，普通攻击目标 HUD 仍可使用。");
        }

        // Target HUD
        List<String> targetUiIdCandidates = targetConfiguration.uiIds();
        List<String> resolvedTargetUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : targetUiIdCandidates) {
            UiBinding targetBinding = registerModuleUi(
                TARGET_UI_RESOURCE_PATH,
                TARGET_UI_FILE_PATH,
                candidateUiId,
                targetConfiguration.registerUiOnEnable()
            );
            if (targetBinding.registeredUiId() != null) {
                resolvedTargetUiIds.add(targetBinding.runtimeUiId());
            }
        }
        if (!resolvedTargetUiIds.isEmpty() || !targetConfiguration.registerUiOnEnable()) {
            targetRuntimeUiIds = List.copyOf(resolvedTargetUiIds);
            PluginConfiguration bossConf = configuration;
            BossTrackerService trackedBosses = bossService;
            targetService = new EntityTargetHudService(
                plugin,
                logger,
                () -> bossConf,
                targetConfiguration,
                packetBridge,
                targetRuntimeUiIds,
                entityUuid -> trackedBosses != null
                    && trackedBosses.isTrackedConfiguredBoss(entityUuid)
            );
            targetService.start();
        }

        // 初始化排行榜奖励系统
        initializeRewardSystem();
        if (rewardService != null && packetBridge != null && packetBridge.isAvailable()) {
            rewardManagerUiId = bindRewardUi(REWARD_MANAGER_UI_RESOURCE_PATH, REWARD_MANAGER_UI_FILE_PATH,
                "AXS:ranking_rewards");
            rewardHistoryUiId = bindRewardUi(REWARD_HISTORY_UI_RESOURCE_PATH, REWARD_HISTORY_UI_FILE_PATH,
                "AXS:reward_history");
        }
        if (bossService != null && rewardService != null) {
            bossService.setPeriodScoreRecorder(rewardService::recordPeriodScores);
        }

        // 注册标准 capability
        if (repository != null) {
            var currentRepository = repository;
            var calls = runtimeCalls;
            registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
                new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                    @Override public @NotNull String moduleId() { return "entitytracker"; }
                    @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                        return calls.call(() -> { try { return currentRepository.deletePlayerData(playerUuid); }
                        catch (Exception e) { logger.warning("EntityTracker purge 失败: " + e.getMessage()); return -1; } }, -1);
                    }
                    @Override public int purgeAllPlayerData() {
                        return calls.call(() -> { try { return currentRepository.deleteAllPlayerData(); }
                        catch (Exception e) { logger.warning("EntityTracker purgeAll 失败: " + e.getMessage()); return -1; } }, -1);
                    }
                });
            registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
                new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                    @Override public @NotNull String moduleId() { return "entitytracker"; }
                    @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                            @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                        var unavailable = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
                        unavailable.addError("EntityTracker 已停止");
                        return calls.call(() -> currentRepository.migrateTo(target, overwrite), unavailable);
                    }
                    @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                        return currentRepository.getDescriptor();
                    }
                });
        }

        var trackedBosses = bossService;
        registerCapability(xuanmo.arcartxsuite.api.capability.BossTrackerQueryable.class,
            new xuanmo.arcartxsuite.api.capability.BossTrackerQueryable() {
                @Override public boolean isTrackedBoss(@NotNull java.util.UUID entityUuid) {
                    return trackedBosses != null && trackedBosses.isTrackedConfiguredBoss(entityUuid);
                }
                @Override public int activeSessionCount() {
                    return trackedBosses != null ? trackedBosses.getActiveSessionCount() : 0;
                }
                @Override public int activeViewerCount() {
                    return trackedBosses != null ? trackedBosses.getActiveViewerCount() : 0;
                }
                @Override public @NotNull List<BossSessionInfo> activeSessions(
                        @Nullable String mobIdFilter) {
                    if (trackedBosses == null) return List.of();
                    return trackedBosses.activeSessions(mobIdFilter).stream()
                        .map(v -> new BossSessionInfo(v.entityUuid(), v.mythicMobId(),
                            v.displayName(), v.health(), v.maxHealth(),
                            v.participantCount(), v.trackedPlayerCount(), v.totalDamage()))
                        .toList();
                }
            });

        logger.fine(
            "EntityTracker 模块已载入，bosses=" + configuration.getTrackedBossCount()
                + " | boss-ui=" + bossRuntimeUiIds
                + " | target-ui=" + targetRuntimeUiIds
                + " | reward-system=" + (rewardService != null ? "enabled" : "disabled")
                + " | drop-recording=" + (newFeaturesSettings.dropRecording().enabled() ? "ON" : "OFF")
                + " | drop-allocation=" + (newFeaturesSettings.dropAllocation().enabled() ? "ON" : "OFF")
                + " | cross-server-ranking=" + (crossServerService != null && crossServerService.isActive() ? "ON" : "OFF")
        );
    }

    private void initializePersistenceServices() {
        javax.sql.DataSource ds = dataSource();
        if (ds == null) {
            return;
        }
        boolean crossServerRanking = newFeaturesSettings.crossServerRanking().enabled()
            && crossServerChannelConfig.enabled();
        if (crossServerRanking) {
            crossServerService = new EntityTrackerCrossServerService(
                plugin, logger, crossServer, crossServerChannelConfig, ds
            );
            var currentConfiguration = configuration;
            rankingCacheService = new CrossServerRankingCacheService(
                plugin, logger,
                newFeaturesSettings.crossServerRanking(),
                ds,
                () -> currentConfiguration,
                () -> crossServer.nodeId()
            );
        }
        if (newFeaturesSettings.dropRecording().enabled()
            || newFeaturesSettings.dropAllocation().enabled()
            || crossServerRanking) {
            killRecordingService = new BossKillRecordingService(
                plugin, logger,
                newFeaturesSettings,
                ds,
                pendingRewardService,
                crossServerService,
                rankingCacheService,
                () -> crossServer.nodeId(),
                dataFolder.toPath().resolve("kill-outbox"), messages()
            );
            killRecordingService.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
            killRecordingService.start();
            if (crossServerService != null) {
                crossServerService.attachKillRecording(killRecordingService);
                crossServerService.attachRankingCache(rankingCacheService);
                crossServerService.start();
                rankingCacheService.start();
            }
            killRecordingService.purgeOldRecords();
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> onReload() {
        bossSessionsOnReload = bossService == null ? Map.of() : bossService.captureSessionsForReload();
        return super.onReload().whenComplete((ignored, failure) -> bossSessionsOnReload = Map.of()).toCompletableFuture().copy();
    }

    @Override
    protected void stopService() {
        var scope = storageScope;
        storageScope = null;
        storageInitialization = null;
        if (runtimeCalls != null) {
            runtimeCalls.close();
            retiredServices.add(runtimeCalls.stopped().toCompletableFuture());
            runtimeCalls = null;
        }
        if (targetService != null) {
            retireService(targetService::shutdown, targetService::databaseStopped);
            targetService = null;
        }
        if (bossService != null) {
            retireService(bossService::shutdown, bossService::databaseStopped);
            bossService = null;
        }
        
        // 关闭排行榜奖励系统
        if (rewardScheduler != null) {
            retireService(rewardScheduler::shutdown, rewardScheduler::databaseStopped);
            rewardScheduler = null;
        }
        if (rewardService != null) retireService(rewardService::shutdown, rewardService::databaseStopped);
        rewardService = null;
        rewardPacketHandler = null;
        rewardManagerUiId = null;
        rewardHistoryUiId = null;
        rewardCommand = null;

        if (killRecordingService != null) {
            retireService(killRecordingService::shutdown, killRecordingService::databaseStopped);
            killRecordingService = null;
        }
        if (rankingCacheService != null) {
            retireService(rankingCacheService::shutdown, rankingCacheService::databaseStopped);
            rankingCacheService = null;
        }
        if (crossServerService != null) {
            retireService(crossServerService::shutdown, crossServerService::databaseStopped);
            crossServerService = null;
        }
        repository = null;
        var stopped = java.util.concurrent.CompletableFuture.allOf(
            retiredServices.toArray(java.util.concurrent.CompletableFuture[]::new));
        retiredServices.clear();
        trackShutdown(stopped);
        if (scope != null) {
            scope.retireAfter(stopped);
            trackShutdown(scope.retired());
        }
        adminCommand = null;
        moduleYaml = null;
        configuration = null;
        bossesDirectory = null;
        targetConfiguration = null;
    }

    private void retireService(Runnable close,
            java.util.function.Supplier<java.util.concurrent.CompletionStage<Void>> stopped) {
        try { close.run(); }
        catch (RuntimeException | LinkageError failure) {
            logger.log(java.util.logging.Level.WARNING, "EntityTracker 服务关闭失败", failure);
            retiredServices.add(java.util.concurrent.CompletableFuture.failedFuture(failure));
        } finally { retiredServices.add(stopped.get().toCompletableFuture()); }
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new EntityTrackerPlaceholderExpansion(
            plugin,
            () -> configuration,
            () -> bossService,
            () -> bossRuntimeUiIds.isEmpty() ? "" : bossRuntimeUiIds.get(0),
            () -> packetBridge,
            () -> targetService
        );
    }

    public BossTrackerService getBossTrackerService() {
        return bossService;
    }

    public PluginConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * 初始化排行榜奖励系统
     */
    private void initializeRewardSystem() {
        try {
            if (moduleYaml == null) {
                logger.info("排行榜奖励系统：模块配置尚未加载，跳过初始化");
                return;
            }

            ConfigurationSection rewardSection = moduleYaml.getConfigurationSection("new-features.ranking-rewards");
            if (rewardSection == null) {
                logger.info("排行榜奖励系统未配置，跳过初始化");
                return;
            }

            javax.sql.DataSource ds = dataSource();
            if (ds == null) {
                logger.severe("排行榜奖励系统数据库初始化失败");
                return;
            }

            // 创建通用奖励执行器
            java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher =
                (signal, player) -> {
                    var dispatcher = getCapability(xuanmo.arcartxsuite.api.capability.SignalDispatchable.class);
                    if (dispatcher == null) throw new IllegalStateException("EventPacket 信号能力不可用");
                    dispatcher.dispatchSignal(signal, player, java.util.Map.of());
                };
            var actionExecutor = new RewardActionExecutor(signalDispatcher, placeholderResolver);

            // 创建服务实例
            var currentConfiguration = configuration;
            rewardService = new RankingRewardService(
                ds, actionExecutor, new BossRewardItems(itemSourceRegistry, pendingRewardService),
                () -> getCapability(AxsMailService.class), repository.getDescriptor().isMysql(), plugin, logger,
                () -> currentConfiguration, bossesDirectory
            );
            rewardScheduler = new RankingRewardScheduler(rewardService, plugin, logger);
            rewardCommand = new RankingRewardCommand(rewardService, rewardScheduler,
                new File(dataFolder, "config.yml"), logger, messages(), plugin, this::openRewardUi);

            // 初始化调度器（传入主配置）
            rewardScheduler.initialize(moduleYaml);

            logger.info("排行榜奖励系统初始化完成");

        } catch (Exception e) {
            if (rewardScheduler != null) retireService(rewardScheduler::shutdown, rewardScheduler::databaseStopped);
            if (rewardService != null) retireService(rewardService::shutdown, rewardService::databaseStopped);
            rewardScheduler = null;
            rewardService = null;
            rewardCommand = null;
            logger.severe("排行榜奖励系统初始化失败: " + e.getMessage());
            // 不抛出异常，允许模块继续运行其他功能
        }
    }

    private String bindRewardUi(String resource, String file, String id) throws java.io.IOException {
        UiBinding binding = registerModuleUi(resource, file, id, true);
        return binding.registeredUiId() == null ? null : binding.runtimeUiId();
    }

    private boolean openRewardUi(org.bukkit.entity.Player player, boolean history) {
        String uiId = history ? rewardHistoryUiId : rewardManagerUiId;
        return uiId != null && packetBridge != null && packetBridge.isAvailable() && packetBridge.openUi(player, uiId);
    }

    private boolean hasRankingRewardsSection() {
        return moduleYaml != null
            && moduleYaml.getConfigurationSection("new-features.ranking-rewards") != null;
    }

    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() {
        if (!newFeaturesSettings.needsDatabase() && !hasRankingRewardsSection()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        try {
            ConfigurationSection storageSection = moduleYaml.getConfigurationSection("storage");
            EntityTrackerStorageConfiguration storageConfig = EntityTrackerStorageConfiguration.load(storageSection);
            xuanmo.arcartxsuite.api.storage.StorageDescriptor etDesc;
            javax.sql.DataSource etDs;
            if (storageConfig.hasOverride()) {
                etDesc = storageConfig.toDescriptor();
                storageScope = storageManager.openModuleStorage("entitytracker", null, dataFolder, etDesc);
                etDs = storageScope.dataSource();
            } else {
                etDesc = storageManager.getDescriptor().withTablePrefix("");
                storageScope = storageManager.openModuleStorage("entitytracker",
                    storageConfig.sqliteFileName(), dataFolder, null);
                etDs = storageScope.dataSource();
            }
            repository = new JdbcEntityTrackerRepository(etDs, etDesc, dataFolder, logger);
            storageInitialization = storageScope.initialize(repository::initialize).toCompletableFuture();
            return storageInitialization.exceptionally(failure -> {
                logger.severe("EntityTracker 数据库初始化失败: " + failure.getMessage());
                return null;
            });
        } catch (Exception e) {
            retireUnavailableStorage();
            logger.severe("EntityTracker 数据库初始化失败: " + e.getMessage());
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private void retireUnavailableStorage() {
        var scope = storageScope;
        storageScope = null;
        repository = null;
        if (scope != null) {
            scope.retireAfter(java.util.concurrent.CompletableFuture.completedFuture(null));
            trackShutdown(scope.retired());
        }
    }

    private javax.sql.DataSource dataSource() {
        return repository != null ? repository.dataSource() : null;
    }

    // ─── ModuleCommandHandler ───────────────────────────────

    @Override
    public String commandId() {
        return "entitytracker";
    }

    @Override
    public List<String> actions() {
        List<String> actions = new java.util.ArrayList<>();
        if (adminCommand != null) {
            actions.addAll(adminCommand.actions());
        }
        if (rewardCommand != null) {
            actions.addAll(List.of("rewards"));
        }
        return actions.isEmpty() ? List.of("help", "status") : actions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length > 1 && "rewards".equalsIgnoreCase(args[1])) {
            // 奖励命令处理
            if (rewardCommand != null) {
                String[] rewardArgs = Arrays.copyOfRange(args, 2, args.length);
                return rewardCommand.onCommand(sender, label + " entitytracker rewards", rewardArgs);
            }
            sender.sendMessage("§c[EntityTracker] 排行榜奖励系统未启用。");
            return true;
        }
        
        // 原有命令处理
        if (adminCommand != null) {
            return adminCommand.onCommand(sender, label, args);
        }
        sender.sendMessage("\u00a7c[EntityTracker] BossTracker 服务未启动，管理命令不可用。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            // 第一层补全：包含 rewards 选项
            List<String> completions = new java.util.ArrayList<>();
            if (adminCommand != null) {
                List<String> adminCompletions = adminCommand.onTabComplete(sender, args);
                if (adminCompletions != null) {
                    completions.addAll(adminCompletions);
                }
            }
            if (rewardCommand != null && sender.hasPermission("entitytracker.rewards.admin")
                && "rewards".startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                completions.add("rewards");
            }
            return completions;
        }
        
        if (args.length > 2 && "rewards".equalsIgnoreCase(args[1])) {
            // 奖励命令补全
            if (rewardCommand != null) {
                String[] rewardArgs = Arrays.copyOfRange(args, 2, args.length);
                return rewardCommand.onTabComplete(sender, rewardArgs);
            }
            return List.of();
        }
        
        // 原有命令补全
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

