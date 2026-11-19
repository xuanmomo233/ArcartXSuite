package xuanmo.arcartxsuite.afkreward;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.afkreward.command.AfkRewardAdminCommand;
import xuanmo.arcartxsuite.afkreward.command.AfkRewardPlayerCommand;
import xuanmo.arcartxsuite.api.capability.AfkRewardDispatchable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.afkreward.config.AreaConfiguration;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.listener.AfkRewardListener;
import xuanmo.arcartxsuite.afkreward.placeholder.AfkRewardPlaceholderExpansion;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;

/**
 * AFK 挂机奖励模块主类。
 * <p>
 * 关联模块：afkreward。负责模块生命周期管理：加载配置与区域、初始化仓储与服务、
 * 注册事件监听器、玩家命令、管理命令与 PlaceholderAPI 变量，
 * 并向宿主暴露 {@link xuanmo.arcartxsuite.api.capability.AfkRewardDispatchable} 能力。
 */
public final class AfkRewardModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String HUD_UI_RESOURCE_PATH = "arcartx/ui/afk_reward_hud.yml";
    private static final String HUD_UI_FILE_PATH = "ui/afk_reward_hud.yml";

    private AfkRewardConfiguration configuration;
    private AfkRewardRepository repository;
    private AfkRewardService service;
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;
    private AfkRewardListener listener;
    private AfkRewardAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("afkreward")
            .name("AfkReward")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .externalDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXAfkReward.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder().build())
            .currentVersion(9)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.of("manual.leaderboard-size", ValueType.INT)
                        .withRange(1, 100),
                    ValidationRule.of("anti-abuse.time-limit.on-exceed", ValueType.STRING)
                        .withEnum(java.util.Set.of("STOP", "KICK", "DECAY")),
                    ValidationRule.of("anti-abuse.bot-detection.action", ValueType.STRING)
                        .withEnum(java.util.Set.of("NO_REWARD", "KICK")),
                    ValidationRule.of("anti-abuse.bot-detection.window-seconds", ValueType.INT)
                        .withRange(1, 3600),
                    ValidationRule.of("anti-abuse.bot-detection.min-view-changes", ValueType.INT)
                        .withRange(0, 1000),
                    ValidationRule.of("manual.permission-recheck-seconds", ValueType.INT)
                        .withRange(1, 3600)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of(HUD_UI_RESOURCE_PATH, HUD_UI_FILE_PATH), configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXAfkReward.yml 配置文件缺失");
        }
        configuration = AfkRewardConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), logger
        );

        // 区域目录应位于模块 data 目录下，而非插件根目录
        String effectiveAreasDir = configuration.areasDirectory();

        // 导出默认区域文件（若目录为空）
        AreaConfiguration.exportDefaultArea(moduleClassLoader(),
            dataFolder, effectiveAreasDir);
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.afkreward.reward_claimed");
    }

    @Override
    protected @NotNull ClientInitializedHandler createInitializedHandler() {
        return player -> {
            AfkRewardService current = service;
            if (current != null) current.onClientInitialized(player);
        };
    }

    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {
        // 加载区域独立配置文件
        String effectiveAreasDir = configuration.areasDirectory();
        java.util.Map<String, xuanmo.arcartxsuite.afkreward.model.AfkArea> loadedAreas =
            AreaConfiguration.loadAreas(dataFolder, effectiveAreasDir, logger);
        configuration = configuration.withAreas(loadedAreas);

        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor afkDesc;
        javax.sql.DataSource afkDs;
        if (configuration.storage().hasOverride()) {
            afkDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("afkreward", null, dataFolder, afkDesc);
            afkDs = storageScope.dataSource();
        } else {
            afkDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            storageScope = storageManager.openModuleStorage("afkreward",
                configuration.storage().sqliteFileName(), dataFolder, null);
            afkDs = storageScope.dataSource();
        }
        repository = new AfkRewardRepository(afkDs, afkDesc, dataFolder, logger);
        return storageScope.initialize(repository::initialize);
    }

    @Override
    protected void startService() throws Exception {
        service = new AfkRewardService(
            plugin, configuration, repository, logger, messages(),
            () -> getCapability(MailDispatchable.class),
            () -> getCapability(SignalDispatchable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(EssentialsQueryable.class)
        );
        service.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setItemSourceRegistry(itemSourceRegistry);
        service.setPendingRewardService(pendingRewardService);
        UiBinding hudBinding = null;
        if (configuration.ui().registerUiOnEnable()) {
            hudBinding = registerModuleUi(HUD_UI_FILE_PATH, configuration.ui().uiId(), true);
        }
        if (hudBinding != null) {
            service.setHudBridge(packetBridge, hudBinding.runtimeUiId());
        }
        service.start();

        listener = new AfkRewardListener(() -> service);
        adminCommand = new AfkRewardAdminCommand(() -> service, messages());

        // UI 绑定
        AfkRewardService svc = service;
        AfkRewardRepository repo = repository;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "afkreward"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return svc.callApi(() -> {
                        try { return repo.deletePlayerData(playerUuid); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                    }, -1); }
                    catch (Exception e) { logger.warning("AfkReward purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return svc.callApi(() -> {
                        try { return repo.deleteAllPlayerData(); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                    }, -1); }
                    catch (Exception e) { logger.warning("AfkReward purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        // 注册 AfkRewardDispatchable capability
        registerCapability(AfkRewardDispatchable.class, new AfkRewardDispatchable() {
            @Override public boolean isAfk(@NotNull java.util.UUID playerUuid) {
                return svc != null && svc.isInManualAfk(playerUuid);
            }
            @Override public @Nullable String getAreaName(@NotNull java.util.UUID playerUuid) {
                if (svc == null) return null;
                var st = svc.getState(playerUuid);
                return st != null ? st.areaName : null;
            }
            @Override public int getAfkSeconds(@NotNull java.util.UUID playerUuid) {
                if (svc == null) return 0;
                var st = svc.getState(playerUuid);
                return st != null ? st.seconds : 0;
            }
            @Override public @Nullable String getAfkMode(@NotNull java.util.UUID playerUuid) {
                if (svc == null) return null;
                var st = svc.getState(playerUuid);
                return st != null ? st.mode.name() : null;
            }
            @Override public boolean startManualAfk(@NotNull org.bukkit.entity.Player player, @NotNull String areaName) {
                return svc.callApi(() -> svc.startManualAfk(player, areaName), false);
            }
        });

        logger.info(
            ChatColor.GOLD + "AfkReward 模块已启动 (区域=" + configuration.areas().size()
                + ")"
        );
    }

    @Override
    protected void stopService() {
        var current = service;
        var scope = storageScope;
        service = null;
        repository = null;
        storageScope = null;
        try { if (current != null) current.shutdown(); }
        finally {
            var stopped = current == null ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                : current.databaseStopped();
            trackShutdown(stopped);
            if (scope != null) { scope.retireAfter(stopped); trackShutdown(scope.retired()); }
            listener = null;
            adminCommand = null;
            configuration = null;
        }
    }

    @Override
    protected @NotNull java.util.List<org.bukkit.event.Listener> createListeners() {
        return listener != null ? List.of(listener) : List.of();
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of(
            "afkreward", new AfkRewardPlayerCommand(() -> service, messages()),
            "afk", new AfkRewardPlayerCommand(() -> service, messages())
        );
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new AfkRewardPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    public String commandId() { return "afkreward"; }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull org.bukkit.command.CommandSender sender,
                             @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull org.bukkit.command.CommandSender sender,
                                                @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    public AfkRewardService getService() {
        return service;
    }

}
