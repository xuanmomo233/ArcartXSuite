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
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.conversation.command.ConversationAdminCommand;
import xuanmo.arcartxsuite.conversation.config.ConversationModuleConfiguration;
import xuanmo.arcartxsuite.conversation.service.ConversationService;
import xuanmo.arcartxsuite.api.capability.InteractionState;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class ConversationModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String DIALOG_UI_RESOURCE_PATH = "arcartx/ui/conversation_dialog.yml";
    private static final String DIALOG_UI_FILE_PATH = "ui/conversation_dialog.yml";
    private static final String SELECTOR_UI_RESOURCE_PATH = "arcartx/ui/conversation_selector.yml";
    private static final String SELECTOR_UI_FILE_PATH = "ui/conversation_selector.yml";

    private ConversationModuleConfiguration configuration;
    private ConversationService service;
    private ConversationAdminCommand adminCommand;

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
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(DIALOG_UI_RESOURCE_PATH, DIALOG_UI_FILE_PATH);
        mappings.put(SELECTOR_UI_RESOURCE_PATH, SELECTOR_UI_FILE_PATH);
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
        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();

        File dialogFile = new File(context.pluginDataFolder(), DIALOG_UI_FILE_PATH);
        File selectorFile = new File(context.pluginDataFolder(), SELECTOR_UI_FILE_PATH);

        java.util.List<String> dialogRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.clientConfig().dialogUiIds()) {
            UiBinding dialogBinding = context.prepareUiBinding(
                "Conversation Dialog", candidateUiId,
                configuration.registerUiOnEnable(), dialogFile
            );
            if (dialogBinding != null) {
                recordUiBinding(DIALOG_UI_FILE_PATH + "#" + candidateUiId, dialogBinding);
                dialogRuntimeUiIds.add(dialogBinding.runtimeUiId());
            }
        }
        java.util.List<String> selectorRuntimeUiIds = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.clientConfig().selectorUiIds()) {
            UiBinding selectorBinding = context.prepareUiBinding(
                "Conversation Selector", candidateUiId,
                configuration.registerUiOnEnable(), selectorFile
            );
            if (selectorBinding != null) {
                recordUiBinding(SELECTOR_UI_FILE_PATH + "#" + candidateUiId, selectorBinding);
                selectorRuntimeUiIds.add(selectorBinding.runtimeUiId());
            }
        }
        if (dialogRuntimeUiIds.isEmpty() || selectorRuntimeUiIds.isEmpty()) {
            throw new IllegalStateException("Conversation UI 注册失败");
        }

        service = new ConversationService(
            context.plugin(), packetGuard, configuration, packetBridge,
            java.util.List.copyOf(dialogRuntimeUiIds), java.util.List.copyOf(selectorRuntimeUiIds)
        );
        service.start();
        adminCommand = new ConversationAdminCommand(() -> service, () -> service == null ? null : service.npcBridge(), messages());

        context.registerCapability(InteractionState.class, player -> service != null && service.isPlayerInteracting(player));

        // 注册宿主全局按键回调（优先级 50，拾取优先于对话）
        context.registerKeybindHandler("AXS_INTERACT", 50, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleConfirmKeyFromHost(player);
        });
        context.registerKeybindHandler("AXS_NAVIGATE_PREV", 10, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleNavigationKeyFromHost(player, -1);
        });
        context.registerKeybindHandler("AXS_NAVIGATE_NEXT", 10, (player, keyName) -> {
            if (service == null || !service.interactionReady()) return false;
            return service.handleNavigationKeyFromHost(player, 1);
        });

        context.logger().fine(
            "Conversation 模块已载入 | dialogUI: " + dialogRuntimeUiIds
                + " | selectorUI: " + selectorRuntimeUiIds
        );
    }

    @Override
    protected void stopService() {
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

    public ConversationService getService() {
        return service;
    }

    // ─── ModuleCommandHandler ───────────────────────────────

    @Override
    public String commandId() {
        return "conversation";
    }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "adyeshach");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (adminCommand != null) {
            return adminCommand.onCommand(sender, label, args);
        }
        sender.sendMessage("\u00a7c[Conversation] 服务未启动，命令不可用。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
