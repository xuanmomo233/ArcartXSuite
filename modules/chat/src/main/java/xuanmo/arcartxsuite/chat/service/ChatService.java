package xuanmo.arcartxsuite.chat.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.chat.config.ChatCardConfiguration;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.config.ChatCustomComponent;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatItemPreview;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.chat.service.ChatEnvelopeCodec;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;

public final class ChatService implements Listener {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final String PRIVATE_PERMISSION = "arcartxsuite.chat.msg";
    private static final String SOCIAL_SPY_PERMISSION = "arcartxsuite.chat.socialspy";
    private static final long CLEANUP_PERIOD_TICKS = 1200L;
    private static final long ENVELOPE_TTL_MILLIS = 15000L;

    private final JavaPlugin plugin;
    private final ChatModuleConfiguration configuration;
    private final ChatRepository repository;
    private final PacketBridgeAPI packetBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final ExecutorService ioExecutor;
    private final Map<UUID, ChatPlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ChatPlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, ChatMuteRecord> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, DuplicateStamp> lastDuplicateStamps = new ConcurrentHashMap<>();
    private final Map<String, Long> processedEnvelopeKeys = new ConcurrentHashMap<>();
    private final Set<String> cloudWords = ConcurrentHashMap.newKeySet();
    private final java.util.function.Supplier<TabRefreshable> tabRefreshableProvider;
    private java.util.function.Supplier<EventBusCapability> eventBusProvider;
    private final String completionUiId;
    private final CrossServerAPI crossServer;
    private final PlaceholderResolverAPI placeholderResolver;

    private CrossServerChannel crossServerChannel;
    private BukkitTask cleanupTask;
    private BukkitTask cloudRefreshTask;
    private boolean paperChatEventRegistered;
    private volatile boolean paperChatEventObserved;
    private java.util.function.BiConsumer<Player, Collection<String>> completionAdder;
    private java.util.function.BiConsumer<Player, Collection<String>> completionRemover;

