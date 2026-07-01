package xuanmo.arcartxsuite.title;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.title.command.TitleAdminCommand;
import xuanmo.arcartxsuite.title.command.TitlePlayerCommand;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleQualityDefinition;
import xuanmo.arcartxsuite.title.placeholder.TitlePlaceholderExpansion;
import xuanmo.arcartxsuite.title.service.TitleOperationResult;
import xuanmo.arcartxsuite.title.service.TitleService;
import xuanmo.arcartxsuite.title.storage.JdbcTitleRepository;

public final class TitleModule extends AbstractAXSModule implements ModuleCommandHandler {

    private TitleAdminCommand adminCommand;

    private static final String TITLE_MENU_RESOURCE_PATH = "arcartx/ui/title_menu.yml";
    private static final String TITLE_MENU_FILE_PATH = "ui/title_menu.yml";

    private TitleModuleConfiguration configuration;
    private TitleService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("title")
            .name("Title")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXTitle.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // 分组、品质、称号定义均由用户自由增删
        return SyncPolicy.builder()
            .dynamicSection("groups")
            .dynamicSection("qualities")
            .dynamicSection("titles")
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
            // 过期清理周期必须为正
            ValidationRule.of("settings.expiration-cleanup-interval-ticks", ValueType.INT)
                .withRange(20, null),
            // UI ID 不能为空
            ValidationRule.required("ui.ui-id", ValueType.STRING)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(TITLE_MENU_RESOURCE_PATH, TITLE_MENU_FILE_PATH);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXTitle.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String titlesDirRelative = yaml.getString("titles-directory", "titles");
        File titlesDirectory = new File(dataFolder, titlesDirRelative);
        if (!titlesDirectory.exists()) {
            titlesDirectory.mkdirs();
        }
        ensureTitleDefaults(titlesDirectory);
        configuration = TitleModuleConfiguration.load(yaml, logger, titlesDirectory);
    }

    private void ensureTitleDefaults(File titlesDirectory) {
        // 若目录已存在用户自定义内容，不再重复导出默认示例
        File[] existing = titlesDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        String[] defaults = {"titles/adventure.yml", "titles/exploration.yml", "titles/activity.yml"};
        for (String res : defaults) {
            String fileName = res.substring(res.lastIndexOf('/') + 1);
            File target = new File(titlesDirectory, fileName);
            if (!target.exists()) {
                exportResource(res, target, false);
            }
        }
    }

    /**
     * 初始化称号服务并注册跨模块能力。
     * <p>
     * 流程：
     * <ol>
     *   <li>构造并启动 {@link TitleService}（含数据库初始化、UI 注册、监听器绑定）</li>
     *   <li>注册 {@link TitleGrantable} —— 供 EventPacket 等模块授予称号</li>
     *   <li>注册 {@link TitleConfigQueryable} —— 供外部查询称号配置元数据</li>
     *   <li>注册 {@link PlayerDataPurgeable} —— 支持 /axs purge 统一清理玩家数据</li>
     *   <li>注册 {@link DatabaseMigratable} —— 支持 /axs migrate 跨源数据库迁移</li>
     * </ol>
     */
    @Override
    protected void startService() throws Exception {

        TitleService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) ->
            exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());

        JdbcTitleRepository titleRepo = new JdbcTitleRepository(
            dataFolder,
            configuration.storage(), logger);
        service = new TitleService(
            plugin, logger, configuration,
            titleRepo,
            packetBridge, packetGuard,
            () -> getCapability(TabRefreshable.class),
            uiExporter,
            attributeBridge,
            worldTextureBridge
        );
        service.start();
        adminCommand = new TitleAdminCommand(() -> service, messages());

        // 注册跨模块称号授予能力（EventPacket 等模块通过 capability 调用）
        registerCapability(TitleGrantable.class,
            (playerId, titleId, duration, source) -> {
                var specOpt = xuanmo.arcartxsuite.title.TitleDurationParser.parse(duration);
                if (specOpt.isEmpty()) {
                    logger.warning("TitleGrantable 收到无效的 duration 格式: " + duration);
                    return false;
                }
                TitleOperationResult result = service.giveTitle(playerId, titleId, specOpt.get(), source);
                return result.success();
            });

        registerCapability(TitleConfigQueryable.class, titleId -> {
            if (configuration == null) return null;
            TitleDefinition def = configuration.title(titleId);
            if (def == null) return null;
            String qualityName = "";
            TitleQualityDefinition quality = configuration.quality(def.qualityId());
            if (quality != null) qualityName = quality.name();
            return new TitleConfigQueryable.TitleInfo(def.displayName(), qualityName, def.description());
        });

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "title"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return titleRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Title purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return titleRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Title purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "title"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return titleRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return titleRepo.getDescriptor();
                }
            });

        logger.fine(
            "Title 模块已载入，称号数: " + configuration.titles().size()
                + " | 分组数: " + configuration.groups().size()
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
        TitlePlayerCommand cmd = new TitlePlayerCommand(() -> service, () -> configuration, messages());
        return Map.of("title", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new TitlePlaceholderExpansion(plugin, () -> service, () -> configuration);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    public TitleService getService() {
        return service;
    }

    public TitleModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "title"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@org.jetbrains.annotations.NotNull CommandSender sender, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @org.jetbrains.annotations.Nullable List<String> onTabComplete(@org.jetbrains.annotations.NotNull CommandSender sender, @org.jetbrains.annotations.NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}



