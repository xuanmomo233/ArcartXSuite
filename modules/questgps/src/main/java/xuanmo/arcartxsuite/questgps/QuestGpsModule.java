package xuanmo.arcartxsuite.questgps;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
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
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.questgps.command.QuestGpsAdminCommand;
import xuanmo.arcartxsuite.questgps.command.QuestGpsPlayerCommand;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.questgps.service.QuestGpsService;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;

/**
 * QuestGPS 模块主类。
 * <p>
 * 继承 {@link AbstractAXSModule}，负责模块配置加载、UI 资源注册、
 * {@link QuestGpsService} 启停、命令与客户端数据包绑定，以及
 * {@link xuanmo.arcartxsuite.api.capability.QuestGpsNavigable} 能力注册。
 * 同时实现 {@link ModuleCommandHandler}，将管理命令委托给 {@link QuestGpsAdminCommand}。
 */
public final class QuestGpsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private QuestGpsAdminCommand adminCommand;

    private QuestGpsModuleConfiguration configuration;
    private QuestGpsService service;
    /** 地图导航互斥事件订阅 ID（axs.map.nav_started），stopService 时退订。 */
    private String mapNavSubscriptionId;
    private String mapReadySubscriptionId;

    /** @return 模块描述符，包含 id、名称与版本 */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("questgps")
            .name("QuestGPS")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXQuestGPS.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("categories")
                .build())
            .currentVersion(4)
            .validations(List.of(
                    ValidationRule.required("ui.packet-id", ValueType.STRING),
                    ValidationRule.of("navigation.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.waypoint.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.model-marker.path-max-iterations", ValueType.INT).withRange(100, 50000),
                    ValidationRule.of("navigation.texture-marker.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("navigation.texture-marker.width", ValueType.DOUBLE).withRange(0.0, null),
                    ValidationRule.of("navigation.texture-marker.height", ValueType.DOUBLE).withRange(0.0, null),
                    ValidationRule.of("navigation.texture-marker.pitch", ValueType.DOUBLE).withRange(-180.0, 180.0),
                    ValidationRule.of("navigation.texture-marker.path-max-iterations", ValueType.INT).withRange(100, 50000),
                    ValidationRule.of("debug.enabled", ValueType.BOOLEAN)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(QuestGpsService.MENU_UI_RESOURCE_PATH, QuestGpsService.MENU_UI_FILE_PATH);
        mappings.put(QuestGpsService.GUIDE_UI_RESOURCE_PATH, QuestGpsService.GUIDE_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data), 0, configuration.ui().packetId(), "questgps");
    }

    /**
     * 加载并解析主配置文件，初始化任务目录与 MySQL 数据库。
     *
     * @param configFile 主配置文件，为 {@code null} 时抛出异常
     * @throws Exception 配置缺失或解析失败时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) throw new IllegalStateException("ArcartXQuestGPS.yml 配置文件缺失");
        configuration = QuestGpsModuleConfiguration.load(YamlConfiguration.loadConfiguration(configFile), logger);
        File example = new File(dataFolder, "examples/chemdah-quest.yml");
        if (!example.exists()) exportResource("examples/chemdah-quest.yml", example, false);
    }

    /**
     * 启动模块服务：注册菜单/引导 UI、构建 {@link QuestGpsService}、
     * 注册 {@link xuanmo.arcartxsuite.api.capability.QuestGpsNavigable} 能力。
     *
     * @throws Exception UI 注册失败或服务启动异常时抛出
     */
    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.questgps.quest_completed");
    }

    @Override
    protected void startService() throws Exception {

        java.util.List<String> menuRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.ui().menuUiIds()) {
            UiBinding menuBinding = registerModuleUi(
                QuestGpsService.MENU_UI_RESOURCE_PATH,
                QuestGpsService.MENU_UI_FILE_PATH,
                candidateUiId,
                configuration.ui().registerUiOnEnable()
            );
            if (menuBinding.registeredUiId() != null) {
                menuRuntimeUiIds.add(menuBinding.runtimeUiId());
            }
        }
        java.util.List<String> guideRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.ui().guideUiIds()) {
            UiBinding guideBinding = registerModuleUi(
                QuestGpsService.GUIDE_UI_RESOURCE_PATH,
                QuestGpsService.GUIDE_UI_FILE_PATH,
                candidateUiId,
                configuration.ui().registerUiOnEnable()
            );
            if (guideBinding.registeredUiId() != null) {
                guideRuntimeUiIds.add(guideBinding.runtimeUiId());
            }
        }
        if (menuRuntimeUiIds.isEmpty()) {
            throw new IllegalStateException("QuestGPS UI 注册失败");
        }

        service = new QuestGpsService(
            plugin, logger, packetGuard, configuration, packetBridge, itemStackBridge,
            () -> getCapability(TitleConfigQueryable.class),
            () -> getCapability(MapNavigable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(ChatCardSendable.class),
            (signal, player, questId) -> {
                SignalDispatchable dispatcher = getCapability(SignalDispatchable.class);
                if (dispatcher != null) {
                    dispatcher.dispatchSignal(signal, player, Map.of("quest_id", questId == null ? "" : questId));
                } else if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("QuestGPS: hook signal: " + signal + " -> " + player.getName());
                }
            },
            java.util.List.copyOf(menuRuntimeUiIds),
            java.util.List.copyOf(guideRuntimeUiIds),
            itemSourceRegistry,
            createWaypointBridge(),
            createAdyeshachNpcBridge(),
            worldTextureBridge,
            messages(),
            vanillaItemNameBridge
        );
        service.setEventBusProvider(() -> getCapability(EventBusCapability.class));
        service.start();

        // 互斥：地图自身导航开始时停止本模块的任务导航（任务标记仍保留在地图上）
        EventBusCapability eventBus = getCapability(EventBusCapability.class);
        if (eventBus != null) {
            mapNavSubscriptionId = eventBus.subscribe("axs.map.nav_started", event -> {
                if (event.player() != null && service != null) {
                    service.onMapNavigationStarted(event.player());
                }
            });
            mapReadySubscriptionId = eventBus.subscribe("axs.map.ready", event -> {
                if (event.player() != null && service != null) service.onMapReady(event.player());
            });
        }

        // 注册 QuestGpsNavigable capability
        registerCapability(QuestGpsNavigable.class, new QuestGpsNavigable() {
            @Override
            public void offerQuest(@NotNull org.bukkit.entity.Player player, @NotNull String questId, boolean openMenu) {
                service.offerQuest(player, questId, openMenu);
            }

            @Override
            public void acceptQuest(@NotNull org.bukkit.entity.Player player, @NotNull String questId) {
                service.acceptQuest(player, questId);
            }

            @Override
            public void openMenu(@NotNull org.bukkit.entity.Player player) {
                service.openMenu(player);
            }

            @Override
            public void trackQuest(@NotNull org.bukkit.entity.Player player, @NotNull String questId) {
                service.trackQuest(player, questId);
            }

            @Override
            public void trackTask(@NotNull org.bukkit.entity.Player player, @NotNull String questId, @NotNull String taskId) {
                service.trackTask(player, questId, taskId);
            }

            @Override
            public boolean eventRuleLocked(@NotNull org.bukkit.entity.Player player, @NotNull String ruleId) {
                return service.eventRuleLocked(player, ruleId);
            }

            @Override
            public boolean moduleEntryLocked(@NotNull org.bukkit.entity.Player player, @NotNull String moduleEntryId) {
                return service.moduleEntryLocked(player, moduleEntryId);
            }

            @Override
            public @NotNull QuestState questState(@NotNull org.bukkit.entity.Player player, @NotNull String questId) {
                return service.questState(player, questId);
            }
        });
        adminCommand = new QuestGpsAdminCommand(() -> service, messages());

        logger.fine(
            "QuestGPS: 模块已载入，packet-id=" + configuration.ui().packetId()
                + " | menu-ui=" + menuRuntimeUiIds
                + " | guide-ui=" + guideRuntimeUiIds
                + " | quests=" + configuration.configuredQuestCount()
        );
    }

    /** 停止模块服务，释放 {@link QuestGpsService} 与配置资源。 */
    @Override
    protected void stopService() {
        // 地图导航回调由服务在发布外部目标时注册，并随服务关闭注销。
        EventBusCapability eventBus = getCapability(EventBusCapability.class);
        if (eventBus != null) {
            if (mapNavSubscriptionId != null) eventBus.unsubscribe(mapNavSubscriptionId);
            if (mapReadySubscriptionId != null) eventBus.unsubscribe(mapReadySubscriptionId);
        }
        mapNavSubscriptionId = null;
        mapReadySubscriptionId = null;
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    /** @return 玩家命令绑定映射，键为命令名，值为 {@link TabExecutor} */
    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        QuestGpsPlayerCommand cmd = new QuestGpsPlayerCommand(() -> service, messages());
        return Map.of("questgps", (TabExecutor) cmd);
    }

    /** @return 当前运行中的 {@link QuestGpsService} 实例，未启动时为 {@code null} */
    public QuestGpsService getService() {
        return service;
    }

    /** @return 命令标识符，固定为 {@code "questgps"} */
    @Override public String commandId() { return "questgps"; }
    /** @return 支持的子操作列表 */
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    /**
     * 委托给 {@link QuestGpsAdminCommand} 处理管理命令。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组
     * @return {@code true} 表示已处理
     */
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    /**
     * 委托给 {@link QuestGpsAdminCommand} 提供 Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表
     */
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

