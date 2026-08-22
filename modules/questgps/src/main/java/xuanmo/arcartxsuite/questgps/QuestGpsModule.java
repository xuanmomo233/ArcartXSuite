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
import xuanmo.arcartxsuite.questgps.chemdah.database.QuestGpsMysqlDatabase;
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

    /** @return 模块描述符，包含 id、名称与版本 */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("questgps")
            .name("QuestGPS")
            .version("1.1.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    /** @return 主配置文件名 */
    

    /** @return 当前配置文件版本号（用于迁移脚本识别版本） */
    

    /** @return 消息文件名 */
    

    /** @return 默认配置同步策略，允许 {@code categories} 段动态新增 */
    

    /** @return 主配置文件的校验规则列表 */
    

    /** @return UI 资源文件到目标路径的映射 */
    

    /** @return 是否在启用时覆盖已存在的 UI 文件 */
    

    /**
     * 加载并解析主配置文件，初始化任务目录与 MySQL 数据库。
     *
     * @param configFile 主配置文件，为 {@code null} 时抛出异常
     * @throws Exception 配置缺失或解析失败时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXQuestGPS.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String questsDirRelative = yaml.getString("quests-directory", "quests");
        File questsDirectory = new File(dataFolder, questsDirRelative);
        if (!questsDirectory.exists()) {
            questsDirectory.mkdirs();
        }
        ensureQuestDefaults(questsDirectory);
        configuration = QuestGpsModuleConfiguration.load(yaml, logger, questsDirectory);
        if (configuration.database().enabled() && Bukkit.getPluginManager().getPlugin("Chemdah") == null) {
            logger.fine("QuestGPS: database.enabled=true 但未检测到 Chemdah 插件，MySQL 数据库注册已跳过。");
        } else {
            QuestGpsMysqlDatabase.registerIfNeeded(logger, configuration.database());
        }
    }

    private void ensureQuestDefaults(File questsDirectory) {
        File[] existing = questsDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        String[] defaults = {"quests/mainline.yml", "quests/side.yml", "quests/encounter.yml"};
        for (String res : defaults) {
            String fileName = res.substring(res.lastIndexOf('/') + 1);
            File target = new File(questsDirectory, fileName);
            if (!target.exists()) {
                exportResource(res, target, false);
            }
        }
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
            messages(),
            vanillaItemNameBridge
        );
        service.setEventBusProvider(() -> getCapability(EventBusCapability.class));
        service.start();

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

    /** @return 客户端数据包处理器，委托给 {@link QuestGpsService#handleClientPacket} */
    

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