    public ChatService(
        JavaPlugin plugin,
        java.util.function.Supplier<TabRefreshable> tabRefreshableProvider,
        ChatModuleConfiguration configuration,
        ChatRepository repository,
        PacketBridgeAPI packetBridge,
        ItemBridgeAPI itemStackBridge,
        String completionUiId,
        CrossServerAPI crossServer,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = Objects.requireNonNull(plugin);
        this.tabRefreshableProvider = tabRefreshableProvider;
        this.configuration = Objects.requireNonNull(configuration);
        this.repository = Objects.requireNonNull(repository);
        this.packetBridge = Objects.requireNonNull(packetBridge);
        this.itemStackBridge = Objects.requireNonNull(itemStackBridge);
        this.completionUiId = completionUiId;
        this.crossServer = Objects.requireNonNull(crossServer);
        this.placeholderResolver = placeholderResolver;
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AXS-Chat-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setEventBusProvider(java.util.function.Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void start() throws Exception {
        repository.initialize();
        crossServerChannel = crossServer.openChannel(
            "chat",
            configuration.crossServer(),
            delivery -> handleRemotePayload(delivery.payload())
        );
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPaperChatEventIfAvailable();
        initMentionCompletionReflection();
        for (Player player : Bukkit.getOnlinePlayers()) {
            touchProfile(player, true);
            state(player.getUniqueId());
            currentMute(player.getUniqueId());
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runCleanup, CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
        scheduleCloudRefresh();
        broadcastCompletionPlayerList();
    }

    private void publishChatEvent(Player player, String channelId, String type) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("channel_id", channelId);
        payload.put("type", type);
        eventBus.publish("axs.chat.chat_message_sent", player, payload);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (cloudRefreshTask != null) {
            cloudRefreshTask.cancel();
            cloudRefreshTask = null;
        }
        HandlerList.unregisterAll(this);
        paperChatEventRegistered = false;
        paperChatEventObserved = false;
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        repository.close();
        states.clear();
        profiles.clear();
        mutes.clear();
        replyTargets.clear();
        lastMessageTimes.clear();
        lastDuplicateStamps.clear();
        processedEnvelopeKeys.clear();
        cloudWords.clear();
    }

    public ChatModuleConfiguration configuration() {
        return configuration;
    }

    public int channelCount() {
        return configuration.channels().size();
    }

    public int cachedStateCount() {
        return states.size();
    }

    public int cachedProfileCount() {
        return profiles.size();
    }

    public int cachedMuteCount() {
        return mutes.size();
    }

    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    public List<String> channelIds() {
        return new ArrayList<>(configuration.channels().keySet());
    }

    public ChatPlayerState getCachedState(UUID playerUuid) {
        return playerUuid == null ? null : state(playerUuid);
    }

    public ChatMuteRecord getCachedMute(UUID playerUuid) {
        return playerUuid == null ? null : currentMute(playerUuid);
    }

    public String replyTargetName(UUID playerUuid) {
        if (playerUuid == null) {
            return "";
        }
        UUID targetUuid = replyTargets.get(playerUuid);
        if (targetUuid == null) {
            return "";
        }
        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null) {
            return online.getName();
        }
        ChatPlayerProfile profile = profile(targetUuid);
        return profile == null ? "" : profile.lastKnownName();
    }

    public String channelDisplayName(String channelId) {
        ChatChannelDefinition channel = channel(channelId);
        return channel == null ? "" : channel.displayName();
    }

    public Collection<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    public ChatOperationResult handleChatMessage(Player sender, String rawMessage) {
        if (sender == null || !sender.isOnline()) {
            return ChatOperationResult.failure("玩家当前不在线。");
        }
        ChatPlayerState state = state(sender.getUniqueId());
        ChatChannelDefinition channel = channel(state.currentChannelId());
        if (channel == null) {
            channel = channel(configuration.defaultChannelId());
        }
        if (channel == null) {
            return ChatOperationResult.failure("未找到可用聊天频道。");
        }
        if (channel.mode() == ChatChannelMode.PRIVATE) {
            UUID targetUuid = replyTargets.get(sender.getUniqueId());
            if (targetUuid == null) {
                return ChatOperationResult.failure("当前私聊频道没有最近会话对象，请先使用 /msg。");
            }
            ChatPlayerProfile targetProfile = profile(targetUuid);
            if (targetProfile == null) {
                return ChatOperationResult.failure("最近私聊对象不可用，请重新使用 /msg。");
            }
            return dispatchPrivateMessage(sender, targetProfile, rawMessage, channel, false);
        }
        return dispatchChannelMessage(sender, rawMessage, channel);
    }

    public ChatOperationResult changeChannel(Player player, String channelId) {
        ChatChannelDefinition channel = channel(channelId);
        if (channel == null) {
            return ChatOperationResult.failure("未知频道: " + channelId);
        }
        if (!channel.sendPermission().isBlank() && !player.hasPermission(channel.sendPermission())) {
            return ChatOperationResult.failure("你没有权限进入该频道。");
        }
        ChatPlayerState updated = state(player.getUniqueId()).withCurrentChannel(channel.id(), Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        refreshTab(player, "chat-channel");
        return ChatOperationResult.success("当前频道已切换为 " + channel.displayName() + "。");
    }

    public ChatOperationResult setAcceptsPrivate(Player player, Boolean enabled) {
        ChatPlayerState previous = state(player.getUniqueId());
        boolean nextValue = enabled == null ? !previous.acceptsPrivateMessages() : enabled.booleanValue();
        ChatPlayerState updated = previous.withAcceptsPrivateMessages(nextValue, Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        refreshTab(player, "chat-toggle-private");
        return ChatOperationResult.success("私聊接收已" + (nextValue ? "开启" : "关闭") + "。");
    }

    public ChatOperationResult setAcceptsMentions(Player player, Boolean enabled) {
        ChatPlayerState previous = state(player.getUniqueId());
        boolean nextValue = enabled == null ? !previous.acceptsMentions() : enabled.booleanValue();
        ChatPlayerState updated = previous.withAcceptsMentions(nextValue, Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        refreshTab(player, "chat-toggle-mentions");
        return ChatOperationResult.success("提及接收已" + (nextValue ? "开启" : "关闭") + "。");
    }

    public ChatOperationResult setSocialSpy(Player player, Boolean enabled) {
        if (!player.hasPermission(SOCIAL_SPY_PERMISSION)) {
            return ChatOperationResult.failure("你没有权限切换社交监听。");
        }
        ChatPlayerState previous = state(player.getUniqueId());
        boolean nextValue = enabled == null ? !previous.socialSpyEnabled() : enabled.booleanValue();
        ChatPlayerState updated = previous.withSocialSpyEnabled(nextValue, Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        refreshTab(player, "chat-social-spy");
        return ChatOperationResult.success("社交监听已" + (nextValue ? "开启" : "关闭") + "。");
    }

    public ChatOperationResult setSocialSpy(String playerName, boolean enabled, String operatorName) {
        ChatPlayerProfile profile = resolveProfileByName(playerName);
        if (profile == null) {
            return ChatOperationResult.failure("找不到玩家: " + playerName);
        }
        ChatPlayerState previous = state(profile.playerUuid());
        ChatPlayerState updated = previous.withSocialSpyEnabled(enabled, Instant.now());
        states.put(profile.playerUuid(), updated);
        saveStateAsync(updated);
        Player online = Bukkit.getPlayer(profile.playerUuid());
        if (online != null) {
            online.sendMessage(PREFIX + ChatColor.YELLOW + "你的社交监听已被 " + operatorName + (enabled ? " 开启。" : " 关闭。"));
            refreshTab(online, "chat-admin-spy");
        }
        return ChatOperationResult.success("已将 " + profile.lastKnownName() + " 的社交监听设置为 " + enabled + "。");
    }

    public ChatOperationResult ignore(Player player, String targetName) {
        ChatPlayerProfile target = resolveProfileByName(targetName);
        if (target == null) {
            return ChatOperationResult.failure("找不到玩家: " + targetName);
        }
        if (player.getUniqueId().equals(target.playerUuid())) {
            return ChatOperationResult.failure("不能忽略自己。");
        }
        ChatPlayerState previous = state(player.getUniqueId());
        LinkedHashSet<UUID> ignored = new LinkedHashSet<>(previous.ignoredPlayers());
        if (!ignored.add(target.playerUuid())) {
            return ChatOperationResult.failure("你已经忽略了 " + target.lastKnownName() + "。");
        }
        ChatPlayerState updated = previous.withIgnoredPlayers(ignored, Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        saveIgnoredAsync(updated.playerUuid(), updated.ignoredPlayers());
        refreshTab(player, "chat-ignore");
        return ChatOperationResult.success("已忽略 " + target.lastKnownName() + "。");
    }

    public ChatOperationResult unignore(Player player, String targetName) {
        ChatPlayerProfile target = resolveProfileByName(targetName);
        if (target == null) {
            return ChatOperationResult.failure("找不到玩家: " + targetName);
        }
        ChatPlayerState previous = state(player.getUniqueId());
        LinkedHashSet<UUID> ignored = new LinkedHashSet<>(previous.ignoredPlayers());
        if (!ignored.remove(target.playerUuid())) {
            return ChatOperationResult.failure("你没有忽略 " + target.lastKnownName() + "。");
        }
        ChatPlayerState updated = previous.withIgnoredPlayers(ignored, Instant.now());
        states.put(player.getUniqueId(), updated);
        saveStateAsync(updated);
        saveIgnoredAsync(updated.playerUuid(), updated.ignoredPlayers());
        refreshTab(player, "chat-unignore");
        return ChatOperationResult.success("已取消忽略 " + target.lastKnownName() + "。");
    }

    public ChatOperationResult sendPrivateMessage(Player sender, String targetName, String message) {
        ChatChannelDefinition channel = privateChannel();
        if (channel == null) {
            return ChatOperationResult.failure("未配置私聊频道。");
        }
        ChatPlayerProfile target = resolveProfileByName(targetName);
        if (target == null) {
            return ChatOperationResult.failure("找不到玩家: " + targetName);
        }
        return dispatchPrivateMessage(sender, target, message, channel, false);
    }

    public ChatOperationResult reply(Player sender, String message) {
        ChatChannelDefinition channel = privateChannel();
        if (channel == null) {
            return ChatOperationResult.failure("未配置私聊频道。");
        }
        UUID targetUuid = replyTargets.get(sender.getUniqueId());
        if (targetUuid == null) {
            return ChatOperationResult.failure("当前没有可回复的私聊对象。");
        }
        ChatPlayerProfile target = profile(targetUuid);
        if (target == null) {
            return ChatOperationResult.failure("最近私聊对象不存在。");
        }
        return dispatchPrivateMessage(sender, target, message, channel, true);
    }

    public ChatOperationResult mutePlayer(String playerName, Instant expiresAt, String reason, String mutedBy) {
        ChatPlayerProfile target = resolveProfileByName(playerName);
        if (target == null) {
            return ChatOperationResult.failure("找不到玩家: " + playerName);
        }
        ChatMuteRecord record = new ChatMuteRecord(
            target.playerUuid(),
            mutedBy == null ? "" : mutedBy,
            reason == null ? "" : reason,
            Instant.now(),
            expiresAt
        );
        mutes.put(target.playerUuid(), record);
        saveMuteAsync(record);
        Player online = Bukkit.getPlayer(target.playerUuid());
        if (online != null) {
            boolean cardSent = sendSystemCard(
                online,
                "mute",
                Map.of(
                    "message", "你已被禁言",
                    "reason", record.reason().isBlank() ? "无" : record.reason(),
                    "mutedBy", record.mutedBy(),
                    "remaining", record.remainingText(Instant.now())
                )
            );
            if (!cardSent) {
                online.sendMessage(PREFIX + ChatColor.RED + "你已被禁言，时长: " + record.remainingText(Instant.now()) + "。");
            }
            refreshTab(online, "chat-muted");
        }
        return ChatOperationResult.success("已禁言 " + target.lastKnownName() + "。");
    }

    public ChatOperationResult unmutePlayer(String playerName) {
        ChatPlayerProfile target = resolveProfileByName(playerName);
        if (target == null) {
            return ChatOperationResult.failure("找不到玩家: " + playerName);
        }
        mutes.remove(target.playerUuid());
        deleteMuteAsync(target.playerUuid());
        Player online = Bukkit.getPlayer(target.playerUuid());
        if (online != null) {
            online.sendMessage(PREFIX + ChatColor.GREEN + "你的聊天禁言已解除。");
            refreshTab(online, "chat-unmuted");
        }
        return ChatOperationResult.success("已解除 " + target.lastKnownName() + " 的禁言。");
    }


    @EventHandler(ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (configuration.forceChatTakeover()) {
            forceCancelLegacyChatEvent(event);
            return;
        }
        event.setCancelled(true);
        dispatchOrDeferToPaper(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAsyncPlayerChatForceTakeover(AsyncPlayerChatEvent event) {
        if (!configuration.forceChatTakeover()) {
            return;
        }
        forceCancelLegacyChatEvent(event);
        dispatchOrDeferToPaper(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAsyncPlayerChatForceFinalCancel(AsyncPlayerChatEvent event) {
        if (configuration.forceChatTakeover()) {
            forceCancelLegacyChatEvent(event);
        }
    }

    private void dispatchOrDeferToPaper(Player player, String message) {
        if (paperChatEventRegistered) {
            if (!paperChatEventObserved) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!paperChatEventObserved) {
                        scheduleChatMessage(player, message);
                    }
                }, 1L);
            }
            return;
        }
        scheduleChatMessage(player, message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        touchProfile(event.getPlayer(), true);
        state(event.getPlayer().getUniqueId());
        currentMute(event.getPlayer().getUniqueId());
        updateMentionCompletions(event.getPlayer(), true);
        broadcastCompletionPlayerList();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        touchProfile(event.getPlayer(), true);
        UUID uuid = event.getPlayer().getUniqueId();
        states.remove(uuid);
        replyTargets.remove(uuid);
        lastMessageTimes.remove(uuid);
        lastDuplicateStamps.remove(uuid);
        updateMentionCompletions(event.getPlayer(), false);
        broadcastCompletionPlayerList();
    }

    private ChatOperationResult dispatchChannelMessage(Player sender, String rawMessage, ChatChannelDefinition channel) {
        String message = sanitizeAndValidateOutgoingMessage(sender, rawMessage);
        if (message == null) {
            return ChatOperationResult.failure("消息不能为空。");
        }
        ChatOperationResult validation = validateBeforeSend(sender, message, channel, false);
        if (!validation.success()) {
            return validation;
        }

        String processedMessage = applyCustomComponents(message);
        ChatModerationSupport.ModerationResult moderationResult = ChatModerationSupport.applyFilters(
            processedMessage,
            configuration.filter(),
            List.copyOf(cloudWords)
        );
        if (moderationResult.blocked()) {
            boolean cardSent = sendSystemCard(sender, "filter", Map.of("message", "消息包含被禁止内容"));
            return cardSent ? ChatOperationResult.failureCardNotified("消息包含被禁止内容。") : ChatOperationResult.failure("消息包含被禁止内容。");
        }
        processedMessage = moderationResult.message();

        ProcessedContent processedContent = processContent(sender, processedMessage);
        ChatMentionSupport.MentionResult mentionResult = configuration.functions().mentionEnabled()
            ? ChatMentionSupport.parseMentions(processedMessage, onlinePlayerNames(), configuration.functions().mentionAllEnabled())
            : new ChatMentionSupport.MentionResult(false, Set.of());

        Map<String, String> variables = ChatFormatSupport.baseVariables(channel.displayName(), sender, "", processedContent.message());
        String renderedText = ChatFormatSupport.renderTemplate(
            sender,
            channel.format(),
            variables,
            placeholderResolver
        );
        String consoleText = ChatFormatSupport.renderTemplate(
            sender,
            channel.consoleFormat(),
            variables,
            placeholderResolver
        );
        ChatEnvelope envelope = new ChatEnvelope(
            UUID.randomUUID().toString(),
            nodeId(),
            configuration.serverId(),
            channel.id(),
            sender.getUniqueId().toString(),
            sender.getName(),
            nullToEmpty(sender.getDisplayName()),
            "",
            "",
            renderedText,
            renderedText,
            "",
            consoleText,
            false,
            channel.mode() == ChatChannelMode.STAFF,
            mentionResult.mentionAll(),
            new ArrayList<>(mentionResult.mentionedNames()),
            processedContent.itemPreview(),
            processedMessage
        );
        rememberEnvelope(envelope.dedupeKey());
        deliverPublicEnvelope(envelope, channel, sender);
        if (channel.crossServer() && crossServerActive()) {
            crossServerChannel.publish(ChatEnvelopeCodec.encode(envelope));
        }
        trackMessageFingerprint(sender.getUniqueId(), processedMessage);
        publishChatEvent(sender, channel.id(), "channel");
        return ChatOperationResult.success("聊天已发送。");
    }

    private ChatOperationResult dispatchPrivateMessage(
        Player sender,
        ChatPlayerProfile targetProfile,
        String rawMessage,
        ChatChannelDefinition channel,
        boolean replyMode
    ) {
        if (targetProfile == null) {
            return ChatOperationResult.failure("目标玩家不存在。");
        }
        if (sender.getUniqueId().equals(targetProfile.playerUuid())) {
            return ChatOperationResult.failure("不能给自己发送私聊。");
        }
        String message = sanitizeAndValidateOutgoingMessage(sender, rawMessage);
        if (message == null) {
            return ChatOperationResult.failure("消息不能为空。");
        }
        ChatOperationResult validation = validateBeforeSend(sender, message, channel, true);
        if (!validation.success()) {
            return validation;
        }

        Player localTarget = Bukkit.getPlayer(targetProfile.playerUuid());
        if (localTarget != null) {
            ChatPlayerState targetState = state(localTarget.getUniqueId());
            if (!targetState.acceptsPrivateMessages()) {
                return ChatOperationResult.failure("对方当前拒收私聊。");
            }
            if (targetState.ignores(sender.getUniqueId())) {
                return ChatOperationResult.failure("对方已忽略你。");
            }
        } else if (!crossServerActive()) {
            return ChatOperationResult.failure("对方当前不在线。");
        }

        String processedMessage = applyCustomComponents(message);
        ChatModerationSupport.ModerationResult moderationResult = ChatModerationSupport.applyFilters(
            processedMessage,
            configuration.filter(),
            List.copyOf(cloudWords)
        );
        if (moderationResult.blocked()) {
            boolean cardSent = sendSystemCard(sender, "filter", Map.of("message", "消息包含被禁止内容"));
            return cardSent ? ChatOperationResult.failureCardNotified("消息包含被禁止内容。") : ChatOperationResult.failure("消息包含被禁止内容。");
        }
        processedMessage = moderationResult.message();

        ProcessedContent processedContent = processContent(sender, processedMessage);
        Map<String, String> variables = ChatFormatSupport.baseVariables(channel.displayName(), sender, targetProfile.lastKnownName(), processedContent.message());
        String senderText = ChatFormatSupport.renderTemplate(
            sender,
            channel.senderFormat(),
            variables,
            placeholderResolver
        );
        String recipientText = ChatFormatSupport.renderTemplate(
            sender,
            channel.recipientFormat(),
            variables,
            placeholderResolver
        );
        String spyText = ChatFormatSupport.renderTemplate(
            sender,
            channel.spyFormat(),
            variables,
            placeholderResolver
        );
        String consoleText = ChatFormatSupport.renderTemplate(
            sender,
            channel.consoleFormat(),
            variables,
            placeholderResolver
        );
        ChatEnvelope envelope = new ChatEnvelope(
            UUID.randomUUID().toString(),
            nodeId(),
            configuration.serverId(),
            channel.id(),
            sender.getUniqueId().toString(),
            sender.getName(),
            nullToEmpty(sender.getDisplayName()),
            targetProfile.playerUuid().toString(),
            targetProfile.lastKnownName(),
            senderText,
            recipientText,
            spyText,
            consoleText,
            true,
            false,
            false,
            List.of(),
            processedContent.itemPreview(),
            processedMessage
        );
        rememberEnvelope(envelope.dedupeKey());
        deliverPrivateEnvelope(envelope, sender, localTarget);
        if (channel.crossServer() && crossServerActive()) {
            crossServerChannel.publish(ChatEnvelopeCodec.encode(envelope));
        }
        replyTargets.put(sender.getUniqueId(), targetProfile.playerUuid());
        if (localTarget != null) {
            replyTargets.put(localTarget.getUniqueId(), sender.getUniqueId());
        }
        refreshTab(sender, replyMode ? "chat-reply" : "chat-msg");
        if (localTarget != null) {
            refreshTab(localTarget, "chat-msg-target");
        }
        trackMessageFingerprint(sender.getUniqueId(), processedMessage);
        return ChatOperationResult.success("私聊已发送。");
    }

    private ChatOperationResult validateBeforeSend(Player sender, String message, ChatChannelDefinition channel, boolean privateMessage) {
        if (sender == null || !sender.isOnline()) {
            return ChatOperationResult.failure("玩家当前不在线。");
        }
        if (!channel.sendPermission().isBlank() && !sender.hasPermission(channel.sendPermission())) {
            return ChatOperationResult.failure("你没有权限在该频道发言。");
        }
        if (privateMessage && !sender.hasPermission(PRIVATE_PERMISSION)) {
            return ChatOperationResult.failure("你没有权限使用私聊。");
        }
        ChatMuteRecord muteRecord = currentMute(sender.getUniqueId());
        if (muteRecord != null && muteRecord.active(Instant.now())) {
            boolean cardSent = sendSystemCard(
                sender,
                "mute",
                Map.of(
                    "message", "你当前处于禁言状态",
                    "remaining", muteRecord.remainingText(Instant.now()),
                    "reason", muteRecord.reason().isBlank() ? "无" : muteRecord.reason()
                )
            );
            return cardSent
                ? ChatOperationResult.failureCardNotified("你当前处于禁言状态，剩余 " + muteRecord.remainingText(Instant.now()) + "。")
                : ChatOperationResult.failure("你当前处于禁言状态，剩余 " + muteRecord.remainingText(Instant.now()) + "。");
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = configuration.cooldownMillis();
        if (cooldownMillis > 0L) {
            long lastAt = lastMessageTimes.getOrDefault(sender.getUniqueId(), 0L);
            if (now - lastAt < cooldownMillis) {
                return ChatOperationResult.failure("发言过快，请稍后再试。");
            }
        }

        long duplicateWindowMillis = configuration.duplicateWindowMillis();
        if (duplicateWindowMillis > 0L) {
            String normalized = ChatFormatSupport.normalizeForDuplicateCheck(message);
            DuplicateStamp previous = lastDuplicateStamps.get(sender.getUniqueId());
            if (previous != null && previous.normalizedMessage().equals(normalized) && now - previous.atMillis() < duplicateWindowMillis) {
                return ChatOperationResult.failure("请勿重复发送相同内容。");
            }
        }
        return ChatOperationResult.success("ok");
    }

    private String sanitizeAndValidateOutgoingMessage(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) {
            return null;
        }
        String message = rawMessage.trim();
        if (message.isBlank()) {
            return null;
        }
        if (message.length() > configuration.maxLength()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "消息长度不能超过 " + configuration.maxLength() + " 个字符。");
            return null;
        }
        return message;
    }

    private String applyCustomComponents(String message) {
        String current = message == null ? "" : message;
        for (ChatCustomComponent component : configuration.functions().customComponents()) {
            Matcher matcher = component.pattern().matcher(current);
            current = matcher.replaceAll(component.replacement());
        }
        return current;
    }

    private ProcessedContent processContent(Player sender, String message) {
        String current = message;
        ChatItemPreview itemPreview = null;

        if (configuration.functions().itemEnabled() && current.contains(configuration.functions().itemToken())) {
            itemPreview = buildItemPreview(sender);
            if (itemPreview == null) {
                current = current.replace(
                    configuration.functions().itemToken(),
                    ChatFormatSupport.translateColors(configuration.functions().itemFailedFormat())
                );
            } else {
                current = current.replace(configuration.functions().itemToken(), ChatFormatSupport.ITEM_MARKER);
            }
        }
        return new ProcessedContent(current, itemPreview);
    }

    private ChatItemPreview buildItemPreview(Player sender) {
        ItemStack itemStack = sender.getInventory().getItemInMainHand();
        if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() <= 0) {
            return null;
        }
        Optional<String> itemJson = itemStackBridge.itemToJson(itemStack);
        if (itemJson.isEmpty()) {
            return null;
        }
        String itemName = resolveItemName(itemStack);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("item_name", itemName);
        variables.put("item_amount", Integer.toString(itemStack.getAmount()));
        variables.put("item_material", itemStack.getType().getKey().toString());
        String displayText = ChatFormatSupport.renderTemplate(sender, configuration.functions().itemFormat(), variables, placeholderResolver);
        return new ChatItemPreview(
            itemJson.get(),
            displayText,
            itemStack.getType().getKey().toString(),
            itemStack.getAmount()
        );
    }

    private void deliverPublicEnvelope(ChatEnvelope envelope, ChatChannelDefinition channel, Player localSender) {
        UUID senderUuid = parseUuid(envelope.senderUuid());
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!canReceivePublic(channel, localSender, senderUuid, recipient)) {
                continue;
            }
            boolean mentionCardSent = sendMentionCardIfNeeded(recipient, envelope, channel);
            if (!mentionCardSent) {
                if (!sendItemPreviewCard(recipient, envelope, channel)) {
                    sendChatComponents(recipient, envelope.renderedText(), envelope.itemPreview(), true);
                }
            }
        }
        sendConsole(envelope.consoleText(), envelope.itemPreview());
    }

    private void deliverPrivateEnvelope(ChatEnvelope envelope, Player localSender, Player localTarget) {
        if (localSender != null && localSender.isOnline()) {
            boolean cardSent = sendPrivateCard(localSender, envelope, true);
            if (!cardSent) {
                if (!sendItemPreviewCard(localSender, envelope, privateChannel())) {
                    sendChatComponents(localSender, envelope.renderedText(), envelope.itemPreview(), true);
                }
            }
        }

        if (localTarget != null && localTarget.isOnline()) {
            boolean cardSent = sendPrivateCard(localTarget, envelope, false);
            if (!cardSent) {
                if (!sendItemPreviewCard(localTarget, envelope, privateChannel())) {
                    sendChatComponents(localTarget, envelope.renderedTargetText(), envelope.itemPreview(), true);
                }
            }
        }

        UUID senderUuid = parseUuid(envelope.senderUuid());
        UUID targetUuid = parseUuid(envelope.targetUuid());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (senderUuid != null && senderUuid.equals(viewer.getUniqueId())) {
                continue;
            }
            if (targetUuid != null && targetUuid.equals(viewer.getUniqueId())) {
                continue;
            }
            ChatPlayerState state = state(viewer.getUniqueId());
            if (!state.socialSpyEnabled() || !viewer.hasPermission(SOCIAL_SPY_PERMISSION)) {
                continue;
            }
            sendChatComponents(viewer, envelope.renderedSpyText(), envelope.itemPreview(), true);
        }
        sendConsole(envelope.consoleText(), envelope.itemPreview());
    }

    private void handleRemotePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            handleRemoteEnvelope(ChatEnvelopeCodec.decode(payload));
        } catch (Exception exception) {
            plugin.getLogger().warning("解析跨服聊天消息失败: " + exception.getMessage());
        }
    }

