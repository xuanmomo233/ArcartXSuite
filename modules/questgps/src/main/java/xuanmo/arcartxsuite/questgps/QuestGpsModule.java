package xuanmo.arcartxsuite.questgps;

import java.io.File;
import java.util.LinkedHashMap;
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
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
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

public final class QuestGpsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private QuestGpsAdminCommand adminCommand;

    private QuestGpsModuleConfiguration configuration;
    private QuestGpsService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("questgps")
            .name("QuestGPS")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXQuestGPS.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // categories 允许用户新增自定义分类（如 daily）
        return SyncPolicy.builder()
            .dynamicSection("categories")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("client.packet-id", ValueType.STRING),
            ValidationRule.of("navigation.enabled", ValueType.BOOLEAN),
            ValidationRule.of("navigation.mode", ValueType.STRING),
            ValidationRule.of("database.enabled", ValueType.BOOLEAN),
            ValidationRule.of("discovery.mode", ValueType.STRING),
            ValidationRule.of("navigation.path-max-iterations", ValueType.INT).withRange(100, 50000),
            ValidationRule.of("debug.enabled", ValueType.BOOLEAN)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(QuestGpsService.MENU_UI_RESOURCE_PATH, QuestGpsService.MENU_UI_FILE_PATH);
        mappings.put(QuestGpsService.GUIDE_UI_RESOURCE_PATH, QuestGpsService.GUIDE_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.client().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXQuestGPS.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String questsDirRelative = yaml.getString("quests-directory", "quests");
        File questsDirectory = new File(context.dataFolder(), questsDirRelative);
        if (!questsDirectory.exists()) {
            questsDirectory.mkdirs();
        }
        ensureQuestDefaults(questsDirectory);
        configuration = QuestGpsModuleConfiguration.load(yaml, context.logger(), questsDirectory);
        QuestGpsMysqlDatabase.registerIfNeeded(context.logger(), configuration.database());
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
                context.exportResource(res, target, false);
            }
        }
    }

    @Override
    protected void startService() throws Exception {
        PacketBridgeAPI packetBridge = context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();
        ItemBridgeAPI itemStackBridge = context.itemStackBridge();

        java.util.List<String> menuRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.client().menuUiIds()) {
            UiBinding menuBinding = registerModuleUi(
                QuestGpsService.MENU_UI_RESOURCE_PATH,
                QuestGpsService.MENU_UI_FILE_PATH,
                candidateUiId,
                configuration.client().registerUiOnEnable()
            );
            if (menuBinding.registeredUiId() != null) {
                menuRuntimeUiIds.add(menuBinding.runtimeUiId());
            }
        }
        java.util.List<String> guideRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.client().guideUiIds()) {
            UiBinding guideBinding = registerModuleUi(
                QuestGpsService.GUIDE_UI_RESOURCE_PATH,
                QuestGpsService.GUIDE_UI_FILE_PATH,
                candidateUiId,
                configuration.client().registerUiOnEnable()
            );
            if (guideBinding.registeredUiId() != null) {
                guideRuntimeUiIds.add(guideBinding.runtimeUiId());
            }
        }
        if (menuRuntimeUiIds.isEmpty()) {
            throw new IllegalStateException("QuestGPS UI 注册失败");
        }

        service = new QuestGpsService(
            context.plugin(), packetGuard, configuration, packetBridge, itemStackBridge,
            () -> context.getCapability(TitleConfigQueryable.class),
            () -> context.getCapability(MapNavigable.class),
            () -> context.getCapability(SubtitlePlayable.class),
            () -> context.getCapability(ChatCardSendable.class),
            (signal, player, questId) -> {
                SignalDispatchable dispatcher = context.getCapability(SignalDispatchable.class);
                if (dispatcher != null) {
                    dispatcher.dispatchSignal(signal, player, Map.of("quest_id", questId == null ? "" : questId));
                } else if (context.logger().isLoggable(java.util.logging.Level.FINE)) {
                    context.logger().fine("QuestGPS: hook signal: " + signal + " -> " + player.getName());
                }
            },
            java.util.List.copyOf(menuRuntimeUiIds),
            java.util.List.copyOf(guideRuntimeUiIds),
            context.itemSourceRegistry(),
            context.createWaypointBridge(),
            context.createAdyeshachNpcBridge()
        );
        service.start();

        // 注册 QuestGpsNavigable capability
        context.registerCapability(QuestGpsNavigable.class, new QuestGpsNavigable() {
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

        context.logger().fine(
            "QuestGPS: 模块已载入，packet-id=" + configuration.client().packetId()
                + " | menu-ui=" + menuRuntimeUiIds
                + " | guide-ui=" + guideRuntimeUiIds
                + " | quests=" + configuration.configuredQuestCount()
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
        QuestGpsPlayerCommand cmd = new QuestGpsPlayerCommand(() -> service, messages());
        return Map.of("questgps", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    public QuestGpsService getService() {
        return service;
    }

    @Override public String commandId() { return "questgps"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

