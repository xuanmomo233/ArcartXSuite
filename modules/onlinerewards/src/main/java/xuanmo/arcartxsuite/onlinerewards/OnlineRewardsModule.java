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
import java.util.Set;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.onlinerewards.capability.OnlineRewardsQueryable;
import xuanmo.arcartxsuite.onlinerewards.command.OnlineRewardsAdminCommand;
import xuanmo.arcartxsuite.onlinerewards.command.OnlineRewardsPlayerCommand;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.placeholder.OnlineRewardsPlaceholderExpansion;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.storage.JdbcOnlineRewardsRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class OnlineRewardsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private OnlineRewardsAdminCommand adminCommand;

    private OnlineRewardsModuleConfiguration configuration;
    private OnlineRewardsService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("onlinerewards")
            .name("OnlineRewards")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .externalDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXOnlineRewards.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected int currentConfigVersion() {
        return 2;
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("weekly-rewards")
            .dynamicSection("monthly-rewards")
            .dynamicSection("server-sign-in-goal.targets")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.mode 必须是 sqlite 或 mysql
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            // pool-size 范围 1-100
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            // 离线储蓄上限
            ValidationRule.of("offline-savings.max-minutes", ValueType.INT)
                .withRange(0, 1440),
            // 离线储蓄存储率
            ValidationRule.of("offline-savings.storage-rate", ValueType.DOUBLE)
                .withRange(0.0, 1.0)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(OnlineRewardsService.MENU_UI_RESOURCE_PATH, OnlineRewardsService.MENU_UI_FILE_PATH);
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
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
    protected void startService() throws Exception {

        if (clientBridge == null || !clientBridge.isAvailable()) {
            throw new IllegalStateException("OnlineRewards 模块需要 ArcartX 客户端桥接");
        }

        JdbcOnlineRewardsRepository rewardsRepo = new JdbcOnlineRewardsRepository(
            dataFolder,
            configuration.storage(), logger);
        service = new OnlineRewardsService(
            plugin, logger, configuration,
            rewardsRepo,
            clientBridge, packetBridge, packetGuard,
            () -> getCapability(MailDispatchable.class),
            () -> getCapability(SignalDispatchable.class),
            () -> getCapability(ChatCardSendable.class),
            () -> getCapability(TitleGrantable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(QQBotBroadcastable.class),
            () -> getCapability(EventBusCapability.class),
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
        service.start();

        registerCapability(OnlineRewardsQueryable.class, new OnlineRewardsQueryable() {
            @Override public boolean hasSignedToday(@NotNull java.util.UUID playerUuid) { return service.hasSignedTodayPublic(playerUuid); }
            @Override public int todayOnlineMinutes(@NotNull java.util.UUID playerUuid) { return service.todayOnlineMinutesPublic(playerUuid); }
            @Override public int weeklyOnlineMinutes(@NotNull java.util.UUID playerUuid) { return service.weeklyOnlineMinutesPublic(playerUuid); }
            @Override public int monthlyOnlineMinutes(@NotNull java.util.UUID playerUuid) { return service.monthlyOnlineMinutesPublic(playerUuid); }
            @Override public boolean signIn(@NotNull Player player) { return service.signIn(player).success(); }
            @Override public boolean makeupSignIn(@NotNull Player player, @NotNull String date) { return service.makeupSignIn(player, date).success(); }
            @Override public boolean addMakeupCards(@NotNull Player player, int amount) { return service.addMakeupCardsPublic(player, amount); }
            @Override public void grantDailyRewards(@NotNull Player player) { service.grantDailyRewardsPublic(player); }
            @Override public boolean addOnlineMinutes(@NotNull Player player, int minutes) { return service.addOnlineMinutesPublic(player, minutes); }
        });
        adminCommand = new OnlineRewardsAdminCommand(() -> service, messages());

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "onlinerewards"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return rewardsRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("OnlineRewards purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return rewardsRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("OnlineRewards purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "onlinerewards"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return rewardsRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return rewardsRepo.getDescriptor();
                }
            });

        logger.fine(
            "OnlineRewards 模块已载入，rewards=" + configuration.rewards().size()
                + " | storage=" + configuration.storage().dialect().configKey()
                + " | menu-ui=" + service.runtimeMenuUiId()
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
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

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    public OnlineRewardsService getService() {
        return service;
    }

    public OnlineRewardsModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "onlinerewards"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}



