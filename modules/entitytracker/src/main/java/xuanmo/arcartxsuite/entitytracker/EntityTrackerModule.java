package xuanmo.arcartxsuite.entitytracker;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
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
import xuanmo.arcartxsuite.entitytracker.service.DropAllocationService;
import xuanmo.arcartxsuite.entitytracker.config.EntityTrackerNewFeaturesSettings;
import xuanmo.arcartxsuite.entitytracker.crossserver.EntityTrackerCrossServerService;
import xuanmo.arcartxsuite.entitytracker.command.RankingRewardCommand;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionExecutor;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.boss.placeholder.EntityTrackerPlaceholderExpansion;
import xuanmo.arcartxsuite.entitytracker.boss.platform.ServerPlatform;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService;
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
    private static final String REWARD_EDITOR_UI_RESOURCE_PATH = "arcartx/ui/reward_editor.yml";
    private static final String REWARD_EDITOR_UI_FILE_PATH = "ui/reward_editor.yml";
    private static final String REWARD_HISTORY_UI_RESOURCE_PATH = "arcartx/ui/reward_history.yml";
    private static final String REWARD_HISTORY_UI_FILE_PATH = "ui/reward_history.yml";

    private PluginConfiguration configuration;
    private EntityTargetHudConfiguration targetConfiguration;
    private BossTrackerService bossService;
    private EntityTargetHudService targetService;
    private RankingRewardService rewardService;
    private RankingRewardScheduler rewardScheduler;
    private CrossServerChannelConfig crossServerChannelConfig = CrossServerChannelConfig.disabled();
    private EntityTrackerNewFeaturesSettings newFeaturesSettings = EntityTrackerNewFeaturesSettings.defaults();
    private FileConfiguration moduleYaml;
    private EntityTrackerCrossServerService crossServerService;
    private BossKillRecordingService killRecordingService;
    private DropAllocationService dropAllocationService;
    private CrossServerRankingCacheService rankingCacheService;
    private JdbcEntityTrackerRepository repository;
    private List<String> bossRuntimeUiIds = List.of();
    private List<String> targetRuntimeUiIds = List.of();

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("entitytracker")
            .name("EntityTracker")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXEntityTracker.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // boss 与 attack-target 下包含用户自定义条目
        return SyncPolicy.builder()
            .dynamicSection("boss")
            .dynamicSection("attack-target")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // Boss追踪刷新间隔
            ValidationRule.of("boss.update-interval-ticks", ValueType.INT)
                .withRange(1, null),
            // 目标HUD刷新间隔
            ValidationRule.of("attack-target.refresh-interval-ticks", ValueType.INT)
                .withRange(1, null)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(BOSS_UI_RESOURCE_PATH, BOSS_UI_FILE_PATH);
        mappings.put(TARGET_UI_RESOURCE_PATH, TARGET_UI_FILE_PATH);
        mappings.put(REWARD_MANAGER_UI_RESOURCE_PATH, REWARD_MANAGER_UI_FILE_PATH);
        mappings.put(REWARD_EDITOR_UI_RESOURCE_PATH, REWARD_EDITOR_UI_FILE_PATH);
        mappings.put(REWARD_HISTORY_UI_RESOURCE_PATH, REWARD_HISTORY_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return (configuration != null && configuration.overwriteUiFile())
            || (targetConfiguration != null && targetConfiguration.overwriteUiFile());
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXEntityTracker.yml 配置文件缺失");
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        moduleYaml = yaml;
        ConfigurationSection bossSection = yaml.getConfigurationSection("boss");
        String bossesDirRelative = bossSection != null
            ? bossSection.getString("bosses-directory", "bosses") : "bosses";
        File bossesDirectory = new File(dataFolder, bossesDirRelative);
        ensureBossDefaults(bossesDirRelative);
        configuration = PluginConfiguration.from(bossSection, bossesDirectory);
        targetConfiguration = EntityTargetHudConfiguration.load(yaml.getConfigurationSection("attack-target"));
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
    protected void startService() throws Exception {

        initializeRepositoryIfNeeded();
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
            if (resolvedBossUiIds.isEmpty()) {
                throw new IllegalStateException("EntityTracker Boss UI 注册失败");
            }
            bossRuntimeUiIds = List.copyOf(resolvedBossUiIds);

            ServerPlatform platform = ServerPlatform.detect(plugin.getServer());
            bossService = new BossTrackerService(
                plugin, logger, configuration, packetBridge, bossRuntimeUiIds,
                platform, null, () -> getCapability(MailDispatchable.class),
                itemSourceRegistry, attributeBridge,
                crossServerService, killRecordingService, placeholderResolver, messages()
            );
            bossService.start();
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
        if (!resolvedTargetUiIds.isEmpty()) {
            targetRuntimeUiIds = List.copyOf(resolvedTargetUiIds);
            PluginConfiguration bossConf = configuration;
            targetService = new EntityTargetHudService(
                plugin, logger, () -> bossConf, targetConfiguration, packetBridge, targetRuntimeUiIds
            );
            targetService.start();
        }

        // 初始化排行榜奖励系统
        initializeRewardSystem();

        // 注册标准 capability
        if (repository != null) {
            registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
                new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                    @Override public @NotNull String moduleId() { return "entitytracker"; }
                    @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                        try { return repository.deletePlayerData(playerUuid); }
                        catch (Exception e) { logger.warning("EntityTracker purge 失败: " + e.getMessage()); return -1; }
                    }
                    @Override public int purgeAllPlayerData() {
                        try { return repository.deleteAllPlayerData(); }
                        catch (Exception e) { logger.warning("EntityTracker purgeAll 失败: " + e.getMessage()); return -1; }
                    }
                });
            registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
                new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                    @Override public @NotNull String moduleId() { return "entitytracker"; }
                    @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                            @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                        return repository.migrateTo(target, overwrite);
                    }
                    @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                        return repository.getDescriptor();
                    }
                });
        }

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
        if (newFeaturesSettings.dropAllocation().enabled()) {
            dropAllocationService = new DropAllocationService(
                plugin, logger, newFeaturesSettings.dropAllocation(), ds, messages()
            );
        }
        boolean crossServerRanking = newFeaturesSettings.crossServerRanking().enabled()
            && crossServerChannelConfig.enabled();
        if (crossServerRanking) {
            crossServerService = new EntityTrackerCrossServerService(
                plugin, logger, crossServer, crossServerChannelConfig, ds
            );
            rankingCacheService = new CrossServerRankingCacheService(
                plugin, logger,
                newFeaturesSettings.crossServerRanking(),
                ds,
                () -> configuration,
                () -> crossServer.nodeId()
            );
            crossServerService.start();
            rankingCacheService.start();
        }
        if (newFeaturesSettings.dropRecording().enabled()
            || newFeaturesSettings.dropAllocation().enabled()
            || crossServerRanking) {
            killRecordingService = new BossKillRecordingService(
                plugin, logger,
                newFeaturesSettings,
                ds,
                dropAllocationService,
                crossServerService,
                rankingCacheService,
                () -> crossServer.nodeId()
            );
            killRecordingService.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
            killRecordingService.start();
            if (crossServerService != null) {
                crossServerService.attachKillRecording(killRecordingService);
                crossServerService.attachRankingCache(rankingCacheService);
            }
            killRecordingService.purgeOldRecords();
        }
    }

    @Override
    protected void stopService() {
        if (targetService != null) {
            targetService.shutdown();
            targetService = null;
        }
        if (bossService != null) {
            bossService.shutdown();
            bossService = null;
        }
        
        // 关闭排行榜奖励系统
        if (rewardScheduler != null) {
            rewardScheduler.shutdown();
            rewardScheduler = null;
        }
        rewardService = null;
        rewardCommand = null;

        if (killRecordingService != null) {
            killRecordingService.shutdown();
            killRecordingService = null;
        }
        if (rankingCacheService != null) {
            rankingCacheService.shutdown();
            rankingCacheService = null;
        }
        dropAllocationService = null;
        if (crossServerService != null) {
            crossServerService.shutdown();
            crossServerService = null;
        }
        if (repository != null) {
            repository.shutdown();
            repository = null;
        }
        moduleYaml = null;
        configuration = null;
        targetConfiguration = null;
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
            java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailSupplier =
                () -> getCapability(MailDispatchable.class);
            java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher =
                (signal, player) -> {}; // 待接入实际信号派发器
            RewardActionExecutor actionExecutor = new RewardActionExecutor(
                plugin, mailSupplier, signalDispatcher, itemSourceRegistry, placeholderResolver
            );

            // 创建服务实例
            rewardService = new RankingRewardService(
                ds, actionExecutor, plugin, logger, () -> configuration
            );
            rewardScheduler = new RankingRewardScheduler(rewardService, plugin, logger);
            rewardCommand = new RankingRewardCommand(rewardService, rewardScheduler, plugin, logger, messages());

            // 初始化调度器（传入主配置）
            rewardScheduler.initialize(moduleYaml);

            logger.info("排行榜奖励系统初始化完成");

        } catch (Exception e) {
            logger.severe("排行榜奖励系统初始化失败: " + e.getMessage());
            // 不抛出异常，允许模块继续运行其他功能
        }
    }

    private boolean hasRankingRewardsSection() {
        return moduleYaml != null
            && moduleYaml.getConfigurationSection("new-features.ranking-rewards") != null;
    }

    private void initializeRepositoryIfNeeded() {
        if (!newFeaturesSettings.needsDatabase() && !hasRankingRewardsSection()) {
            return;
        }
        try {
            repository = new JdbcEntityTrackerRepository(
                dataFolder, moduleYaml, logger);
            repository.initialize();
        } catch (Exception e) {
            logger.severe("EntityTracker 数据库初始化失败: " + e.getMessage());
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
        return actions.isEmpty() ? List.of("help", "status", "reload") : actions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && "rewards".equalsIgnoreCase(args[0])) {
            // 奖励命令处理
            if (rewardCommand != null) {
                String[] rewardArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return rewardCommand.onCommand(sender, "rewards", rewardArgs);
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
        if (args.length == 1) {
            // 第一层补全：包含 rewards 选项
            List<String> completions = new java.util.ArrayList<>();
            if (adminCommand != null) {
                List<String> adminCompletions = adminCommand.onTabComplete(sender, args);
                if (adminCompletions != null) {
                    completions.addAll(adminCompletions);
                }
            }
            if (rewardCommand != null && "rewards".startsWith(args[0].toLowerCase())) {
                completions.add("rewards");
            }
            return completions;
        }
        
        if (args.length > 1 && "rewards".equalsIgnoreCase(args[0])) {
            // 奖励命令补全
            if (rewardCommand != null) {
                String[] rewardArgs = Arrays.copyOfRange(args, 1, args.length);
                return rewardCommand.onTabComplete(sender, rewardArgs);
            }
            return List.of("manage", "history", "distribute", "status");
        }
        
        // 原有命令补全
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}