    private void handleRemoteEnvelope(ChatEnvelope envelope) {
        if (envelope == null || !rememberEnvelope(envelope.dedupeKey())) {
            return;
        }
        ChatChannelDefinition channel = channel(envelope.channelId());
        if (channel == null) {
            return;
        }
        if (envelope.privateMessage()) {
            Player localTarget = parseUuid(envelope.targetUuid()) == null ? null : Bukkit.getPlayer(parseUuid(envelope.targetUuid()));
            if (localTarget != null) {
                ChatPlayerState targetState = state(localTarget.getUniqueId());
                UUID senderUuid = parseUuid(envelope.senderUuid());
                if (!targetState.acceptsPrivateMessages() || (senderUuid != null && targetState.ignores(senderUuid))) {
                    localTarget = null;
                }
            }
            deliverPrivateEnvelope(envelope, null, localTarget);
            if (localTarget != null) {
                replyTargets.put(localTarget.getUniqueId(), parseUuid(envelope.senderUuid()));
                refreshTab(localTarget, "chat-remote-msg");
            }
            return;
        }
        deliverPublicEnvelope(envelope, channel, null);
    }

    private void registerPaperChatEventIfAvailable() {
        try {
            Class<?> eventClass = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            if (!Event.class.isAssignableFrom(eventClass)) {
                return;
            }
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            var getPlayerMethod = eventClass.getMethod("getPlayer");
            var messageMethod = eventClass.getMethod("message");
            var setCancelledMethod = eventClass.getMethod("setCancelled", boolean.class);
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            var serializeMethod = serializerClass.getMethod("serialize", componentClass);

            @SuppressWarnings("unchecked")
            Class<? extends Event> typedEventClass = (Class<? extends Event>) eventClass;
            EventPriority priority = configuration.forceChatTakeover() ? EventPriority.LOWEST : EventPriority.HIGHEST;
            boolean ignoreCancelled = !configuration.forceChatTakeover();
            Bukkit.getPluginManager().registerEvent(
                typedEventClass,
                this,
                priority,
                (listener, event) -> handlePaperChatEvent(
                    event,
                    eventClass,
                    getPlayerMethod,
                    messageMethod,
                    setCancelledMethod,
                    serializer,
                    serializeMethod
                ),
                plugin,
                ignoreCancelled
            );
            if (configuration.forceChatTakeover()) {
                Bukkit.getPluginManager().registerEvent(
                    typedEventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> forceCancelPaperChatEvent(event, eventClass, setCancelledMethod),
                    plugin,
                    false
                );
            }
            paperChatEventRegistered = true;
        } catch (ClassNotFoundException ignored) {
            paperChatEventRegistered = false;
        } catch (ReflectiveOperationException exception) {
            paperChatEventRegistered = false;
            plugin.getLogger().warning("注册 Paper AsyncChatEvent 监听失败，已回退到 Bukkit 旧聊天事件: " + exception.getMessage());
        }
    }

