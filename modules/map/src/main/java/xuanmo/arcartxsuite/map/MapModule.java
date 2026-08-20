package xuanmo.arcartxsuite.map;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.map.command.MapAdminCommand;
import xuanmo.arcartxsuite.map.command.MapPlayerCommand;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.service.MapService;
import xuanmo.arcartxsuite.map.storage.JdbcMapRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

/**
 * 地图模块主类，继承 {@code AbstractAXSModule} 并实现管理命令接口。
 * <p>
 * 关联模块：map。负责加载配置、注册 UI、启动 {@code MapService}、
 * 绑定玩家与管理命令、注册 {@code MapNavigable} / {@code PlayerDataPurgeable} /
 * {@code DatabaseMigratable} capability，以及处理客户端初始化与数据包路由。
 */
public final class MapModule extends AbstractAXSModule implements ModuleCommandHandler {

    private MapAdminCommand adminCommand;

    private MapModuleConfiguration configuration;
    private MapService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("map")
            .name("Map")
            .version("1.1.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXMap.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected int currentConfigVersion() {
        // client 节的 UI 配置字段整体迁移到 ui 节（move 操作，见 migrations/1-2.yml）
        return 3;
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // worlds、anchors、waypoints 为用户自定义世界、锚点与路径点
        return SyncPolicy.builder()
            .dynamicSection("worlds")
            .dynamicSection("anchors")
            .dynamicSection("waypoints")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.shared 必须是布尔值
            ValidationRule.required("storage.shared", ValueType.BOOLEAN),
            // pool-size 范围 1-100（仅在 shared=false 时需要）
            ValidationRule.of("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.required("ui.packet-id", ValueType.STRING),
            ValidationRule.of("navigation.enabled", ValueType.BOOLEAN)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MapService.MENU_UI_RESOURCE_PATH, MapService.MENU_UI_FILE_PATH);
        mappings.put(MapService.HUD_UI_RESOURCE_PATH, MapService.HUD_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.client().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMap.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String anchorsDirRelative = yaml.getString("anchors-directory", "anchors");
        File anchorsDirectory = new File(dataFolder, anchorsDirRelative);
        if (!anchorsDirectory.exists()) {
            anchorsDirectory.mkdirs();
        }
        File[] existing = anchorsDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing == null || existing.length == 0) {
            File defaultAnchors = new File(anchorsDirectory, "default.yml");
            if (!defaultAnchors.exists()) {
                exportResource("anchors/default.yml", defaultAnchors, false);
            }
        }
        configuration = MapModuleConfiguration.load(yaml, logger, anchorsDirectory);
    }

    @Override
    protected void startService() throws Exception {

        UiBinding menuBinding = registerModuleUi(
            MapService.MENU_UI_FILE_PATH,
            configuration.client().menuUiId(),
            configuration.client().registerUiOnEnable()
        );
        UiBinding hudBinding = registerModuleUi(
            MapService.HUD_UI_FILE_PATH,
            configuration.client().hudUiId(),
            configuration.client().registerUiOnEnable()
        );
        if (menuBinding.registeredUiId() == null || hudBinding.registeredUiId() == null) {
            throw new IllegalStateException("Map UI 注册失败");
        }

        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（dialect=mysql 等），则走自建模式（向后兼容）
        JdbcMapRepository mapRepo;
        if (configuration.storage().hasOverride()) {
            // 旧路径：模块自建连接池
            mapRepo = new JdbcMapRepository(
                dataFolder,
                configuration.storage(), logger);
        } else {
            // 新路径：共享本体数据源
            mapRepo = new JdbcMapRepository(
                storageManager, "map",
                "", configuration.storage().sqliteFileName(), logger);
        }
        service = new MapService(
            plugin, logger, packetGuard, configuration,
            mapRepo,
            packetBridge, menuBinding.runtimeUiId(), hudBinding.runtimeUiId(),
            itemSourceRegistry, itemMatcher, currencyManager,
            createWaypointBridge(),
            createAdyeshachNpcBridge(),
            messages()
        );
        service.start();

        // 注册 MapNavigable capability，供 QuestGps 等模块调用
        registerCapability(MapNavigable.class, new MapNavigable() {
            @Override
            public void upsertExternalTarget(
                @NotNull Player player, @NotNull String targetId, @NotNull String source,
                @NotNull String worldId, double x, double y, double z,
                @NotNull String displayName, @Nullable String iconId, boolean select
            ) {
                service.upsertExternalTarget(player,
                    new MapExternalTarget(targetId, source, worldId, displayName,
                        "QuestGPS 任务导航", x, y, z, 0),
                    select);
            }

            @Override
            public void clearExternalTargets(@NotNull Player player, @NotNull String source, boolean syncView) {
                service.clearExternalTargets(player, source, syncView);
            }

            @Override
            public void openMenuFor(@NotNull Player player, @NotNull String waypointId) {
                service.openMenuFor(player, waypointId);
            }
        });

        adminCommand = new MapAdminCommand(() -> service, messages());

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "map"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return mapRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Map purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return mapRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Map purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "map"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return mapRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return mapRepo.getDescriptor();
                }
            });

        logger.fine(
            "Map 模块已载入，packet-id=" + configuration.client().packetId()
                + " | menu-ui=" + menuBinding.runtimeUiId()
                + " | hud-ui=" + hudBinding.runtimeUiId()
                + " | worlds=" + configuration.worlds().size()
                + " | anchors=" + configuration.anchors().size()
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
        MapPlayerCommand cmd = new MapPlayerCommand(() -> service, () -> configuration, messages());
        return Map.of("map", (TabExecutor) cmd);
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

    /**
     * 获取当前运行中的地图服务实例。
     *
     * @return 地图服务实例，模块未启动时返回 {@code null}
     */
    public MapService getService() {
        return service;
    }

    @Override public String commandId() { return "map"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}


