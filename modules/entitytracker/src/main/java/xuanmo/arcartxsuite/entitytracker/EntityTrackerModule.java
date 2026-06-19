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
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
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
    private javax.sql.DataSource moduleDataSource;
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
        File bossesDirectory = new File(context.dataFolder(), bossesDirRelative);
        ensureBossDefaults(bossesDirRelative);
        configuration = PluginConfiguration.from(bossSection, bossesDirectory);
        targetConfiguration = EntityTargetHudConfiguration.load(yaml.getConfigurationSection("attack-target"));
        crossServerChannelConfig = CrossServerChannelConfigs.fromSection(yaml.getConfigurationSection("cross-server"));
        newFeaturesSettings = EntityTrackerNewFeaturesSettings.load(yaml.getConfigurationSection("new-features"));
    }

    private void ensureBossDefaults(String bossesRelative) {
        File bossesDir = new File(context.dataFolder(), bossesRelative);
        if (!bossesDir.exists()) {
            bossesDir.mkdirs();
        }
        File[] existing = bossesDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        File target = new File(bossesDir, "ExampleBoss.yml");
        if (!target.exists()) {
            context.exportResource("bosses/ExampleBoss.yml", target, false);
        }
    }

    @Override
    protected void startService() throws Exception {
        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();

        moduleDataSource = initializeModuleDatabaseIfNeeded();
        initializePersistenceServices();

        // Boss tracker depends on MythicMobs/MythicBukkit. If it is absent, keep the module alive
        // and only run the generic attack-target HUD below.
        boolean mythicAvailable = context.hasPlugin("MythicMobs") || context.hasPlugin("MythicBukkit");
        if (mythicAvailable) {
            List<String> bossUiIdCandidates = configuration.uiIds();
            List<String> resolvedBossUiIds = new java.util.ArrayList<>();
            for (String candidateUiId : bossUiIdCandidates) {
                File bossUiFile = new File(context.pluginDataFolder(), BOSS_UI_FILE_PATH);
                UiBinding bossBinding = context.prepareUiBinding(
                    "EntityTracker Boss", candidateUiId,
                    configuration.registerUiOnEnable(), bossUiFile
                );
                if (bossBinding != null) {
                    recordUiBinding(BOSS_UI_FILE_PATH + "#" + candidateUiId, bossBinding);
                    resolvedBossUiIds.add(bossBinding.runtimeUiId());
                }
            }
            if (resolvedBossUiIds.isEmpty()) {
                throw new IllegalStateException("EntityTracker Boss UI 注册失败");
            }
            bossRuntimeUiIds = List.copyOf(resolvedBossUiIds);

            ServerPlatform platform = ServerPlatform.detect(context.plugin().getServer());
            bossService = new BossTrackerService(
                context.plugin(), configuration, packetBridge, bossRuntimeUiIds,
                platform, null, () -> context.getCapability(MailDispatchable.class),
                context.itemSourceRegistry(), context.attributeBridge(),
                crossServerService, killRecordingService
            );
            bossService.start();
            adminCommand = new EntityTrackerAdminCommand(() -> bossService, messages());
        } else {
            bossRuntimeUiIds = List.of();
            context.logger().warning("未检测到 MythicMobs/MythicBukkit，EntityTracker Boss 追踪已跳过，普通攻击目标 HUD 仍可使用。");
        }

        // Target HUD
        List<String> targetUiIdCandidates = targetConfiguration.uiIds();
        List<String> resolvedTargetUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : targetUiIdCandidates) {
            File targetUiFile = new File(context.pluginDataFolder(), TARGET_UI_FILE_PATH);
            UiBinding targetBinding = context.prepareUiBinding(
                "EntityTracker Target", candidateUiId,
                targetConfiguration.registerUiOnEnable(), targetUiFile
            );
            if (targetBinding != null) {
                recordUiBinding(TARGET_UI_FILE_PATH + "#" + candidateUiId, targetBinding);
                resolvedTargetUiIds.add(targetBinding.runtimeUiId());
            }
        }
        if (!resolvedTargetUiIds.isEmpty()) {
            targetRuntimeUiIds = List.copyOf(resolvedTargetUiIds);
            PluginConfiguration bossConf = configuration;
            targetService = new EntityTargetHudService(
                context.plugin(), () -> bossConf, targetConfiguration, packetBridge, targetRuntimeUiIds
            );
            targetService.start();
        }

        // 初始化排行榜奖励系统
        initializeRewardSystem();

        context.logger().fine(
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
        if (moduleDataSource == null) {
            return;
        }
        if (newFeaturesSettings.dropAllocation().enabled()) {
            dropAllocationService = new DropAllocationService(
                context.plugin(), newFeaturesSettings.dropAllocation(), moduleDataSource
            );
        }
        boolean crossServerRanking = newFeaturesSettings.crossServerRanking().enabled()
            && crossServerChannelConfig.enabled();
        if (crossServerRanking) {
            crossServerService = new EntityTrackerCrossServerService(
                context.plugin(), context.crossServer(), crossServerChannelConfig, moduleDataSource
            );
            rankingCacheService = new CrossServerRankingCacheService(
                context.plugin(),
                newFeaturesSettings.crossServerRanking(),
                moduleDataSource,
                () -> configuration,
                () -> context.crossServer().nodeId()
            );
            crossServerService.start();
            rankingCacheService.start();
        }
        if (newFeaturesSettings.dropRecording().enabled()
            || newFeaturesSettings.dropAllocation().enabled()
            || crossServerRanking) {
            killRecordingService = new BossKillRecordingService(
                context.plugin(),
                newFeaturesSettings,
                moduleDataSource,
                dropAllocationService,
                crossServerService,
                rankingCacheService,
                () -> context.crossServer().nodeId()
            );
            killRecordingService.setEventBusProvider(() -> context.getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
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
        moduleDataSource = null;
        moduleYaml = null;
        configuration = null;
        targetConfiguration = null;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new EntityTrackerPlaceholderExpansion(
            context.plugin(),
            () -> configuration,
            () -> bossService,
            () -> bossRuntimeUiIds.isEmpty() ? "" : bossRuntimeUiIds.get(0),
            () -> (ArcartXPacketBridge) context.packetBridge()
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
                context.logger().info("排行榜奖励系统：模块配置尚未加载，跳过初始化");
                return;
            }

            ConfigurationSection rewardSection = moduleYaml.getConfigurationSection("new-features.ranking-rewards");
            if (rewardSection == null) {
                context.logger().info("排行榜奖励系统未配置，跳过初始化");
                return;
            }

            if (moduleDataSource == null) {
                moduleDataSource = initializeDatabase(moduleYaml);
            }
            if (moduleDataSource == null) {
                context.logger().severe("排行榜奖励系统数据库初始化失败");
                return;
            }

            // 创建通用奖励执行器
            java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailSupplier =
                () -> context.getCapability(MailDispatchable.class);
            java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher =
                (signal, player) -> {}; // 待接入实际信号派发器
            RewardActionExecutor actionExecutor = new RewardActionExecutor(
                context.plugin(), mailSupplier, signalDispatcher, context.itemSourceRegistry()
            );

            // 创建服务实例
            rewardService = new RankingRewardService(
                moduleDataSource, actionExecutor, context.plugin(), () -> configuration
            );
            rewardScheduler = new RankingRewardScheduler(rewardService, context.plugin());
            rewardCommand = new RankingRewardCommand(rewardService, rewardScheduler, context.plugin(), messages());

            // 初始化调度器（传入主配置）
            rewardScheduler.initialize(moduleYaml);

            context.logger().info("排行榜奖励系统初始化完成");

        } catch (Exception e) {
            context.logger().severe("排行榜奖励系统初始化失败: " + e.getMessage());
            // 不抛出异常，允许模块继续运行其他功能
        }
    }

    private javax.sql.DataSource initializeModuleDatabaseIfNeeded() {
        if (!newFeaturesSettings.needsDatabase() && !hasRankingRewardsSection()) {
            return null;
        }
        try {
            FileConfiguration config = moduleYaml != null ? moduleYaml : new YamlConfiguration();
            return initializeDatabase(config);
        } catch (Exception exception) {
            context.logger().severe("EntityTracker 数据库初始化失败: " + exception.getMessage());
            return null;
        }
    }

    private boolean hasRankingRewardsSection() {
        return moduleYaml != null
            && moduleYaml.getConfigurationSection("new-features.ranking-rewards") != null;
    }

    private javax.sql.DataSource initializeSharedDatabaseIfNeeded() {
        return initializeModuleDatabaseIfNeeded();
    }

    /**
     * 初始化数据库，返回DataSource
     */
    private javax.sql.DataSource initializeDatabase(FileConfiguration config) {
        try {
            // 读取数据库配置
            ConfigurationSection dbSection = config.getConfigurationSection("database");
            String dbType = dbSection != null ? dbSection.getString("type", "sqlite") : "sqlite";
            
            // 创建SQLite数据源
            File dbFile = new File(context.dataFolder(), "entitytracker.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            
            org.sqlite.SQLiteDataSource sqliteDs = new org.sqlite.SQLiteDataSource();
            sqliteDs.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            
            // 执行初始化SQL
            try (java.sql.Connection conn = sqliteDs.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                
                // 逐条执行建表语句（SQLite不支持一次执行多条）
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("sql/init_tables.sql");
                if (is != null) {
                    String initSql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    // 按分号分割并逐条执行
                    String[] statements = initSql.split(";");
                    for (String sql : statements) {
                        // 去除前导注释行，保留实际 SQL
                        String[] lines = sql.split("\n");
                        StringBuilder sb = new StringBuilder();
                        for (String line : lines) {
                            String l = line.trim();
                            if (!l.isEmpty() && !l.startsWith("--")) {
                                sb.append(line).append('\n');
                            }
                        }
                        String trimmed = sb.toString().trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                stmt.execute(trimmed);
                            } catch (java.sql.SQLException e) {
                                // 忽略已存在的表错误
                                if (!e.getMessage().contains("already exists")) {
                                    context.logger().warning("SQL执行警告: " + e.getMessage());
                                }
                            }
                        }
                    }
                    is.close();
                }
                context.logger().info("数据库表初始化完成");
            }
            
            return sqliteDs;
            
        } catch (Exception e) {
            context.logger().severe("数据库初始化失败: " + e.getMessage());
            return null;
        }
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

