package xuanmo.arcartxsuite.battlepass;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.battlepass.command.BattlePassAdminCommand;
import xuanmo.arcartxsuite.battlepass.command.BattlePassPlayerCommand;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.packet.BattlePassPacketHandler;
import xuanmo.arcartxsuite.battlepass.placeholder.BattlePassPlaceholderExpansion;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;
import xuanmo.arcartxsuite.battlepass.storage.JdbcBattlePassRepository;

/**
 * BattlePass（战令）模块入口，继承 {@link AbstractAXSModule} 并实现 {@link ModuleCommandHandler}。
 * <p>
 * 负责战令模块的完整生命周期管理：配置加载、仓储初始化、UI 注册、服务启动、
 * 跨模块能力注册（数据库迁移、玩家数据清理）、命令绑定、PlaceholderAPI 扩展及客户端包处理。
 */
public final class BattlePassModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String MODULE_VERSION = "1.3.2";

    private static final String MAIN_UI_RESOURCE_PATH = "arcartx/ui/battlepass_main.yml";
    private static final String MAIN_UI_FILE_PATH = "ui/battlepass_main.yml";
    private static final String TASKS_UI_RESOURCE_PATH = "arcartx/ui/battlepass_tasks.yml";
    private static final String TASKS_UI_FILE_PATH = "ui/battlepass_tasks.yml";
    private static final String REWARDS_UI_RESOURCE_PATH = "arcartx/ui/battlepass_rewards.yml";
    private static final String REWARDS_UI_FILE_PATH = "ui/battlepass_rewards.yml";
    private static final String SETTLEMENT_UI_RESOURCE_PATH = "arcartx/ui/battlepass_settlement.yml";
    private static final String SETTLEMENT_UI_FILE_PATH = "ui/battlepass_settlement.yml";

    private BattlePassModuleConfiguration configuration;
    private BattlePassService service;
    private JdbcBattlePassRepository repository;
    private BattlePassAdminCommand adminCommand;
    private BattlePassPacketHandler packetHandler;

    /**
     * {@inheritDoc}
     * <p>
     * 返回 BattlePass 模块描述符（id=battlepass）。
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("battlepass")
            .name("BattlePass")
            .version(MODULE_VERSION)
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected int currentConfigVersion() {
        return 3;
    }

    @Override
    protected String configFileName() {
        return "ArcartXBattlePass.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("tasks")
            .dynamicSection("messages")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.required("season.season-id", ValueType.STRING),
            ValidationRule.required("season.max-level", ValueType.INT)
                .withRange(1, 10000)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MAIN_UI_RESOURCE_PATH, MAIN_UI_FILE_PATH);
        mappings.put(TASKS_UI_RESOURCE_PATH, TASKS_UI_FILE_PATH);
        mappings.put(REWARDS_UI_RESOURCE_PATH, REWARDS_UI_FILE_PATH);
        mappings.put(SETTLEMENT_UI_RESOURCE_PATH, SETTLEMENT_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXBattlePass.yml 配置文件缺失");
        }
        File dataFolder = configFile.getParentFile();
        // 首次安装时导出默认任务文件
        ensureTaskDefaults(dataFolder);
        // 首次安装时导出默认奖励文件
        ensureRewardDefaults(dataFolder);
        configuration = BattlePassModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), dataFolder, logger, ariaBridge);
    }

    private void ensureTaskDefaults(File dataFolder) {
        File tasksDir = new File(dataFolder, "tasks");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        for (String taskFile : new String[]{"daily.yml", "weekly.yml", "season.yml"}) {
            File target = new File(tasksDir, taskFile);
            if (!target.exists()) {
                exportResource("tasks/" + taskFile, target, false);
            }
        }
    }

    private void ensureRewardDefaults(File dataFolder) {
        // 先从主配置读取 rewards-file 字段（默认 rewards.yml），导出默认奖励文件
        File target = new File(dataFolder, "rewards.yml");
        if (!target.exists()) {
            exportResource("rewards.yml", target, false);
        }
        // 导出默认赛季结算奖励文件
        File seasonRewardsTarget = new File(dataFolder, "season-rewards.yml");
        if (!seasonRewardsTarget.exists()) {
            exportResource("season-rewards.yml", seasonRewardsTarget, false);
        }
    }

    @Override
    protected void startService() throws Exception {
        JdbcBattlePassRepository repo;
        if (configuration.storage().hasOverride()) {
            // 旧路径：模块自建连接池
            repo = new JdbcBattlePassRepository(dataFolder, configuration.storage(), logger);
        } else {
            // 新路径：共享本体数据源
            repo = new JdbcBattlePassRepository(storageManager, "battlepass",
                configuration.storage().sqliteFileName(), logger);
        }
        repo.initialize();
        repository = repo;

        UiBinding mainBinding = registerModuleUi(MAIN_UI_FILE_PATH, configuration.ui().mainId(), true);
        UiBinding tasksBinding = registerModuleUi(TASKS_UI_FILE_PATH, configuration.ui().tasksId(), true);
        UiBinding rewardsBinding = registerModuleUi(REWARDS_UI_FILE_PATH, configuration.ui().rewardsId(), true);
        UiBinding settlementBinding = registerModuleUi(SETTLEMENT_UI_FILE_PATH, configuration.ui().settlementId(), true);
        if (mainBinding.registeredUiId() == null || tasksBinding.registeredUiId() == null
            || rewardsBinding.registeredUiId() == null || settlementBinding.registeredUiId() == null) {
            throw new IllegalStateException("BattlePass UI 注册失败");
        }

        service = new BattlePassService(plugin, configuration, repo, logger,
            itemSourceRegistry, currencyManager,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class));
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start();

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("BattlePass purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("BattlePass purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new BattlePassAdminCommand(() -> service, this::msg, MODULE_VERSION);
        logger.fine("BattlePass 模块已载入 | 赛季=" + configuration.season().seasonId()
            + " | 跨服=" + (configuration.crossServer().enabled() ? "ON" : "OFF"));
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            try {
                repository.shutdown();
            } catch (Exception e) {
                logger.warning("BattlePass 仓储关闭失败: " + e.getMessage());
            }
            repository = null;
        }
        packetHandler = null;
        adminCommand = null;
        configuration = null;
    }

    @Override
    protected @NotNull List<org.bukkit.event.Listener> createListeners() {
        return List.of(new xuanmo.arcartxsuite.battlepass.listener.BattlePassPlayerListener(
            plugin, () -> service, () -> packetHandler));
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        BattlePassPlayerCommand cmd = new BattlePassPlayerCommand(
            () -> packetHandler, () -> service, messages());
        Map<String, TabExecutor> map = new LinkedHashMap<>();
        map.put("bp", cmd);
        map.put("battlepass", cmd);
        return map;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new BattlePassPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        if (service == null) return null;
        String mainUiId = getModuleUiId(MAIN_UI_FILE_PATH);
        String tasksUiId = getModuleUiId(TASKS_UI_FILE_PATH);
        String rewardsUiId = getModuleUiId(REWARDS_UI_FILE_PATH);
        String settlementUiId = getModuleUiId(SETTLEMENT_UI_FILE_PATH);
        packetHandler = new BattlePassPacketHandler(
            plugin,
            packetBridge,
            packetGuard,
            service,
            mainUiId != null ? mainUiId : configuration.ui().mainId(),
            tasksUiId != null ? tasksUiId : configuration.ui().tasksId(),
            rewardsUiId != null ? rewardsUiId : configuration.ui().rewardsId(),
            settlementUiId != null ? settlementUiId : configuration.ui().settlementId(),
            itemSourceRegistry,
            itemStackBridge,
            this::msg
        );
        service.setUiRefreshListener(packetHandler::onServiceRefresh);
        return packetHandler;
    }

    // ─── ModuleCommandHandler（/axs battlepass）───────────────────

    @Override public String commandId() { return "battlepass"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    // ─── 辅助方法 ────────────────────────────────────────────

    private String msg(String key, Object[] args) {
        MessageProvider mp = messages();
        if (mp == null) return key;
        String prefix = mp.get("prefix");
        return prefix + mp.get(key, args);
    }
}