    private void handlePaperChatEvent(
        Event event,
        Class<?> eventClass,
        java.lang.reflect.Method getPlayerMethod,
        java.lang.reflect.Method messageMethod,
        java.lang.reflect.Method setCancelledMethod,
        Object serializer,
        java.lang.reflect.Method serializeMethod
    ) {
        if (!eventClass.isInstance(event)) {
            return;
        }
        try {
            Object rawPlayer = getPlayerMethod.invoke(event);
            Object rawMessage = messageMethod.invoke(event);
            if (!(rawPlayer instanceof Player player) || rawMessage == null) {
                return;
            }
            String message = String.valueOf(serializeMethod.invoke(serializer, rawMessage));
            setCancelledMethod.invoke(event, true);
            paperChatEventObserved = true;
            scheduleChatMessage(player, message);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("处理 Paper AsyncChatEvent 失败: " + exception.getMessage());
        }
    }

    private void forceCancelPaperChatEvent(
        Event event,
        Class<?> eventClass,
        java.lang.reflect.Method setCancelledMethod
    ) {
        if (!configuration.forceChatTakeover() || !eventClass.isInstance(event)) {
            return;
        }
        try {
            setCancelledMethod.invoke(event, true);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("强制取消 Paper AsyncChatEvent 失败: " + exception.getMessage());
        }
    }

