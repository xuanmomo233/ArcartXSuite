package xuanmo.arcartxsuite.map;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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
import xuanmo.arcartxsuite.map.service.MapService;
import xuanmo.arcartxsuite.map.service.MapTerrainPushService;
import xuanmo.arcartxsuite.map.storage.JdbcMapRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;

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
    /** reload 跨 service 实例暂存的外部运行时状态（外部目标/导航回调/委托）。 */
    private MapService.ExternalRuntime carriedExternalRuntime;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("map")
            .name("Map")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXMap.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("worlds")
                    .dynamicSection("anchors")
                    .dynamicSection("waypoints")
                .build())
            .currentVersion(6)
            .validations(List.of(
                    // storage.shared 必须是布尔值
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // pool-size 范围 1-100（仅在 shared=false 时需要）
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    ValidationRule.required("ui.packet-id", ValueType.STRING),
                    ValidationRule.of("navigation.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.waypoint.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.texture-marker.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.texture-marker.width", ValueType.DOUBLE).withRange(0.0, null),
                    ValidationRule.of("navigation.texture-marker.height", ValueType.DOUBLE).withRange(0.0, null),
                    ValidationRule.of("navigation.texture-marker.pitch", ValueType.DOUBLE).withRange(-180.0, 180.0),
                    ValidationRule.of("navigation.texture-marker.path-max-iterations", ValueType.INT).withRange(100, 50000)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MapService.MENU_UI_RESOURCE_PATH, MapService.MENU_UI_FILE_PATH);
        mappings.put(MapService.HUD_UI_RESOURCE_PATH, MapService.HUD_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.client().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data), 0, null, null);
    }

    @Override
    protected @NotNull List<String> publishedTopics() {
        return List.of(
            "axs.map.nav_started",
            "axs.map.nav_cleared",
            "axs.map.external_navigate",
            "axs.map.external_nav_cancel",
            "axs.map.ready"
        );
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMap.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        // 世界配置目录：每个 <worldId>.yml 含世界配置 + anchors 节
        String worldsDirRelative = yaml.getString("worlds-directory", "worlds");
        File worldsDirectory = new File(dataFolder, worldsDirRelative);
        if (!worldsDirectory.exists()) {
            worldsDirectory.mkdirs();
            // 导出示例世界文件
            File exampleWorld = new File(worldsDirectory, "world.yml");
            if (!exampleWorld.exists()) {
                exportResource("worlds/world.yml", exampleWorld, false);
            }
        }
        configuration = MapModuleConfiguration.load(yaml, logger, worldsDirectory);
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
        xuanmo.arcartxsuite.api.storage.StorageDescriptor mapDesc;
        javax.sql.DataSource mapDs;
        if (configuration.storage().hasOverride()) {
            mapDesc = configuration.storage().toDescriptor();
            mapDs = storageManager.resolveModuleDataSource("map", null, dataFolder, mapDesc);
        } else {
            mapDesc = storageManager.getDescriptor().withTablePrefix("");
            mapDs = storageManager.resolveModuleDataSource("map",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        JdbcMapRepository mapRepo = new JdbcMapRepository(mapDs, mapDesc, dataFolder, logger);
        service = new MapService(
            plugin, logger, packetGuard, configuration,
            mapRepo,
            packetBridge, menuBinding.runtimeUiId(), hudBinding.runtimeUiId(),
            itemSourceRegistry, itemMatcher, currencyManager,
            createWaypointBridge(),
            createAdyeshachNpcBridge(),
            messages(),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.EssentialsQueryable.class),
            worldTextureBridge,
            crossServer
        );
        service.start();

        // HUD defaultOpen 只在玩家进服时触发；reload 路径 shutdown() 已
        // 对所有在线玩家 closeUi，这里必须补 openUi 否则 HUD 不会回来。
        // 客户端 open 动作会回发 hud_ready → 服务端回 init 包恢复 visible。
        if (packetBridge != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                packetBridge.openUi(player, hudBinding.runtimeUiId());
            }
        }

        // reload 续传：恢复第三方来源推送的外部目标与导航回调，并重推视图。
        // 随后发布 axs.map.ready，来源可订阅该事件主动重推最新数据。
        if (carriedExternalRuntime != null) {
            service.importExternalRuntime(carriedExternalRuntime);
            carriedExternalRuntime = null;
        }

        // 导出分享卡片模板到 ArcartX/chat_card/（已存在则保留用户改动）
        ensureMapShareCardDefaults();

        // 订阅宿主全局按键：AXS_MAP_MENU 打开大地图，AXS_MAP_HUD 切换小地图。
        // 优先级 30，低于拾取(10)/对话(50)确认键，避免冲突。
        registerKeybindHandler("AXS_MAP_MENU", 30, (player, keyName) -> {
            if (service == null) return false;
            service.openMenu(player, "");
            return true;
        });
        registerKeybindHandler("AXS_MAP_HUD", 30, (player, keyName) -> {
            if (service == null) return false;
            service.setHud(player, "toggle");
            return true;
        });

        // 注册 MapNavigable capability（公开）：供 QuestGps 等模块与外部插件调用
        registerCapability(MapNavigable.class, new MapNavigable() {
            @Override
            public void upsertExternalTarget(
                @NotNull Player player,
                @NotNull xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec spec,
                boolean select
            ) {
                service.upsertExternalTarget(player, spec, select);
            }

            @Override
            public void upsertExternalTargets(
                @NotNull Player player,
                @NotNull List<xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec> specs
            ) {
                service.upsertExternalTargets(player, specs);
            }

            @Override
            public void reconcileExternalTargets(
                @NotNull Player player,
                @NotNull String source,
                @NotNull List<xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec> specs
            ) {
                service.reconcileExternalTargets(player, source, specs);
            }

            @Override
            public void clearExternalTargets(@NotNull Player player, @NotNull String source, boolean syncView) {
                service.clearExternalTargets(player, source, syncView);
            }

            @Override
            public void setExternalNavigated(@NotNull Player player, @NotNull String targetId, boolean navigated) {
                service.setExternalNavigated(player, targetId, navigated);
            }

            @Override
            public boolean navigateExternalTarget(@NotNull Player player, @NotNull String targetId) {
                return service.navigateExternalTarget(player, targetId);
            }

            @Override
            public void registerNavigationSource(
                @NotNull Plugin owner,
                @NotNull String source,
                @NotNull xuanmo.arcartxsuite.api.capability.MapNavigationHandler handler
            ) {
                service.registerNavigationSource(owner, source, handler);
            }

            @Override
            public void unregisterNavigationSource(@NotNull String source) {
                service.unregisterNavigationSource(source);
            }

            @Override
            public void setNavigationDelegate(
                @NotNull Plugin owner,
                @NotNull xuanmo.arcartxsuite.api.capability.MapNavigationDelegate delegate
            ) {
                service.setNavigationDelegate(owner, delegate);
            }

            @Override
            public void clearNavigationDelegate(@NotNull Plugin owner) {
                service.clearNavigationDelegate(owner);
            }

            @Override
            public boolean hasNavigationDelegate() {
                return service.hasNavigationDelegate();
            }

            @Override
            public void clearTrack(@NotNull Player player) {
                service.clearTrackBySource(player);
            }

            @Override
            public void openMenuFor(@NotNull Player player, @NotNull String targetId) {
                service.openMenuFor(player, targetId);
            }
        });

        // 地形预加载通道：客户端 axsmap:request 请求 → 服务端 axsmap:terrain 回推
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
            plugin, MapTerrainPushService.CHANNEL_TERRAIN);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, MapTerrainPushService.CHANNEL_REQUEST,
            (channel, player, message) -> {
                if (service != null) {
                    service.handleTerrainRequest(player, message);
                }
            });
        // 玩家设置回传通道：客户端 axsmap:setting（UTF key + UTF value）→ 玩家级留存
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, MapService.CHANNEL_SETTING,
            (channel, player, message) -> {
                if (service != null) {
                    service.handleSettingPacket(player, message);
                }
            });

        // 注册 MapAnchorUnlockable capability（公开）：供任务系统/外部插件/命令
        // 以代码路径解锁锚点。unlock=免费强解（跳付费跳距离门）；tryUnlock=完整玩家流程
        registerCapability(xuanmo.arcartxsuite.api.capability.MapAnchorUnlockable.class,
            new xuanmo.arcartxsuite.api.capability.MapAnchorUnlockable() {
                @Override
                public java.util.concurrent.CompletableFuture<Boolean> unlock(
                    @NotNull Player player, @NotNull String anchorId) {
                    java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
                    Runnable task = () -> {
                        if (service == null) { future.complete(false); return; }
                        service.grantAnchorUnlock(player, anchorId).whenComplete((result, error) -> {
                            if (error != null) future.completeExceptionally(error);
                            else future.complete(result.success());
                        });
                    };
                    if (Bukkit.isPrimaryThread()) task.run();
                    else Bukkit.getScheduler().runTask(plugin, task);
                    return future;
                }

                @Override
                public java.util.concurrent.CompletableFuture<Boolean> tryUnlock(
                    @NotNull Player player, @NotNull String anchorId) {
                    java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
                    Runnable task = () -> {
                        if (service == null) { future.complete(false); return; }
                        service.unlockAnchorAsync(player, anchorId).whenComplete((result, error) -> {
                            if (error != null) future.completeExceptionally(error);
                            else future.complete(result.success());
                        });
                    };
                    if (Bukkit.isPrimaryThread()) task.run();
                    else Bukkit.getScheduler().runTask(plugin, task);
                    return future;
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
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
            plugin, MapTerrainPushService.CHANNEL_REQUEST);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
            plugin, MapService.CHANNEL_SETTING);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(
            plugin, MapTerrainPushService.CHANNEL_TERRAIN);
        if (service != null) {
            // 先导出外部运行时状态再 shutdown——shutdown 会清空 externalTargets
            carriedExternalRuntime = service.exportExternalRuntime();
            service.shutdown();
            storageManager.closeModuleDataSource("map");
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

    /**
     * 获取当前运行中的地图服务实例。
     *
     * @return 地图服务实例，模块未启动时返回 {@code null}
     */
    public MapService getService() {
        return service;
    }

    /**
     * 导出默认分享卡片模板到 {@code plugins/ArcartX/chat_card/axs_map_share.yml}。
     * 卡片点击动作走 {@code Packet.send('AXS_MAP', 'claim_share', code)} 静默领取，
     * 已存在的文件不覆盖（用户自定义保留）。
     */
    private void ensureMapShareCardDefaults() {
        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }
        File chatCardDir = new File(arcartX.getDataFolder(), "chat_card");
        if (!chatCardDir.exists()) {
            chatCardDir.mkdirs();
        }
        File target = new File(chatCardDir, "axs_map_share.yml");
        if (target.exists()) {
            return;
        }
        exportResource("map/card/axs_map_share.yml", target, false);
        if (target.exists()) {
            logger.info("已导出地图分享卡片模板到 ArcartX/chat_card/axs_map_share.yml");
        }
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

