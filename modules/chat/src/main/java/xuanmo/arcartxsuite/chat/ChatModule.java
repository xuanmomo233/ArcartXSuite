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
import xuanmo.arcartxsuite.api.BuildEdition;
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
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.chat.command.ChatAdminCommand;
import xuanmo.arcartxsuite.chat.command.ChatPlayerCommand;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.placeholder.ChatPlaceholderExpansion;
import xuanmo.arcartxsuite.chat.service.ChatService;
import xuanmo.arcartxsuite.chat.service.RedPacketService;
import xuanmo.arcartxsuite.chat.storage.JdbcChatRepository;

/**
 * 全频道聊天模块的主入口。
 * <p>
 * 负责加载聊天配置（频道、卡片、过滤器、功能开关）、启动 {@link ChatService}，
 * 注册玩家命令（chat / msg / reply）与管理员命令，绑定 PlaceholderAPI 扩展，
 * 并向宿主注册 {@link ChatCardSendable}、{@link ChatMutable}、
 * {@link xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable}、
 * {@link xuanmo.arcartxsuite.api.capability.DatabaseMigratable} 等跨模块能力。
 */
public final class ChatModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String COMPLETION_UI_RESOURCE_PATH = "chat/ui/axs_chat_completion.yml";
    private static final String COMPLETION_UI_FILE_PATH = "ui/chat/axs_chat_completion.yml";

    private ChatAdminCommand adminCommand;

    private ChatModuleConfiguration configuration;
    private ChatService service;
    private RedPacketService redPacketService;
    private JdbcChatRepository repository;
    private java.util.concurrent.CompletableFuture<String> networkIdentity;
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;
    private volatile xuanmo.arcartxsuite.chat.service.ChatReloadGate reloadGate;
    private String completionUiId;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("chat")
            .name("Chat")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXChat.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("function.custom-components")
                    .obsoletePath("settings.server-id")
                .build())
            .currentVersion(3)
            .validations(List.of(
                    // storage.shared 必须是布尔值
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // pool-size 范围 1-100（仅在 shared=false 时需要）
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(COMPLETION_UI_RESOURCE_PATH, COMPLETION_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, false);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXChat.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String channelsRelative = yaml.getString("channels-directory", "chat/channels");
        File channelsDirectory = new File(dataFolder, channelsRelative);
        ensureChannelDefaults(channelsRelative);
        configuration = ChatModuleConfiguration.load(yaml, channelsDirectory, logger);
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.chat.chat_message_sent");
    }

    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供 SQLite 文件名
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor chatDesc;
        javax.sql.DataSource chatDs;
        if (configuration.storage().hasOverride()) {
            chatDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("chat", null, dataFolder, chatDesc);
            chatDs = storageScope.dataSource();
        } else {
            chatDesc = storageManager.getDescriptor().withTablePrefix("");
            storageScope = storageManager.openModuleStorage("chat",
                configuration.storage().sqliteFileName(), dataFolder, null);
            chatDs = storageScope.dataSource();
        }
        JdbcChatRepository chatRepo = new JdbcChatRepository(chatDs, chatDesc, dataFolder, logger);
        repository = chatRepo;
        var identity = new java.util.concurrent.CompletableFuture<String>();
        networkIdentity = identity;
        return storageScope.initialize(() -> {
            chatRepo.initialize();
            identity.complete(BuildEdition.CROSS_SERVER_INCLUDED && configuration.crossServer().enabled() && advancedCrossServerOrchestrationAllowed()
                ? chatRepo.networkId().orElse(null) : null);
        });
    }

    @Override
    protected void startService() throws Exception {
        completionUiId = null;
        if (configuration.functions().mentionEnabled()) {
            UiBinding completionBinding = registerModuleUi(
                COMPLETION_UI_FILE_PATH, "AXS:axs_chat_completion", true
            );
            if (completionBinding.registeredUiId() != null) {
                completionUiId = completionBinding.registeredUiId();
                logger.fine("Chat @补全 UI 已注册: " + completionUiId);
            }
        }
        var chatRepo = repository;
        service = new ChatService(
            plugin, logger,
            () -> getCapability(TabRefreshable.class),
            configuration,
            chatRepo,
            packetBridge, itemStackBridge,
            completionUiId,
            crossServer,
            placeholderResolver,
            messages(),
            vanillaItemNameBridge,
            contentModerator,
            this::advancedCrossServerOrchestrationAllowed
        );
        service.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setPartyProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.AbyssPartyAccess.class));
        service.setRegionChatProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.RegionChatAccess.class));
        service.setLinks(new xuanmo.arcartxsuite.chat.service.ChatLinks(service, packetBridge,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MapNavigable.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.AxsMarketService.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.AbyssQueryable.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.RegionQueryable.class)));
        service.setReloadGate(reloadGate);
        service.start(networkIdentity.getNow(null));
        networkIdentity = null;

        // 红包服务：仅在共享 MySQL 存储下启用跨服领取
        ensureRedPacketPresetDefaults();
        redPacketService = new RedPacketService(
            plugin, logger, configuration, chatRepo, service,
            packetBridge, currencyManager, pendingRewardService,
            itemSourceRegistry, vanillaItemNameBridge, messages(),
            dataFolder, itemStackBridge, chatRepo.getDescriptor().isMysql()
        );
        service.setRedPacketService(redPacketService);
        redPacketService.start();
        adminCommand = new ChatAdminCommand(() -> service, messages());

        // 注册 ChatCardSendable capability，通过 packetBridge 转发
        PacketBridgeAPI cardBridge = packetBridge;
        registerCapability(ChatCardSendable.class, (player, cardId, data) ->
            cardBridge.sendChatCard(player, cardId, data));
        registerCapability(xuanmo.arcartxsuite.api.capability.ChatExternalRelay.class, service::receiveExternal);
        registerCapability(xuanmo.arcartxsuite.api.capability.ChatModerationAccess.class, service::inspectModeration);

        // 注册 ChatMutable capability，供 Essentials 等模块委托禁言操作
        ChatService muteService = service;
        registerCapability(ChatMutable.class, new ChatMutable() {
            @Override
            public @NotNull java.util.concurrent.CompletionStage<String> mutePlayer(@NotNull String playerName, @Nullable java.time.Instant expiresAt, @Nullable String reason, @Nullable String mutedBy) {
                return muteService.mutePlayer(playerName, expiresAt, reason, mutedBy).thenApply(xuanmo.arcartxsuite.chat.model.ChatOperationResult::message);
            }

            @Override
            public @NotNull java.util.concurrent.CompletionStage<String> unmutePlayer(@NotNull String playerName, @NotNull String operator) {
                return muteService.unmutePlayer(playerName, operator).thenApply(xuanmo.arcartxsuite.chat.model.ChatOperationResult::message);
            }

            @Override
            public @NotNull java.util.concurrent.CompletionStage<Boolean> isMuted(@NotNull java.util.UUID playerUuid) {
                return muteService.queryMute(playerUuid);
            }

            @Override
            public @NotNull java.util.concurrent.CompletionStage<java.util.List<MuteInfo>> listMutes(int limit, int offset) {
                return muteService.activeMutes(limit, offset);
            }
        });

        ensureChatCardDefaults();

        ChatService startedService = service;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "chat"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    if (startedService.isStopping()) return -1;
                    try { return chatRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Chat purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    if (startedService.isStopping()) return -1;
                    try { return chatRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Chat purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "chat"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    if (startedService.isStopping()) {
                        var result = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
                        result.addError("Chat 正在停用");
                        return result;
                    }
                    return chatRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return chatRepo.getDescriptor();
                }
            });

        logger.fine(
            "Chat 模块已载入，频道=" + service.channelCount()
                + " | 存储=" + configuration.storage().mode()
                + " | 跨服=" + (service.crossServerActive() ? "ON" : "OFF")
        );
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> onReload() {
        String text = messages() == null ? "聊天正在重载，刚才的消息未发送，请稍后重试。"
            : messages().get("prefix") + messages().get("result.reloading");
        var gate = new xuanmo.arcartxsuite.chat.service.ChatReloadGate(plugin, text);
        reloadGate = gate;
        if (service != null) service.setReloadGate(gate);
        try {
            gate.start();
            var result = super.onReload();
            result.whenComplete((ignored, failure) -> {
                gate.close();
                if (reloadGate == gate) {
                    reloadGate = null;
                    if (service != null) service.setReloadGate(null);
                }
            });
            return result.toCompletableFuture().copy();
        } catch (RuntimeException | LinkageError failure) {
            gate.close();
            reloadGate = null;
            return java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    protected void stopService() {
        var current = service;
        var rewards = redPacketService;
        var repo = repository;
        var scope = storageScope;
        service = null;
        redPacketService = null;
        repository = null;
        networkIdentity = null;
        storageScope = null;
        try {
            if (rewards != null) rewards.shutdown();
            if (current != null) current.shutdown();
        } finally {
            var workers = java.util.concurrent.CompletableFuture.allOf(
                rewards == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : rewards.databaseStopped().toCompletableFuture(),
                current == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : current.databaseStopped().toCompletableFuture());
            var stopped = workers.thenCompose(ignored -> {
                if (repo == null) return java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
                repo.close();
                return repo.closed();
            });
            trackShutdown(stopped);
            if (scope != null) {
                scope.retireAfter(stopped);
                trackShutdown(scope.retired());
            }
            completionUiId = null;
            configuration = null;
        }
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
        return new ChatPlaceholderExpansion(plugin, () -> service);
    }

    /**
     * 获取当前运行中的聊天服务实例。
     *
     * @return 服务实例，模块未启动或已停止时返回 {@code null}
     */
    public ChatService getService() {
        return service;
    }

    /**
     * 获取当前加载的聊天模块配置。
     *
     * @return 配置实例，模块未启动或已停止时返回 {@code null}
     */
    public ChatModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "chat"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
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
        // 逐文件检查：chat_card 目录可能已有 mail/map 等其他模块卡片，不能整体跳过
        String[] cards = {
            "axs_item_preview",
            "axs_chat_mention",
            "axs_chat_private",
            "axs_chat_system",
            "axs_chat_redpacket",
            "axs_chat_link",
            "axs_chat_party_invite"
        };
        for (String card : cards) {
            File target = new File(chatCardDir, card + ".yml");
            if (!target.exists()) {
                exportResource("chat/card/" + card + ".yml", target, false);
            }
        }
    }

    /** 导出默认红包预设示例（目录为空时）。 */
    private void ensureRedPacketPresetDefaults() {
        if (configuration == null || !configuration.redPacket().enabled()) {
            return;
        }
        File presetsDir = new File(dataFolder, configuration.redPacket().presetsDirectory());
        if (!presetsDir.exists()) {
            presetsDir.mkdirs();
        }
        File[] existing = presetsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        File target = new File(presetsDir, "example.yml");
        if (!target.exists()) {
            exportResource("chat/redpackets/example.yml", target, false);
        }
    }

    private void ensureChannelDefaults(String channelsRelative) {
        File channelsDir = new File(dataFolder, channelsRelative);
        if (!channelsDir.exists()) {
            channelsDir.mkdirs();
        }
        File[] existing = channelsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        File party = new File(channelsDir, "Party.yml");
        if (existing == null || java.util.Arrays.stream(existing).noneMatch(file ->
                file.getName().equalsIgnoreCase("Party.yml") || file.getName().equalsIgnoreCase("Party.yaml"))) {
            exportResource("chat/channels/Party.yml", party, false);
        }
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String channel : new String[]{"Normal.yml", "Global.yml", "Private.yml", "Staff.yml"}) {
            File target = new File(channelsDir, channel);
            if (!target.exists()) {
                exportResource("chat/channels/" + channel, target, false);
            }
        }
    }
}

