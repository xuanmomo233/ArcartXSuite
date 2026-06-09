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
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.listener.AfkRewardListener;
import xuanmo.arcartxsuite.afkreward.placeholder.AfkRewardPlaceholderExpansion;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;

public final class AfkRewardModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String HUD_UI_RESOURCE_PATH = "arcartx/ui/afk_reward_hud.yml";
    private static final String HUD_UI_FILE_PATH = "ui/afk_reward_hud.yml";

    private AfkRewardConfiguration configuration;
    private AfkRewardRepository repository;
    private AfkRewardService service;
    private AfkRewardListener listener;
    private AfkRewardAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("afkreward")
            .name("AfkReward")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .externalDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXAfkReward.yml";
    }

    @Override
    protected int currentConfigVersion() {
        return 2;
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.dialect", ValueType.STRING)
                .withEnum(java.util.Set.of("sqlite", "mysql")),
            ValidationRule.of("reward.round", ValueType.INT)
                .withRange(1, 1440),
            ValidationRule.of("reward.max.limit", ValueType.INT)
                .withRange(1, 9999),
            ValidationRule.of("reward.player.limit", ValueType.INT)
                .withRange(1, 9999),
            ValidationRule.of("manual.leaderboard-size", ValueType.INT)
                .withRange(1, 100)
        );
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // types.* 和 areas.* 允许用户自由增删
        return SyncPolicy.builder()
            .dynamicSection("types")
            .dynamicSection("areas")
            .build();
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(HUD_UI_RESOURCE_PATH, HUD_UI_FILE_PATH);
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFile();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXAfkReward.yml 配置文件缺失");
        }
        configuration = AfkRewardConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), context.logger()
        );
    }

    @Override
    protected void startService() throws Exception {
        repository = new AfkRewardRepository(
            context.dataFolder(), configuration.storage(), context.logger()
        );
        repository.initialize();

        service = new AfkRewardService(
            context.plugin(), configuration, repository, context.logger(), messages()
        );
        service.start();

        listener = new AfkRewardListener(() -> service);
        adminCommand = new AfkRewardAdminCommand(() -> service, messages());

        // UI 绑定
        PacketBridgeAPI packetBridge = (PacketBridgeAPI) context.packetBridge();
        if (packetBridge != null && packetBridge.isAvailable() && configuration.ui().registerOnEnable()) {
            File uiFile = new File(context.pluginDataFolder(), HUD_UI_FILE_PATH);
            UiBinding binding = context.prepareUiBinding(
                "AfkRewardHUD", configuration.ui().hudId(), true, uiFile
            );
            if (binding != null) {
                recordUiBinding(HUD_UI_FILE_PATH, binding);
            }
        }

        // 注册跨服/迁移能力
        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "afkreward"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return repository.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return repository.getDescriptor();
                }
            });

        context.registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "afkreward"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return repository.deletePlayerData(playerUuid); }
                    catch (Exception e) { context.logger().warning("AfkReward purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return repository.deleteAllPlayerData(); }
                    catch (Exception e) { context.logger().warning("AfkReward purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        context.logger().info(
            ChatColor.GOLD + "AfkReward 模块已启动 (区域=" + configuration.areas().size()
                + " | 类型=" + configuration.types().size() + ")"
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            repository.shutdown();
            repository = null;
        }
        listener = null;
        adminCommand = null;
        configuration = null;
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
        return new AfkRewardPlaceholderExpansion(context.plugin(), () -> service);
    }

    @Override
    public String commandId() { return "afkreward"; }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload");
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
