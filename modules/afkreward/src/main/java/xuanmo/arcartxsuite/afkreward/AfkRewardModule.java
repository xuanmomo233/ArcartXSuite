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
import xuanmo.arcartxsuite.api.capability.AfkRewardDispatchable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.afkreward.config.AreaConfiguration;
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
        return 4;
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
                .withRange(1, 100),
            ValidationRule.of("anti-abuse.time-limit.on-exceed", ValueType.STRING)
                .withEnum(java.util.Set.of("STOP", "KICK", "DECAY")),
            ValidationRule.of("anti-abuse.bot-detection.action", ValueType.STRING)
                .withEnum(java.util.Set.of("NO_REWARD", "KICK")),
            ValidationRule.of("anti-abuse.bot-detection.window-seconds", ValueType.INT)
                .withRange(1, 3600),
            ValidationRule.of("anti-abuse.bot-detection.min-view-changes", ValueType.INT)
                .withRange(0, 1000),
            ValidationRule.of("multiplier.combine", ValueType.STRING)
                .withEnum(java.util.Set.of("MAX", "MULTIPLY", "LAST")),
            ValidationRule.of("manual.permission-recheck-seconds", ValueType.INT)
                .withRange(1, 3600)
        );
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // types.* 允许用户自由增删
        return SyncPolicy.builder()
            .dynamicSection("types")
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
            YamlConfiguration.loadConfiguration(configFile), logger
        );

        // 区域目录应位于模块 data 目录下，而非插件根目录
        String effectiveAreasDir = configuration.areasDirectory();

        // 导出默认区域文件（若目录为空）
        AreaConfiguration.exportDefaultArea(moduleClassLoader(),
            dataFolder, effectiveAreasDir);
    }

    @Override
    protected void startService() throws Exception {
        // 加载区域独立配置文件
        String effectiveAreasDir = configuration.areasDirectory();
        java.util.Map<String, xuanmo.arcartxsuite.afkreward.model.AfkArea> loadedAreas =
            AreaConfiguration.loadAreas(dataFolder, effectiveAreasDir, logger, configuration.types().keySet());
        configuration = configuration.withAreas(loadedAreas);

        repository = new AfkRewardRepository(
            dataFolder, configuration.storage(), logger
        );
        repository.initialize();

        service = new AfkRewardService(
            plugin, configuration, repository, logger, messages(),
            () -> getCapability(MailDispatchable.class),
            () -> getCapability(SignalDispatchable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(EssentialsQueryable.class)
        );
        service.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        UiBinding hudBinding = null;
        if (configuration.ui().registerOnEnable()) {
            hudBinding = registerModuleUi(HUD_UI_FILE_PATH, configuration.ui().hudId(), true);
        }
        if (hudBinding != null) {
            service.setHudBridge(packetBridge, hudBinding.runtimeUiId());
        }
        service.start();

        listener = new AfkRewardListener(() -> service);
        adminCommand = new AfkRewardAdminCommand(() -> service, messages());

        // UI 绑定
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "afkreward"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return repository.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("AfkReward purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return repository.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("AfkReward purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        // 注册 AfkRewardDispatchable capability
        AfkRewardService svc = service;
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
                return svc != null && svc.startManualAfk(player, areaName);
            }
        });

        logger.info(
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
        return new AfkRewardPlaceholderExpansion(plugin, () -> service);
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
