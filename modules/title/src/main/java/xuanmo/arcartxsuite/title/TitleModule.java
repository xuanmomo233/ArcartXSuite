package xuanmo.arcartxsuite.title;

import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.title.command.TitleAdminCommand;
import xuanmo.arcartxsuite.title.command.TitlePlayerCommand;
import xuanmo.arcartxsuite.title.config.TitleDefinition;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.config.TitleQualityDefinition;
import xuanmo.arcartxsuite.title.placeholder.TitlePlaceholderExpansion;
import xuanmo.arcartxsuite.title.service.TitleOperationResult;
import xuanmo.arcartxsuite.title.service.TitleService;
import xuanmo.arcartxsuite.title.storage.JdbcTitleRepository;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;
import java.util.concurrent.CompletableFuture;

public final class TitleModule extends AbstractAXSModule implements ModuleCommandHandler {

    private TitleAdminCommand adminCommand;

    private static final String TITLE_MENU_RESOURCE_PATH = "arcartx/ui/title_menu.yml";
    private static final String TITLE_MENU_FILE_PATH = "ui/title/title_menu.yml";

    private TitleModuleConfiguration configuration;
    private TitleService service;
    private ModuleStorage storageScope;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("title")
            .name("Title")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXTitle.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("groups")
                    .dynamicSection("qualities")
                    .dynamicSection("titles")
                .build())
            .currentVersion(2)
            .validations(List.of(
                    // storage.shared 必须是布尔值
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // pool-size 范围 1-100（仅在 shared: false 时生效）
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    // 过期清理周期必须为正
                    ValidationRule.of("settings.expiration-cleanup-interval-ticks", ValueType.INT)
                        .withRange(20, null),
                    // UI ID 不能为空
                    ValidationRule.required("ui.ui-id", ValueType.STRING)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of(TITLE_MENU_RESOURCE_PATH, TITLE_MENU_FILE_PATH), configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data), 0, TitleService.CLIENT_PACKET_ID, "title");
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

        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（dialect=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor titleDesc;
        if (configuration.storage().hasOverride()) {
            titleDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("title", null, dataFolder, titleDesc);
        } else {
            titleDesc = storageManager.getDescriptor().withTablePrefix("");
            storageScope = storageManager.openModuleStorage("title",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        JdbcTitleRepository titleRepo = new JdbcTitleRepository(storageScope.dataSource(), titleDesc, dataFolder, logger);
        service = new TitleService(
            plugin, logger, configuration,
            titleRepo,
            packetBridge, packetGuard,
            () -> getCapability(TabRefreshable.class),
            uiExporter,
            Clock.systemUTC(),
            attributeBridge,
            worldTextureBridge,
            potionAura,
            modAttributes,
            messages()
        );
        service.start();
        // 将 Service 手动注册的 UI 纳入基类生命周期管理（onDisable 时兜底注销）
        for (String uiId : service.registeredUiIds()) {
            recordExternalUi(uiId, uiId);
        }
        adminCommand = new TitleAdminCommand(plugin, () -> service, messages());

        // 注册跨模块称号授予能力（EventPacket 等模块通过 capability 调用）
        registerCapability(TitleGrantable.class, new TitleGrantable() {
            @Override public boolean giveTitle(java.util.UUID playerId, String titleId, String duration, String source) {
                var specOpt = xuanmo.arcartxsuite.title.TitleDurationParser.parse(duration);
                if (specOpt.isEmpty()) {
                    logger.warning("TitleGrantable 收到无效的 duration 格式: " + duration);
                    return false;
                }
                TitleService current = service;
                if (current == null) return false;
                TitleOperationResult result = current.giveTitle(playerId, titleId, specOpt.get(), source);
                return result.success();
            }

            @Override public java.util.concurrent.CompletableFuture<Boolean> grantPermanent(
                    java.util.UUID playerId, String titleId, String source) {
                return service == null ? java.util.concurrent.CompletableFuture.completedFuture(false)
                    : service.grantPermanent(playerId, titleId, source);
            }
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

        TitleService startedService = service;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "title"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    if (!startedService.isReady()) return -1;
                    try { return titleRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Title purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    if (!startedService.isReady()) return -1;
                    try { return titleRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Title purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "title"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    if (!startedService.isReady()) {
                        var result = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
                        result.addError("Title 存储尚未就绪或正在停用");
                        return result;
                    }
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
        TitleService current = service;
        ModuleStorage scope = storageScope;
        service = null;
        storageScope = null;
        try {
            if (current != null) current.shutdown();
        } finally {
            if (scope != null) {
                scope.retireAfter(current == null
                    ? CompletableFuture.completedFuture(null) : current.databaseStopped());
                trackShutdown(scope.retired());
            } else if (current != null) {
                trackShutdown(current.databaseStopped());
            }
            configuration = null;
        }
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

    public TitleService getService() {
        return service;
    }

    public TitleModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "title"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    @Override public boolean onCommand(@org.jetbrains.annotations.NotNull CommandSender sender, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @org.jetbrains.annotations.Nullable List<String> onTabComplete(@org.jetbrains.annotations.NotNull CommandSender sender, @org.jetbrains.annotations.NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

