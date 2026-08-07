package xuanmo.arcartxsuite.conversation;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.conversation.cinematic.CinematicConfiguration;
import xuanmo.arcartxsuite.conversation.cinematic.CinematicService;
import xuanmo.arcartxsuite.conversation.command.ConversationAdminCommand;
import xuanmo.arcartxsuite.conversation.config.ConversationModuleConfiguration;
import xuanmo.arcartxsuite.conversation.service.ConversationService;
import xuanmo.arcartxsuite.api.capability.InteractionState;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

/**
 * Conversation 模块的主入口，基于 Chemdah 对话引擎为 ArcartX 客户端提供 NPC 对话能力。
 * <p>
 * 该模块负责注册对话 / 选择器 / 信箱 UI，启动 {@link ConversationService} 与
 * {@link CinematicService}，并将宿主全局按键（交互确认、上下导航）路由到对话服务。
 * 同时实现 {@link ModuleCommandHandler}，提供 {@code /axs conversation} 管理子命令。
 */
public final class ConversationModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String DIALOG_UI_RESOURCE_PATH = "arcartx/ui/conversation_dialog.yml";
    private static final String DIALOG_UI_FILE_PATH = "ui/conversation_dialog.yml";
    private static final String SELECTOR_UI_RESOURCE_PATH = "arcartx/ui/conversation_selector.yml";
    private static final String SELECTOR_UI_FILE_PATH = "ui/conversation_selector.yml";
    private static final String LETTERBOX_UI_RESOURCE_PATH = "arcartx/ui/conversation_letterbox.yml";
    private static final String LETTERBOX_UI_FILE_PATH = "ui/conversation_letterbox.yml";
    private static final String DEFAULT_LETTERBOX_UI_ID = "AXS:conversation_letterbox";

    private ConversationModuleConfiguration configuration;
    private ConversationService service;
    private ConversationAdminCommand adminCommand;
    private CinematicService cinematicService;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("conversation")
            .name("Conversation")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXConversation.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("client.packet-id", ValueType.STRING),
            ValidationRule.of("client.register-ui-on-enable", ValueType.BOOLEAN),
            ValidationRule.of("interaction.enabled", ValueType.BOOLEAN),
            ValidationRule.of("interaction.scan-range", ValueType.DOUBLE).withRange(1.0, 128.0),
            ValidationRule.of("interaction.scan-period-ticks", ValueType.INT).withRange(1, 200),
            ValidationRule.of("interaction.open-cooldown-ms", ValueType.LONG).withRange(0L, 60000L),
            ValidationRule.of("cinematic.enabled", ValueType.BOOLEAN),
            ValidationRule.of("cinematic.letterbox-ui-id", ValueType.STRING)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(DIALOG_UI_RESOURCE_PATH, DIALOG_UI_FILE_PATH);
        mappings.put(SELECTOR_UI_RESOURCE_PATH, SELECTOR_UI_FILE_PATH);
        mappings.put(LETTERBOX_UI_RESOURCE_PATH, LETTERBOX_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXConversation.yml 配置文件缺失");
        }
        configuration = ConversationModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile));
    }

    @Override
    protected void startService() throws Exception {

        java.util.List<String> dialogRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.clientConfig().dialogUiIds()) {
            UiBinding dialogBinding = registerModuleUi(
                "arcartx/ui/conversation_dialog.yml",
                DIALOG_UI_FILE_PATH,
                candidateUiId,
                configuration.registerUiOnEnable()
            );
            if (dialogBinding.registeredUiId() != null) {
                dialogRuntimeUiIds.add(dialogBinding.runtimeUiId());
            }
        }
        java.util.List<String> selectorRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.clientConfig().selectorUiIds()) {
            UiBinding selectorBinding = registerModuleUi(
                "arcartx/ui/conversation_selector.yml",
                SELECTOR_UI_FILE_PATH,
                candidateUiId,
                configuration.registerUiOnEnable()
            );
            if (selectorBinding.registeredUiId() != null) {
                selectorRuntimeUiIds.add(selectorBinding.runtimeUiId());
            }
        }
        if (dialogRuntimeUiIds.isEmpty() || selectorRuntimeUiIds.isEmpty()) {
            throw new IllegalStateException("Conversation UI 注册失败");
        }

        service = new ConversationService(
            plugin, logger, packetGuard, configuration, packetBridge,
            java.util.List.copyOf(dialogRuntimeUiIds), java.util.List.copyOf(selectorRuntimeUiIds),
            createAdyeshachNpcBridge()
        );
        service.start();

        // 电影序列服务
        CinematicConfiguration cinematicConfig = CinematicConfiguration.load(
            YamlConfiguration.loadConfiguration(new File(dataFolder, configFileName())));
        java.util.List<String> letterboxRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.cinematic().letterboxUiIds()) {
            UiBinding letterboxBinding = registerModuleUi(
                LETTERBOX_UI_RESOURCE_PATH,
                LETTERBOX_UI_FILE_PATH,
                candidateUiId,
                configuration.registerUiOnEnable()
            );
            if (letterboxBinding.registeredUiId() != null) {
                letterboxRuntimeUiIds.add(letterboxBinding.runtimeUiId());
            }
        }
        cinematicService = new CinematicService(
            plugin, logger, cinematicConfig,
            createAdyeshachNpcBridge(), packetBridge,
            createSoundPlayerBridge(),
            getCapability(xuanmo.arcartxsuite.api.capability.SubtitlePlayable.class),
            java.util.List.copyOf(letterboxRuntimeUiIds)
        );
        cinematicService.setDebug(configuration.debug());

        adminCommand = new ConversationAdminCommand(
            () -> service, () -> service == null ? null : service.npcBridge(),
            () -> cinematicService, messages());

        registerCapability(InteractionState.class, player ->
            (service != null && service.isPlayerInteracting(player))
            || (cinematicService != null && cinematicService.isPlaying(player))
        );

        // 注册宿主全局按键回调（优先级 50，拾取优先于对话）
        registerKeybindHandler("AXS_INTERACT", 50, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleConfirmKeyFromHost(player);
        });
        registerKeybindHandler("AXS_NAVIGATE_PREV", 10, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleNavigationKeyFromHost(player, -1);
        });
        registerKeybindHandler("AXS_NAVIGATE_NEXT", 10, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleNavigationKeyFromHost(player, 1);
        });

        logger.fine(
            "Conversation 模块已载入 | dialogUI: " + dialogRuntimeUiIds
                + " | selectorUI: " + selectorRuntimeUiIds
        );
    }

    @Override
    protected void stopService() {
        if (cinematicService != null) {
            cinematicService.shutdown();
            cinematicService = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        adminCommand = null;
        configuration = null;
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) {
                service.applyNpcAppearancesForPlayer(player);
            }
        };
    }

    @Override
    protected java.util.@NotNull List<org.bukkit.event.Listener> createListeners() {
        return java.util.List.of(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (cinematicService != null) {
                    cinematicService.onPlayerQuit(event.getPlayer());
                }
            }
        });
    }

    /**
     * 获取对话服务实例。
     *
     * @return 已启动的 {@link ConversationService}，模块未启用或已停止时返回 {@code null}
     */
    public ConversationService getService() {
        return service;
    }

    /**
     * 获取电影序列服务实例。
     *
     * @return 已启动的 {@link CinematicService}，模块未启用或已停止时返回 {@code null}
     */
    public CinematicService getCinematicService() {
        return cinematicService;
    }

    // ─── ModuleCommandHandler ───────────────────────────────

    /**
     * 返回该处理器对应的命令标识，用于宿主命令路由。
     *
     * @return 命令 id {@code "conversation"}
     */
    @Override
    public String commandId() {
        return "conversation";
    }

    /**
     * 返回该命令支持的子动作列表，用于 Tab 补全与帮助提示。
     *
     * @return 子动作列表，服务未就绪时返回默认占位列表
     */
    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "adyeshach", "cinematic");
    }

    /**
     * 处理 {@code /axs conversation <action> ...} 命令。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组，首个元素为子动作
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (adminCommand != null) {
            return adminCommand.onCommand(sender, label, args);
        }
        sender.sendMessage(messages().get("common.service-down-command"));
        return true;
    }

    /**
     * 为 {@code /axs conversation} 命令提供 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   当前已输入的参数
     * @return 补全候选列表，服务未就绪或无建议时返回 {@code null}
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}



