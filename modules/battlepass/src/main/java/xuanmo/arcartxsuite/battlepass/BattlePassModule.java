package xuanmo.arcartxsuite.battlepass;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
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

    private static final String MODULE_VERSION = "2.0.0";

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
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;
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
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXBattlePass.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.strict())
            .currentVersion(6)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.required("season.season-id", ValueType.STRING),
                    ValidationRule.required("season.max-level", ValueType.INT)
                        .withRange(1, 10000)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MAIN_UI_RESOURCE_PATH, MAIN_UI_FILE_PATH);
        mappings.put(TASKS_UI_RESOURCE_PATH, TASKS_UI_FILE_PATH);
        mappings.put(REWARDS_UI_RESOURCE_PATH, REWARDS_UI_FILE_PATH);
        mappings.put(SETTLEMENT_UI_RESOURCE_PATH, SETTLEMENT_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        if (service == null) return PacketHandlerSpec.NONE;
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
        packetHandler.setNavigationProviders(
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MapNavigable.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class));
        service.setUiRefreshListener(packetHandler::onServiceRefresh);
        return PacketHandlerSpec.of(packetHandler, 0, BattlePassPacketHandler.PACKET_ID, "battlepass");
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXBattlePass.yml 配置文件缺失");
        }
        File dataFolder = configFile.getParentFile();
        // 首次安装时导出默认任务文件
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        ensureTaskDefaults(dataFolder, yaml.getString("tasks.tasks-directory", "tasks"));
        // 首次安装时导出默认奖励文件
        ensureRewardDefaults(dataFolder);
        configuration = BattlePassModuleConfiguration.load(
            yaml, dataFolder, logger, ariaBridge);
    }

    private void ensureTaskDefaults(File dataFolder, String configuredDirectory) {
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
        File integrationTasks = new File(dataFolder, configuredDirectory + "/integrations.yml");
        if (!integrationTasks.exists()) exportResource("tasks/integrations.yml", integrationTasks, false);
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
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {
        xuanmo.arcartxsuite.api.storage.StorageDescriptor bpDesc;
        javax.sql.DataSource bpDs;
        if (configuration.storage().hasOverride()) {
            bpDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("battlepass", null, dataFolder, bpDesc);
            bpDs = storageScope.dataSource();
        } else {
            bpDesc = storageManager.getDescriptor().withTablePrefix("");
            storageScope = storageManager.openModuleStorage("battlepass",
                configuration.storage().sqliteFileName(), dataFolder, null);
            bpDs = storageScope.dataSource();
        }
        repository = new JdbcBattlePassRepository(bpDs, bpDesc, dataFolder, logger);
        return storageScope.initialize(repository::initialize);
    }

    @Override
    protected void startService() throws Exception {
        var repo = repository;
        boolean registerUi = configuration.ui().registerOnEnable();
        UiBinding mainBinding = registerModuleUi(MAIN_UI_FILE_PATH, configuration.ui().mainId(), registerUi);
        UiBinding tasksBinding = registerModuleUi(TASKS_UI_FILE_PATH, configuration.ui().tasksId(), registerUi);
        UiBinding rewardsBinding = registerModuleUi(REWARDS_UI_FILE_PATH, configuration.ui().rewardsId(), registerUi);
        UiBinding settlementBinding = registerModuleUi(SETTLEMENT_UI_FILE_PATH, configuration.ui().settlementId(), registerUi);
        if (registerUi && (mainBinding.registeredUiId() == null || tasksBinding.registeredUiId() == null
            || rewardsBinding.registeredUiId() == null || settlementBinding.registeredUiId() == null)) {
            throw new IllegalStateException("BattlePass UI 注册失败");
        }

        service = new BattlePassService(plugin, configuration, repo, logger, pendingRewardService);
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setAbyssResultProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess.class),
            crossServer == null ? "local" : crossServer.nodeId());
        service.start();
        BattlePassService svc = service;

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                var unavailable = new xuanmo.arcartxsuite.api.storage.MigrationResult(false,0,0);
                unavailable.addError("BattlePass 正在停用");
                return svc.callApi(() -> repo.migrateData(target, overwrite),unavailable);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return svc.callApi(() -> {
                    try { return repo.deletePlayerData(playerUuid); }
                    catch(Exception failure) { throw new IllegalStateException(failure); }
                },-1); }
                catch (Exception e) { logger.warning("BattlePass purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return svc.callApi(() -> {
                    try { return repo.deleteAllPlayerData(); }
                    catch(Exception failure) { throw new IllegalStateException(failure); }
                },-1); }
                catch (Exception e) { logger.warning("BattlePass purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        registerCapability(xuanmo.arcartxsuite.api.capability.BattlePassAccess.class,
            new xuanmo.arcartxsuite.api.capability.BattlePassAccess() {
                @Override public boolean isSeasonActive() {
                    return svc.callApi(svc::isSeasonActive,false);
                }
                @Override public java.util.concurrent.CompletionStage<Integer> addXp(@NotNull org.bukkit.entity.Player player, int xp) {
                    return svc.addXpAsync(player,xp);
                }
                @Override public java.util.concurrent.CompletionStage<Void> setXp(@NotNull org.bukkit.entity.Player player, int xp) {
                    return svc.setXpAsync(player,xp);
                }
                @Override public java.util.concurrent.CompletionStage<Void> removeXp(@NotNull org.bukkit.entity.Player player, int xp) {
                    return svc.removeXpAsync(player,xp);
                }
                @Override public java.util.concurrent.CompletionStage<Void> setLevel(@NotNull org.bukkit.entity.Player player, int level) {
                    return svc.setLevelAsync(player,level);
                }
                @Override public java.util.concurrent.CompletionStage<Boolean> unlockTier(@NotNull org.bukkit.entity.Player player,
                        @NotNull String tier) {
                    try {
                        return svc.unlockTierAsync(player,
                            xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress.PassTier
                                .valueOf(tier.toUpperCase(java.util.Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return java.util.concurrent.CompletableFuture.failedFuture(e);
                    }
                }
                @Override public java.util.concurrent.CompletionStage<Integer> claimAllRewards(@NotNull org.bukkit.entity.Player player) {
                    return svc.claimAllRewardsAsync(player);
                }
                @Override public java.util.concurrent.CompletionStage<Void> updateTaskProgress(@NotNull org.bukkit.entity.Player player,
                        @NotNull String taskId, int increment) {
                    return svc.updateTaskProgressAsync(player, taskId, increment);
                }
                @Override public java.util.concurrent.CompletionStage<Void> resetProgress(@NotNull java.util.UUID playerUuid) {
                    return svc.resetProgressAsync(playerUuid);
                }
            });

        adminCommand = new BattlePassAdminCommand(plugin, () -> service, this::msg, MODULE_VERSION);
        logger.fine("BattlePass 模块已载入 | 赛季=" + configuration.season().seasonId());
    }

    @Override
    protected void stopService() {
        var current=service;var scope=storageScope;
        service=null;repository=null;storageScope=null;
        try {
            if(current!=null) current.shutdown();
        }finally {
            var stopped=current==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):current.databaseStopped();
            trackShutdown(stopped);
            if(scope!=null){scope.retireAfter(stopped);trackShutdown(scope.retired());}
            packetHandler=null;adminCommand=null;configuration=null;
        }
    }

    @Override
    protected @NotNull List<org.bukkit.event.Listener> createListeners() {
        return List.of(new xuanmo.arcartxsuite.battlepass.listener.BattlePassPlayerListener(
            plugin, () -> service, () -> packetHandler));
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        BattlePassPlayerCommand cmd = new BattlePassPlayerCommand(
            plugin, () -> packetHandler, () -> service, messages());
        Map<String, TabExecutor> map = new LinkedHashMap<>();
        map.put("bp", cmd);
        map.put("battlepass", cmd);
        return map;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new BattlePassPlaceholderExpansion(plugin, () -> service);
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