    private void forceCancelLegacyChatEvent(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        try {
            event.getRecipients().clear();
        } catch (UnsupportedOperationException ignored) {
        } catch (Exception exception) {
            if (configuration.debug()) {
                plugin.getLogger().warning("清空 Bukkit 聊天接收者失败: " + exception.getMessage());
            }
        }
    }

    private void initMentionCompletionReflection() {
        if (!configuration.functions().mentionEnabled()) {
            return;
        }
        // Strategy 1: Paper / Purpur API
        try {
            java.lang.reflect.Method addMethod = Player.class.getMethod("addCustomChatCompletions", Collection.class);
            java.lang.reflect.Method removeMethod = Player.class.getMethod("removeCustomChatCompletions", Collection.class);
            completionAdder = (p, entries) -> { try { addMethod.invoke(p, entries); } catch (Exception ignored) {} };
            completionRemover = (p, entries) -> { try { removeMethod.invoke(p, entries); } catch (Exception ignored) {} };
            plugin.getLogger().info("[Chat] @补全: 已启用 (Paper API)");
            return;
        } catch (NoSuchMethodException ignored) {}

        // Strategy 2: NMS ClientboundCustomChatCompletionsPacket (Spigot / Mohist 1.19.1+)
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket");
            Class<?> actionEnum = null;
            for (Class<?> inner : packetClass.getDeclaredClasses()) {
                if (inner.isEnum()) { actionEnum = inner; break; }
            }
            if (actionEnum == null) throw new ClassNotFoundException("Action enum not found in " + packetClass.getName());
            Object[] actions = actionEnum.getEnumConstants();
            Object addAction = actions[0];   // ADD
            Object removeAction = actions[1]; // REMOVE
            java.lang.reflect.Constructor<?> packetCtor = packetClass.getConstructor(actionEnum, List.class);

            // Resolve send path: CraftPlayer.getHandle() -> ServerPlayer.connection -> send(Packet)
            Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
            java.lang.reflect.Method getHandle = findCraftPlayerGetHandle();
            if (getHandle == null) throw new ReflectiveOperationException("CraftPlayer.getHandle() not found");
            NmsPacketSender sender = resolveNmsSender(getHandle, packetInterface);

            completionAdder = (p, entries) -> {
                try { sender.send(p, packetCtor.newInstance(addAction, List.copyOf(entries))); } catch (Exception ignored) {}
            };
            completionRemover = (p, entries) -> {
                try { sender.send(p, packetCtor.newInstance(removeAction, List.copyOf(entries))); } catch (Exception ignored) {}
            };
            plugin.getLogger().info("[Chat] @补全: 已启用 (NMS 发包)");
        } catch (Exception ex) {
            plugin.getLogger().warning("[Chat] @补全: 不可用 (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
        }
    }

