package xuanmo.arcartxsuite.onlinerewards;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.OnlineRewardsQueryable;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.onlinerewards.command.OnlineRewardsAdminCommand;
import xuanmo.arcartxsuite.onlinerewards.command.OnlineRewardsPlayerCommand;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.placeholder.OnlineRewardsPlaceholderExpansion;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.storage.JdbcOnlineRewardsRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;

/**
 * 在线奖励模块主类。
 * <p>
 * 属于 {@code onlinerewards} 模块，继承 {@link AbstractAXSModule}，负责配置加载、服务启停、
 * 命令绑定、PAPI 占位符、客户端包处理与初始化回调，并注册 {@code OnlineRewardsQueryable}、
 * {@code PlayerDataPurgeable}、{@code DatabaseMigratable} 等跨模块能力。
 */
public final class OnlineRewardsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private OnlineRewardsAdminCommand adminCommand;

    private OnlineRewardsModuleConfiguration configuration;
    private OnlineRewardsService service;
    private JdbcOnlineRewardsRepository repository;
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("onlinerewards")
            .name("OnlineRewards")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .externalDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXOnlineRewards.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("weekly-rewards")
                    .dynamicSection("monthly-rewards")
                    .dynamicSection("server-sign-in-goal.targets")
                .build())
            .currentVersion(4)
            .validations(List.of(
                    // storage.shared 必须是布尔值
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // pool-size 范围 1-100（仅在 shared: false 时生效）
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    // 离线储蓄上限
                    ValidationRule.of("offline-savings.max-minutes", ValueType.INT)
                        .withRange(0, 1440),
                    // 离线储蓄存储率
                    ValidationRule.of("offline-savings.storage-rate", ValueType.DOUBLE)
                        .withRange(0.0, 1.0)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of(OnlineRewardsService.MENU_UI_RESOURCE_PATH, OnlineRewardsService.MENU_UI_FILE_PATH), configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data),
            0, configuration.ui().packetId(), "onlinerewards");
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXOnlineRewards.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String signInRelative = yaml.getString("sign-in-file", "sign-in.yml");
        String rewardsRelative = yaml.getString("rewards-file", "rewards.yml");
        File signInFile = new File(dataFolder, signInRelative);
        File rewardsFile = new File(dataFolder, rewardsRelative);
        ensureOnlineRewardsDefaults(signInFile, rewardsFile);
        configuration = OnlineRewardsModuleConfiguration.load(yaml, signInFile, rewardsFile);
    }

    private void ensureOnlineRewardsDefaults(File signInFile, File rewardsFile) {
        if (!signInFile.exists()) {
            signInFile.getParentFile().mkdirs();
            exportResource("sign-in.yml", signInFile, false);
        }
        if (!rewardsFile.exists()) {
            rewardsFile.getParentFile().mkdirs();
            exportResource("rewards.yml", rewardsFile, false);
        }
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.onlinerewards.server_goal_reached", "axs.onlinerewards.signin_completed");
    }

    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {

        if (clientBridge == null || !clientBridge.isAvailable()) {
            throw new IllegalStateException("OnlineRewards 模块需要 ArcartX 客户端桥接");
        }

        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（dialect=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor orDesc;
        javax.sql.DataSource orDs;
        if (configuration.storage().hasOverride()) {
            orDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("onlinerewards", null, dataFolder, orDesc);
            orDs = storageScope.dataSource();
        } else {
            orDesc = storageManager.getDescriptor().withTablePrefix("");
            storageScope = storageManager.openModuleStorage("onlinerewards",
                configuration.storage().sqliteFileName(), dataFolder, null);
            orDs = storageScope.dataSource();
        }
        repository = new JdbcOnlineRewardsRepository(orDs, orDesc, dataFolder, logger);
        return storageScope.initialize(repository::initialize);
    }

    @Override
    protected void startService() throws Exception {
        var rewardsRepo = repository;
        service = new OnlineRewardsService(
            plugin, logger, configuration,
            rewardsRepo,
            clientBridge, packetBridge, packetGuard,
            () -> getCapability(SignalDispatchable.class),
            () -> getCapability(ChatCardSendable.class),
            () -> getCapability(TitleGrantable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(QQBotBroadcastable.class),
            () -> getCapability(EventBusCapability.class),
            messages(),
            overwrite -> {
                try {
                    return exportUiResource(
                        OnlineRewardsService.MENU_UI_RESOURCE_PATH,
                        OnlineRewardsService.MENU_UI_FILE_PATH,
                        overwrite, moduleClassLoader());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            },
            crossServer
        );
        service.setItemSourceRegistry(itemSourceRegistry);
        service.setDurableMail(() -> getCapability(AxsMailService.class), rewardsRepo.getDescriptor().isMysql());
        service.setScheduler(scheduler);
        service.setIconResolver(new xuanmo.arcartxsuite.api.icon.IconResolver(itemStackBridge, itemSourceRegistry));
        service.start();
        // 将 Service 手动注册的 UI 纳入基类生命周期管理（onDisable 时兜底注销）
        for (String uiId : service.registeredUiIds()) {
            recordExternalUi(uiId, uiId);
        }

        OnlineRewardsService registeredService = service;
        registerCapability(OnlineRewardsQueryable.class, new OnlineRewardsQueryable() {
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> hasSignedToday(@NotNull java.util.UUID playerUuid) {
                return registeredService.hasSignedTodayPublic(playerUuid);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Integer> todayOnlineMinutes(@NotNull java.util.UUID playerUuid) {
                return registeredService.todayOnlineMinutesPublic(playerUuid);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Integer> weeklyOnlineMinutes(@NotNull java.util.UUID playerUuid) {
                return registeredService.weeklyOnlineMinutesPublic(playerUuid);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Integer> monthlyOnlineMinutes(@NotNull java.util.UUID playerUuid) {
                return registeredService.monthlyOnlineMinutesPublic(playerUuid);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> signIn(@NotNull Player player) {
                return registeredService.signIn(player).thenApply(xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsOperationResult::success);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> makeupSignIn(@NotNull Player player, @NotNull String date) {
                return registeredService.makeupSignIn(player, date).thenApply(xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsOperationResult::success);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> addMakeupCards(@NotNull Player player, int amount) {
                return registeredService.addMakeupCardsPublic(player, amount);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> grantDailyRewards(@NotNull Player player) {
                return registeredService.grantDailyRewardsPublic(player);
            }
            @Override public @NotNull java.util.concurrent.CompletionStage<Boolean> addOnlineMinutes(@NotNull Player player, int minutes) {
                return registeredService.addOnlineMinutesPublic(player, minutes);
            }
        });
        adminCommand = new OnlineRewardsAdminCommand(() -> service, messages());

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "onlinerewards"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    if (registeredService.isStopping()) return -1;
                    try { return rewardsRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("OnlineRewards purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    if (registeredService.isStopping()) return -1;
                    try { return rewardsRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("OnlineRewards purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "onlinerewards"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    if (registeredService.isStopping()) {
                        var result = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
                        result.addError("OnlineRewards 正在停用");
                        return result;
                    }
                    return rewardsRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return rewardsRepo.getDescriptor();
                }
            });

        logger.fine(
            "OnlineRewards 模块已载入，rewards=" + configuration.rewards().size()
                + " | storage=" + configuration.storage().mode()
                + " | menu-ui=" + service.runtimeMenuUiId()
        );
    }

    @Override
    protected void stopService() {
        var current = service;
        var scope = storageScope;
        service = null;
        repository = null;
        storageScope = null;
        try {
            if (current != null) current.shutdown();
        } finally {
            var stopped = current == null ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                : current.databaseStopped();
            trackShutdown(stopped);
            if (scope != null) {
                scope.retireAfter(stopped);
                trackShutdown(scope.retired());
            }
            configuration = null;
        }
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        OnlineRewardsPlayerCommand cmd = new OnlineRewardsPlayerCommand(() -> service, () -> configuration, messages());
        return Map.of(
            "onlinerewards", (TabExecutor) cmd,
            "signin", (TabExecutor) cmd
        );
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new OnlineRewardsPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) {
                service.handleClientInitialized(player);
            }
        };
    }

    /**
     * 返回当前在线奖励服务实例。
     *
     * @return 服务实例，模块未启动时为 {@code null}
     */
    public OnlineRewardsService getService() {
        return service;
    }

    /**
     * 返回当前已加载的模块配置。
     *
     * @return 模块配置，未加载时为 {@code null}
     */
    public OnlineRewardsModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "onlinerewards"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

