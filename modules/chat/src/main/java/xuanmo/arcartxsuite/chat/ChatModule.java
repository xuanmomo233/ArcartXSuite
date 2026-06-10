package xuanmo.arcartxsuite.chat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.ChatMutable;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.bridge.ArcartXItemStackBridge;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.chat.command.ChatAdminCommand;
import xuanmo.arcartxsuite.chat.command.ChatPlayerCommand;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.placeholder.ChatPlaceholderExpansion;
import xuanmo.arcartxsuite.chat.service.ChatService;
import xuanmo.arcartxsuite.chat.storage.JdbcChatRepository;

public final class ChatModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String COMPLETION_UI_RESOURCE_PATH = "chat/ui/axs_chat_completion.yml";
    private static final String COMPLETION_UI_FILE_PATH = "ui/axs_chat_completion.yml";

    private ChatAdminCommand adminCommand;

    private ChatModuleConfiguration configuration;
    private ChatService service;
    private String completionUiId;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("chat")
            .name("Chat")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXChat.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // cards、function 包含用户自定义聊天卡片与功能配置
        return SyncPolicy.builder()
            .dynamicSection("cards")
            .dynamicSection("function")
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
                .withRange(1, 100)
        );
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXChat.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String channelsRelative = yaml.getString("channels-directory", "chat/channels");
        // 一次性迁移老路径 plugins/ArcartXSuite/<channelsRelative> -> data/chat/<channelsRelative>
        File channelsDirectory = context.migrateLegacyDirectory(channelsRelative);
        ensureChannelDefaults(channelsRelative);
        configuration = ChatModuleConfiguration.load(yaml, channelsDirectory, context.logger());
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(COMPLETION_UI_RESOURCE_PATH, COMPLETION_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected void startService() throws Exception {
        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        ArcartXItemStackBridge itemStackBridge = (ArcartXItemStackBridge) context.itemStackBridge();

        // 绑定 @补全 overlay UI
        completionUiId = null;
        if (configuration.functions().mentionEnabled()) {
            File completionUiFile = new File(context.pluginDataFolder(), COMPLETION_UI_FILE_PATH);
            UiBinding completionBinding = context.prepareUiBinding(
                "ChatCompletion", "axs_chat_completion", true, completionUiFile
            );
            if (completionBinding != null && completionBinding.registeredUiId() != null) {
                completionUiId = completionBinding.registeredUiId();
                recordUiBinding(COMPLETION_UI_FILE_PATH, completionBinding);
                context.logger().fine("Chat @补全 UI 已注册: " + completionUiId);
            }
        }

        JdbcChatRepository chatRepo = new JdbcChatRepository(
            context.migrateLegacyDataFile(configuration.storage().sqliteFileName()),
            configuration.storage(), context.logger());
        service = new ChatService(
            context.plugin(),
            () -> context.getCapability(TabRefreshable.class),
            configuration,
            chatRepo,
            packetBridge, itemStackBridge,
            completionUiId,
            context.crossServer()
        );
        service.setEventBusProvider(() -> context.getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start();
        adminCommand = new ChatAdminCommand(() -> service, messages());

        // 注册 ChatCardSendable capability，通过 packetBridge 转发
        ArcartXPacketBridge cardBridge = packetBridge;
        context.registerCapability(ChatCardSendable.class, (player, cardId, data) ->
            cardBridge.sendChatCard(player, cardId, data));

        // 注册 ChatMutable capability，供 Essentials 等模块委托禁言操作
        ChatService muteService = service;
        context.registerCapability(ChatMutable.class, new ChatMutable() {
            @Override
            public @NotNull String mutePlayer(@NotNull String playerName, @Nullable java.time.Instant expiresAt, @Nullable String reason, @Nullable String mutedBy) {
                return muteService.mutePlayer(playerName, expiresAt, reason, mutedBy).message();
            }

            @Override
            public @NotNull String unmutePlayer(@NotNull String playerName) {
                return muteService.unmutePlayer(playerName).message();
            }

            @Override
            public boolean isMuted(@NotNull java.util.UUID playerUuid) {
                var record = muteService.getCachedMute(playerUuid);
                return record != null && record.active(java.time.Instant.now());
            }
        });

        ensureChatCardDefaults();

        context.registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "chat"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return chatRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { context.logger().warning("Chat purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return chatRepo.deleteAllPlayerData(); }
                    catch (Exception e) { context.logger().warning("Chat purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "chat"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return chatRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return chatRepo.getDescriptor();
                }
            });

        context.logger().fine(
            "Chat 模块已载入，频道=" + service.channelCount()
                + " | 存储=" + configuration.storage().dialect().configKey()
                + " | 跨服=" + (service.crossServerActive() ? "ON" : "OFF")
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        completionUiId = null;
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        ChatPlayerCommand chatCmd = new ChatPlayerCommand(() -> service, ChatPlayerCommand.CommandMode.CHAT, messages());
        ChatPlayerCommand msgCmd = new ChatPlayerCommand(() -> service, ChatPlayerCommand.CommandMode.MESSAGE, messages());
        ChatPlayerCommand replyCmd = new ChatPlayerCommand(() -> service, ChatPlayerCommand.CommandMode.REPLY, messages());
        return Map.of(
            "chat", (TabExecutor) chatCmd,
            "msg", (TabExecutor) msgCmd,
            "reply", (TabExecutor) replyCmd
        );
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new ChatPlaceholderExpansion(context.plugin(), () -> service);
    }

    public ChatService getService() {
        return service;
    }

    public ChatModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "chat"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    private void ensureChatCardDefaults() {
        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }
        File chatCardDir = new File(arcartX.getDataFolder(), "chat_card");
        if (!chatCardDir.exists()) {
            chatCardDir.mkdirs();
        }
        String[] cards = {
            "axs_item_preview",
            "axs_chat_mention",
            "axs_chat_private",
            "axs_chat_system"
        };
        for (String card : cards) {
            File target = new File(chatCardDir, card + ".yml");
            if (!target.exists()) {
                context.exportResource("chat/card/" + card + ".yml", target, false);
            }
        }
    }

    private void ensureChannelDefaults(String channelsRelative) {
        File channelsDir = new File(context.dataFolder(), channelsRelative);
        if (!channelsDir.exists()) {
            channelsDir.mkdirs();
        }
        for (String channel : new String[]{"Normal.yml", "Global.yml", "Private.yml", "Staff.yml"}) {
            File target = new File(channelsDir, channel);
            if (!target.exists()) {
                context.exportResource("chat/channels/" + channel, target, false);
            }
        }
    }
}