    @FunctionalInterface
    private interface NmsPacketSender {
        void send(Player player, Object packet) throws Exception;
    }

    private static java.lang.reflect.Method findCraftPlayerGetHandle() {
        // Derive CraftBukkit package from the server class (works even with no players online)
        // e.g. org.bukkit.craftbukkit.v1_20_R1.CraftServer → org.bukkit.craftbukkit.v1_20_R1
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();
            String pkg = serverClass.getPackage().getName();
            // On 1.20.5+ Paper the package may be flat "org.bukkit.craftbukkit" (no version suffix)
            Class<?> craftPlayer = Class.forName(pkg + ".entity.CraftPlayer");
            return craftPlayer.getMethod("getHandle");
        } catch (Exception ignored) {}
        // Fallback: try from an online player instance
        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                return online.getClass().getMethod("getHandle");
            } catch (NoSuchMethodException ignored) {}
            break;
        }
        return null;
    }

    private static NmsPacketSender resolveNmsSender(java.lang.reflect.Method getHandle, Class<?> packetInterface) throws Exception {
        // Resolve connection field and send method from ServerPlayer class
        Class<?> serverPlayerClass = getHandle.getReturnType();

        // Find connection field — try known names, then search by type
        java.lang.reflect.Field connectionField = null;
        for (String name : new String[]{"connection", "c", "b"}) {
            try { connectionField = serverPlayerClass.getField(name); break; } catch (NoSuchFieldException ignored) {}
        }
        if (connectionField == null) {
            // Search by type name pattern (ServerGamePacketListenerImpl / PlayerConnection)
            for (java.lang.reflect.Field f : serverPlayerClass.getFields()) {
                String typeName = f.getType().getSimpleName();
                if (typeName.contains("PacketListener") || typeName.contains("PlayerConnection") || typeName.contains("ServerGamePacket")) {
                    connectionField = f;
                    break;
                }
            }
        }
        if (connectionField == null) throw new NoSuchFieldException("connection field not found on " + serverPlayerClass.getName());

        // Find send method — try known names
        java.lang.reflect.Method sendMethod = null;
        for (String name : new String[]{"send", "a", "sendPacket"}) {
            try { sendMethod = connectionField.getType().getMethod(name, packetInterface); break; } catch (NoSuchMethodException ignored) {}
        }
        if (sendMethod == null) {
            // Search by signature: single Packet parameter, void return
            for (java.lang.reflect.Method m : connectionField.getType().getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && packetInterface.isAssignableFrom(params[0]) && m.getReturnType() == void.class) {
                    sendMethod = m;
                    break;
                }
            }
        }
        if (sendMethod == null) throw new NoSuchMethodException("send(Packet) not found on " + connectionField.getType().getName());

        java.lang.reflect.Field connField = connectionField;
        java.lang.reflect.Method sndMethod = sendMethod;
        return (player, packet) -> {
            Object handle = getHandle.invoke(player);
            Object conn = connField.get(handle);
            sndMethod.invoke(conn, packet);
        };
    }

    private void updateMentionCompletions(Player player, boolean join) {
        if (completionAdder == null || completionRemover == null) {
            return;
        }
        // Delay 1 tick: client may not be ready to receive chat completions during PlayerJoinEvent
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (join) {
                    if (!player.isOnline()) return;
                    Collection<String> newEntry = List.of("@" + player.getName());
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.getUniqueId().equals(player.getUniqueId())) {
                            completionAdder.accept(online, newEntry);
                        }
                    }
                    List<String> existing = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.getUniqueId().equals(player.getUniqueId())) {
                            existing.add("@" + online.getName());
                        }
                    }
                    if (!existing.isEmpty()) {
                        completionAdder.accept(player, existing);
                    }
                } else {
                    Collection<String> removeEntry = List.of("@" + player.getName());
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.getUniqueId().equals(player.getUniqueId())) {
                            completionRemover.accept(online, removeEntry);
                        }
                    }
                }
            } catch (Exception ex) {
                if (configuration.debug()) {
                    plugin.getLogger().warning("[Chat] @补全更新失败: " + ex.getMessage());
                }
            }
        }, 5L);
    }

    private void broadcastCompletionPlayerList() {
        if (completionUiId == null || completionUiId.isBlank()) {
            return;
        }
        if (!configuration.functions().mentionEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    packetBridge.sendPacket(online, completionUiId, "updatePlayers", names);
                }
            } catch (Exception ex) {
                if (configuration.debug()) {
                    plugin.getLogger().warning("[Chat] @补全玩家列表广播失败: " + ex.getMessage());
                }
            }
        }, 5L);
    }

    private void scheduleChatMessage(Player player, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ChatOperationResult result = handleChatMessage(player, message);
            if (!result.success() && !result.cardNotified()) {
                player.sendMessage(PREFIX + ChatColor.RED + result.message());
            }
        });
    }

    private boolean canReceivePublic(ChatChannelDefinition channel, Player localSender, UUID senderUuid, Player recipient) {
        if (recipient == null || !recipient.isOnline()) {
            return false;
        }
        if (senderUuid != null && senderUuid.equals(recipient.getUniqueId())) {
            return true;
        }
        if (!channel.receivePermission().isBlank() && !recipient.hasPermission(channel.receivePermission())) {
            return false;
        }
        if (senderUuid != null && state(recipient.getUniqueId()).ignores(senderUuid)) {
            return false;
        }
        if (localSender != null && channel.mode() == ChatChannelMode.NORMAL && channel.range() > 0.0D) {
            if (!Objects.equals(localSender.getWorld(), recipient.getWorld())) {
                return false;
            }
            return recipient.getLocation().distanceSquared(localSender.getLocation()) <= channel.range() * channel.range();
        }
        return true;
    }

    private boolean sendMentionCardIfNeeded(Player recipient, ChatEnvelope envelope, ChatChannelDefinition channel) {
        if (recipient == null || envelope.privateMessage()) {
            return false;
        }
        ChatPlayerState recipientState = state(recipient.getUniqueId());
        if (!recipientState.acceptsMentions()) {
            return false;
        }
        boolean mentioned = envelope.mentionAll();
        if (!mentioned) {
            for (String name : envelope.mentionedNames()) {
                if (recipient.getName().equalsIgnoreCase(name)) {
                    mentioned = true;
                    break;
                }
            }
        }
        if (!mentioned) {
            return false;
        }
        String cardId = configuration.cards().mentionCardId();
        if (cardId.isBlank()) {
            return false;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("channel", channel.displayName());
        payload.put("channelId", channel.id());
        CardLayout layout = computeCardLayout(envelope.rawMessage());
        for (int i = 0; i < layout.lines().size(); i++) {
            payload.put("msg" + i, layout.lines().get(i));
        }
        payload.put("cardWidth", String.valueOf(layout.cardWidth()));
        payload.put("cardHeight", String.valueOf(layout.cardHeight()));
        return packetBridge.sendChatCard(recipient, cardId, payload);
    }

    private boolean sendPrivateCard(Player recipient, ChatEnvelope envelope, boolean senderSide) {
        String cardId = configuration.cards().privateCardId();
        if (cardId.isBlank()) {
            return false;
        }
        CardLayout layout = computeCardLayout(envelope.rawMessage());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("targetName", envelope.targetName());
        payload.put("direction", senderSide ? "sender" : "recipient");
        for (int i = 0; i < layout.lines().size(); i++) {
            payload.put("msg" + i, layout.lines().get(i));
        }
        payload.put("cardWidth", String.valueOf(layout.cardWidth()));
        payload.put("cardHeight", String.valueOf(layout.cardHeight()));
        return packetBridge.sendChatCard(recipient, cardId, payload);
    }

    private boolean sendItemPreviewCard(Player recipient, ChatEnvelope envelope, ChatChannelDefinition channel) {
        if (recipient == null || envelope.itemPreview() == null || envelope.itemPreview().itemJson().isBlank()) {
            return false;
        }
        String cardId = configuration.cards().itemPreviewCardId();
        if (cardId.isBlank()) {
            return false;
        }
        String itemName = ChatColor.stripColor(envelope.itemPreview().displayText());
        String senderLine = envelope.senderDisplayName() + " 展示了一件物品";
        String longestLine = senderLine.length() >= itemName.length() ? senderLine : itemName;
        CardLayout layout = computeCardLayout(longestLine);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("cardWidth", String.valueOf(layout.cardWidth()));
        payload.put("cardHeight", String.valueOf(layout.cardHeight()));
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("itemName", itemName);
        payload.put("itemAmount", Integer.toString(envelope.itemPreview().amount()));
        payload.put("itemMaterial", envelope.itemPreview().materialKey());
        payload.put("channel", channel == null ? envelope.channelId() : channel.displayName());
        payload.put("itemJson", envelope.itemPreview().itemJson());
        return packetBridge.sendChatCard(recipient, cardId, payload);
    }

    private boolean sendSystemCard(Player recipient, String type, Map<String, String> values) {
        if (recipient == null) {
            return false;
        }
        String cardId = configuration.cards().systemCardId();
        if (cardId.isBlank()) {
            return false;
        }
        Map<String, String> payload = new LinkedHashMap<>(values);
        payload.put("type", type);
        String widthText;
        if ("mute".equals(type)) {
            String remaining = values.getOrDefault("remaining", "永久");
            widthText = "剩余时间: " + remaining;
        } else {
            widthText = values.getOrDefault("message", "内容违规");
        }
        CardLayout layout = computeCardLayout(widthText);
        payload.put("cardWidth", String.valueOf(layout.cardWidth()));
        payload.put("cardHeight", String.valueOf(layout.cardHeight()));
        return packetBridge.sendChatCard(recipient, cardId, payload);
    }

    private void sendChatComponents(Player recipient, String renderedText, ChatItemPreview itemPreview, boolean hoverItem) {
        recipient.spigot().sendMessage(ChatFormatSupport.buildComponents(renderedText, itemPreview, hoverItem));
    }

    private void sendConsole(String consoleText, ChatItemPreview itemPreview) {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(ChatColor.stripColor(ChatFormatSupport.plainText(consoleText, itemPreview)));
    }

    private ChatChannelDefinition channel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return configuration.channel(configuration.defaultChannelId());
        }
        return configuration.channel(channelId);
    }

    private ChatChannelDefinition privateChannel() {
        ChatChannelDefinition exact = configuration.channel("private");
        if (exact != null) {
            return exact;
        }
        for (ChatChannelDefinition definition : configuration.channels().values()) {
            if (definition.mode() == ChatChannelMode.PRIVATE) {
                return definition;
            }
        }
        return null;
    }

    private ChatPlayerState state(UUID playerUuid) {
        if (playerUuid == null) {
            return ChatPlayerState.createDefault(new UUID(0L, 0L), configuration.defaultChannelId());
        }
        ChatPlayerState cached = states.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        try {
            ChatPlayerState loaded = repository.loadState(playerUuid, configuration.defaultChannelId());
            states.put(playerUuid, loaded);
            return loaded;
        } catch (SQLException exception) {
            plugin.getLogger().warning("读取聊天状态失败: " + exception.getMessage());
            ChatPlayerState fallback = ChatPlayerState.createDefault(playerUuid, configuration.defaultChannelId());
            states.put(playerUuid, fallback);
            return fallback;
        }
    }

    private ChatMuteRecord currentMute(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        ChatMuteRecord cached = mutes.get(playerUuid);
        if (cached != null) {
            if (!cached.active(Instant.now())) {
                mutes.remove(playerUuid);
                deleteMuteAsync(playerUuid);
                return null;
            }
            return cached;
        }
        try {
            Optional<ChatMuteRecord> loaded = repository.loadMute(playerUuid);
            if (loaded.isEmpty()) {
                return null;
            }
            ChatMuteRecord record = loaded.get();
            if (!record.active(Instant.now())) {
                deleteMuteAsync(playerUuid);
                return null;
            }
            mutes.put(playerUuid, record);
            return record;
        } catch (SQLException exception) {
            plugin.getLogger().warning("读取聊天禁言状态失败: " + exception.getMessage());
            return null;
        }
    }

    private ChatPlayerProfile profile(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            ChatPlayerProfile profile = new ChatPlayerProfile(playerUuid, online.getName(), Instant.now(), configuration.serverId());
            profiles.put(playerUuid, profile);
            return profile;
        }
        ChatPlayerProfile cached = profiles.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<ChatPlayerProfile> loaded = repository.loadProfile(playerUuid);
            loaded.ifPresent(value -> profiles.put(playerUuid, value));
            return loaded.orElse(null);
        } catch (SQLException exception) {
            plugin.getLogger().warning("读取聊天玩家档案失败: " + exception.getMessage());
            return null;
        }
    }

    private ChatPlayerProfile resolveProfileByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            ChatPlayerProfile profile = new ChatPlayerProfile(
                online.getUniqueId(),
                online.getName(),
                Instant.now(),
                configuration.serverId()
            );
            profiles.put(profile.playerUuid(), profile);
            return profile;
        }
        for (ChatPlayerProfile profile : profiles.values()) {
            if (profile.lastKnownName().equalsIgnoreCase(playerName)) {
                return profile;
            }
        }
        try {
            Optional<ChatPlayerProfile> loaded = repository.findProfileByName(playerName);
            loaded.ifPresent(value -> profiles.put(value.playerUuid(), value));
            if (loaded.isPresent()) {
                return loaded.get();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("按名称查找聊天玩家档案失败: " + exception.getMessage());
        }
        for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer == null || offlinePlayer.getUniqueId() == null || offlinePlayer.getName() == null) {
                continue;
            }
            if (!offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                continue;
            }
            ChatPlayerProfile profile = new ChatPlayerProfile(
                offlinePlayer.getUniqueId(),
                offlinePlayer.getName(),
                Instant.now(),
                ""
            );
            profiles.put(profile.playerUuid(), profile);
            saveProfileAsync(profile);
            return profile;
        }
        return null;
    }

    private void touchProfile(Player player, boolean persist) {
        if (player == null) {
            return;
        }
        ChatPlayerProfile profile = new ChatPlayerProfile(
            player.getUniqueId(),
            player.getName(),
            Instant.now(),
            configuration.serverId()
        );
        profiles.put(profile.playerUuid(), profile);
        if (persist) {
            saveProfileAsync(profile);
        }
    }

    private void runCleanup() {
        long now = System.currentTimeMillis();
        processedEnvelopeKeys.entrySet().removeIf(entry -> now - entry.getValue() > ENVELOPE_TTL_MILLIS);
        lastDuplicateStamps.entrySet().removeIf(entry -> now - entry.getValue().atMillis() > Math.max(ENVELOPE_TTL_MILLIS, configuration.duplicateWindowMillis()));
        lastMessageTimes.entrySet().removeIf(entry -> now - entry.getValue() > Math.max(ENVELOPE_TTL_MILLIS, configuration.cooldownMillis() + ENVELOPE_TTL_MILLIS));
        for (Map.Entry<UUID, ChatMuteRecord> entry : new ArrayList<>(mutes.entrySet())) {
            if (!entry.getValue().active(Instant.now())) {
                mutes.remove(entry.getKey());
                deleteMuteAsync(entry.getKey());
            }
        }
        // 清理离线玩家的 profile 缓存，保留在线玩家
        profiles.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }

    private boolean rememberEnvelope(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return false;
        }
        return processedEnvelopeKeys.putIfAbsent(dedupeKey, System.currentTimeMillis()) == null;
    }

    private void trackMessageFingerprint(UUID playerUuid, String message) {
        long now = System.currentTimeMillis();
        lastMessageTimes.put(playerUuid, now);
        lastDuplicateStamps.put(playerUuid, new DuplicateStamp(ChatFormatSupport.normalizeForDuplicateCheck(message), now));
    }

    private void scheduleCloudRefresh() {
        if (!configuration.filter().cloudEnabled() || configuration.filter().cloudUrl().isBlank()) {
            return;
        }
        long periodTicks = Math.max(1200L, configuration.filter().cloudRefreshMinutes() * 60L * 20L);
        cloudRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshCloudWords, 20L, periodTicks);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshCloudWords);
    }

    private void refreshCloudWords() {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(URI.create(configuration.filter().cloudUrl()).toURL().openStream(), StandardCharsets.UTF_8)
        )) {
            LinkedHashSet<String> loaded = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = line.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isBlank() && !normalized.startsWith("#")) {
                    loaded.add(normalized);
                }
            }
            cloudWords.clear();
            cloudWords.addAll(loaded);
            if (configuration.debug()) {
                plugin.getLogger().info("ArcartXChat 已刷新云敏感词，共 " + loaded.size() + " 条。");
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("刷新聊天云敏感词失败: " + exception.getMessage());
        }
    }

    private void saveStateAsync(ChatPlayerState state) {
        ioExecutor.execute(() -> {
            try {
                repository.saveState(state);
            } catch (SQLException exception) {
                plugin.getLogger().warning("保存聊天状态失败: " + exception.getMessage());
            }
        });
    }

    private void saveIgnoredAsync(UUID playerUuid, Set<UUID> ignoredPlayers) {
        ioExecutor.execute(() -> {
            try {
                repository.saveIgnoredPlayers(playerUuid, ignoredPlayers);
            } catch (SQLException exception) {
                plugin.getLogger().warning("保存聊天忽略列表失败: " + exception.getMessage());
            }
        });
    }

    private void saveMuteAsync(ChatMuteRecord muteRecord) {
        ioExecutor.execute(() -> {
            try {
                repository.saveMute(muteRecord);
            } catch (SQLException exception) {
                plugin.getLogger().warning("保存聊天禁言状态失败: " + exception.getMessage());
            }
        });
    }

    private void deleteMuteAsync(UUID playerUuid) {
        ioExecutor.execute(() -> {
            try {
                repository.deleteMute(playerUuid);
            } catch (SQLException exception) {
                plugin.getLogger().warning("删除聊天禁言状态失败: " + exception.getMessage());
            }
        });
    }

    private void saveProfileAsync(ChatPlayerProfile profile) {
        ioExecutor.execute(() -> {
            try {
                repository.upsertProfile(profile);
            } catch (SQLException exception) {
                plugin.getLogger().warning("保存聊天玩家档案失败: " + exception.getMessage());
            }
        });
    }

    private void refreshTab(Player viewer, String reason) {
        TabRefreshable tabRefreshable = tabRefreshableProvider == null ? null : tabRefreshableProvider.get();
        if (tabRefreshable == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        tabRefreshable.requestViewerRefresh(viewer, reason);
    }

    private String nodeId() {
        if (crossServerChannel != null && crossServerChannel.isActive()) {
            return crossServer.nodeId();
        }
        return configuration.serverId();
    }

    private static UUID parseUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String resolveItemName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return ChatColor.stripColor(itemMeta.getDisplayName());
        }
        String raw = itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(raw.length());
        boolean upper = true;
        for (char character : raw.toCharArray()) {
            if (upper) {
                builder.append(Character.toUpperCase(character));
                upper = false;
            } else {
                builder.append(character);
            }
            if (character == ' ') {
                upper = true;
            }
        }
        return builder.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ProcessedContent(String message, ChatItemPreview itemPreview) {
    }

    private record DuplicateStamp(String normalizedMessage, long atMillis) {
    }

    private record CardLayout(int cardWidth, int cardHeight, List<String> lines) {}

    /**
     * 计算卡片布局：先扩宽度适配文字，超过 maxLineWidth 后换行扩高度。
     * 所有尺寸参数均来自 config，Java 只做加法。
     */
    private CardLayout computeCardLayout(String text) {
        ChatCardConfiguration c = configuration.cards();
        int textWidth = measureTextWidth(text, c.charWidthFull(), c.charWidthHalf());
        int maxLineWidth = c.maxLineWidth();

        if (textWidth <= maxLineWidth) {
            // 单行：卡片宽度 = textOffsetX + 文字宽度 + padRight
            int cardWidth = Math.max(c.minWidth(), c.textOffsetX() + textWidth + c.padRight());
            return new CardLayout(cardWidth, c.baseHeight(), List.of(text));
        }
        // 多行：固定宽度 = textOffsetX + maxLineWidth + padRight，换行扩高
        int cardWidth = Math.max(c.minWidth(), c.textOffsetX() + maxLineWidth + c.padRight());
        List<String> wrappedLines = wrapText(text, maxLineWidth, c.charWidthFull(), c.charWidthHalf());
        int cardHeight = c.baseHeight() + (wrappedLines.size() - 1) * c.lineHeight();
        return new CardLayout(cardWidth, cardHeight, wrappedLines);
    }

    /**
     * 测量文本总宽度：全角字符用 charWidthFull，半角用 charWidthHalf，跳过颜色代码。
     */
    static int measureTextWidth(String text, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                i++;
                continue;
            }
            width += isFullWidth(c) ? charWidthFull : charWidthHalf;
        }
        return width;
    }

    /**
     * 将文本按 maxLineWidth 换行，返回各行列表。
     * 换行时保留当前生效的颜色代码，确保每行开头颜色连续。
     */
    static List<String> wrapText(String text, int maxLineWidth, int charWidthFull, int charWidthHalf) {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;
        String lastColor = "";

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                lastColor = String.valueOf(c) + text.charAt(i + 1);
                currentLine.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            int cw = isFullWidth(c) ? charWidthFull : charWidthHalf;
            if (currentWidth + cw > maxLineWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(lastColor);
                currentWidth = 0;
            }
            currentLine.append(c);
            currentWidth += cw;
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static boolean isFullWidth(char c) {
        return c >= '\u2E80';
    }
}

