package xuanmo.arcartxsuite.chat.service;

import java.io.File;
import java.sql.SQLException;
import java.sql.SQLTransientException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import net.md_5.bungee.api.chat.BaseComponent;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.chat.config.ChatCardConfiguration;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.config.ChatCustomComponent;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatItemPreview;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.StateField;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.RouteState;
import xuanmo.arcartxsuite.api.capability.AbyssPartyAccess;
import xuanmo.arcartxsuite.api.capability.ChatMutable.MuteInfo;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.chat.service.ChatEnvelopeCodec;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.RegionChatAccess;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import java.util.logging.Logger;

public final class ChatService implements Listener {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final String PRIVATE_PERMISSION = "arcartxsuite.chat.msg";
    private static final String SOCIAL_SPY_PERMISSION = "arcartxsuite.chat.socialspy";
    private static final long CLEANUP_PERIOD_TICKS = 1200L;
    private static final long ENVELOPE_TTL_MILLIS = 15000L;
    private static final CompletionStage<Boolean> RELAY_HANDLED = CompletableFuture.completedFuture(true);

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ChatModuleConfiguration configuration;
    private final ChatRepository repository;
    private final ChatModeration moderation;
    private final PacketBridgeAPI packetBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final ContentModerator contentModerator;
    private final ModuleExecutor ioExecutor;
    private volatile ChatReloadGate reloadGate;
    private CompletionStage<Void> databaseStopped = CompletableFuture.completedFuture(null);
    private final Semaphore pendingIgnoreCommands = new Semaphore(32);
    private volatile boolean stopping;
    private final Semaphore moderationSlots = new Semaphore(64);
    private final Semaphore moderationReadSlots = new Semaphore(16);
    private final Set<CompletableFuture<?>> moderationResults = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> moderationMainResults = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> pendingMuteWrites = new ConcurrentHashMap<>();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback moderationCallbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});
    private final Set<UUID> ignoreWritesPending = ConcurrentHashMap.newKeySet();
    private final ChatDeliveryQueue publicDelivery;
    private final boolean guardRemoteIgnores;
    private final java.util.concurrent.atomic.AtomicLong deliveryPolicyVersion = new java.util.concurrent.atomic.AtomicLong();
    private final Map<UUID, Long> channelChanges = new ConcurrentHashMap<>();
    private final Map<UUID, ChatConnection> admittedAudience = new LinkedHashMap<>();
    private volatile ChatAudience publishedAudience = ChatAudience.EMPTY;
    private final Map<UUID, ChatPlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ChatPlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, ChatMuteRecord> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, ReplyTarget> replyTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, DuplicateStamp> lastDuplicateStamps = new ConcurrentHashMap<>();
    private final Map<String, Long> processedEnvelopeKeys = new ConcurrentHashMap<>();
    private final Runnable expiredMuteCleanup = this::deleteExpiredMutes;
    private final Map<String, CompletableFuture<Boolean>> remoteInFlight = new LinkedHashMap<>();
    private final Set<CompletableFuture<Boolean>> externalResults = new LinkedHashSet<>();
    // 加载失败的玩家集合：这些玩家的缓存数据不可信，saveStateAsync 必须跳过，避免默认状态覆盖 DB 真实数据
    private final java.util.function.Supplier<TabRefreshable> tabRefreshableProvider;
    private java.util.function.Supplier<EventBusCapability> eventBusProvider;
    private final String completionUiId;
    private final CrossServerAPI crossServer;
    private final PlaceholderResolverAPI placeholderResolver;
    private final MessageProvider messages;
    private final VanillaItemNameBridge vanillaItemNameBridge;

    private CrossServerChannel crossServerChannel;
    private ChatRelay chatRelay;
    private String networkId;
    private RedPacketService redPacketService;
    private BukkitTask cleanupTask;
    private boolean paperChatEventRegistered;
    private volatile boolean paperChatEventObserved;
    private java.util.function.BiConsumer<Player, Collection<String>> completionAdder;
    private java.util.function.BiConsumer<Player, Collection<String>> completionRemover;
    private java.util.function.Supplier<AbyssPartyAccess> partyProvider = () -> null;
    private Supplier<RegionChatAccess> regionChatProvider = () -> null;
    private ChatLinks links;

    public ChatService(
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<TabRefreshable> tabRefreshableProvider,
        ChatModuleConfiguration configuration,
        ChatRepository repository,
        PacketBridgeAPI packetBridge,
        ItemBridgeAPI itemStackBridge,
        String completionUiId,
        CrossServerAPI crossServer,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this(plugin, logger, tabRefreshableProvider, configuration, repository, packetBridge, itemStackBridge,
            completionUiId, crossServer, placeholderResolver, null, null, null);
    }

    public ChatService(
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<TabRefreshable> tabRefreshableProvider,
        ChatModuleConfiguration configuration,
        ChatRepository repository,
        PacketBridgeAPI packetBridge,
        ItemBridgeAPI itemStackBridge,
        String completionUiId,
        CrossServerAPI crossServer,
        PlaceholderResolverAPI placeholderResolver,
        MessageProvider messages,
        VanillaItemNameBridge vanillaItemNameBridge,
        ContentModerator contentModerator
    ) {
        this.plugin = Objects.requireNonNull(plugin);
        this.logger = logger;
        this.tabRefreshableProvider = tabRefreshableProvider;
        this.configuration = Objects.requireNonNull(configuration);
        this.repository = Objects.requireNonNull(repository);
        this.moderation = new ChatModeration(this, repository);
        this.packetBridge = Objects.requireNonNull(packetBridge);
        this.itemStackBridge = Objects.requireNonNull(itemStackBridge);
        this.completionUiId = completionUiId;
        this.crossServer = Objects.requireNonNull(crossServer);
        this.placeholderResolver = placeholderResolver;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.contentModerator = contentModerator != null ? contentModerator : new NoopContentModerator();
        this.guardRemoteIgnores = repository.supportsIgnoreGuards();
        this.publicDelivery = new ChatDeliveryQueue(task -> Bukkit.getScheduler().runTask(plugin, task),
            task -> Bukkit.getScheduler().runTaskLater(plugin, task, 10),
            deliveryPolicyVersion::get, failure -> logger.log(java.util.logging.Level.WARNING, "聊天投递失败", failure),
            guardRemoteIgnores ? repository::lockRouteStates : null);
        this.ioExecutor = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "AXS-Chat-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    public void setEventBusProvider(java.util.function.Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /**
     * 注入红包服务（由 ChatModule 在 startService 后设置）。
     */
    public void setRedPacketService(RedPacketService redPacketService) {
        this.redPacketService = redPacketService;
    }

    public void setPartyProvider(java.util.function.Supplier<AbyssPartyAccess> provider) {
        this.partyProvider = provider;
    }

    public void setRegionChatProvider(Supplier<RegionChatAccess> provider) {
        this.regionChatProvider = provider;
    }

    RegionChatAccess regionChatAccess(ChatChannelDefinition channel) {
        ChatChannelMode mode = channel.mode();
        return mode == ChatChannelMode.NORMAL || mode == ChatChannelMode.GLOBAL ? regionChatProvider.get() : null;
    }

    AbyssPartyAccess.PartySnapshot partyOf(Player player) {
        AbyssPartyAccess access = partyProvider.get();
        return access == null || player == null ? null : access.partyOf(player.getUniqueId());
    }

    public void sendPartyMessage(Player sender, String text, Consumer<ChatOperationResult> result) {
        ChatChannelDefinition party = partyChannel();
        if (party == null) result.accept(ChatOperationResult.failure(message("result.no-channel")));
        else queueChatMessage(new ChatConnection(sender, AuthAccess.snapshot(sender.getUniqueId())), text, party, result);
    }

    ChatChannelDefinition partyChannel() {
        return configuration.channels().values().stream()
            .filter(channel -> channel.mode() == ChatChannelMode.PARTY).findFirst().orElse(null);
    }

    public void setLinks(ChatLinks links) {
        this.links = links;
    }

    public ChatLinks links() {
        return links;
    }

    String linkMessage(String key) {
        return message(key);
    }

    ChatOperationResult linkResult(String key) {
        return ChatOperationResult.failure(message(key));
    }

    void recordLinkSend(Player player, String label) {
        trackMessageFingerprint(player.getUniqueId(), label);
    }

    private boolean canReceiveLink(ChatChannelDefinition channel, Player sender, Player recipient, UUID target,
                                   Map<UUID, RouteState> routes, RegionChatAccess regionAccess) {
        UUID senderId = sender.getUniqueId();
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(senderId)
                || (!channel.sendPermission().isBlank() && !sender.hasPermission(channel.sendPermission()))) return false;
        UUID viewer = recipient.getUniqueId();
        if (channel.mode() != ChatChannelMode.PRIVATE) {
            return canReceiveAudience(channel, sender, senderId, recipient)
                && canReceiveRoute(routes.get(viewer), routes.get(senderId), senderId, recipient)
                && (regionAccess == null || regionAccess.canReceivePublic(recipient));
        }
        if (!sender.hasPermission(PRIVATE_PERMISSION)) return false;
        if (target == null) return false;
        if (!viewer.equals(senderId) && !viewer.equals(target)) return false;
        if (!canReceiveChannel(channel, Bukkit.getPlayer(target))) return false;
        ChatPlayerState from = routedState(routes, senderId);
        ChatPlayerState to = routedState(routes, target);
        return from != null && to != null && to.acceptsPrivateMessages()
            && !from.ignores(target) && !to.ignores(senderId);
    }

    void sendLink(Player sender, ChatChannelDefinition selected, String label,
                  BiFunction<ChatChannelDefinition, List<Player>, ChatOperationResult> send,
                  Consumer<ChatOperationResult> result) {
        UUID id = sender.getUniqueId();
        var actor = new ChatConnection(sender, AuthAccess.snapshot(id));
        if (!actor.isCurrent()) return;
        var audience = captureAudience(selected, sender, id, Set.of());
        List<UUID> viewers = audience.stream().map(connection -> connection.snapshot().playerId()).toList();
        long channelAtSubmission = channelChanges.getOrDefault(id, 0L);
        UUID privateTarget = replyTargetId(id);
        var party = partyOf(sender);
        if (!publicDelivery.offer(() -> {
            ChatChannelDefinition channel = selected != null ? selected
                : configuration.channel(repository.loadState(id, configuration.defaultChannelId()).currentChannelId());
            if (channel == null) return new PreparedLink(null, null, Map.of());
            List<UUID> routeIds = channel.mode() != ChatChannelMode.PRIVATE ? viewers
                : privateTarget == null || privateTarget.equals(id) ? List.of(id) : List.of(id, privateTarget);
            return new PreparedLink(channel, loadActiveMute(id), loadDeliveryRoutes(routeIds, channel.id(), id));
        }, prepared -> true, prepared -> !pendingMuteWrites.containsKey(id) &&
                ignoreWritesPending.stream().noneMatch(prepared.routes()::containsKey), (prepared, failure) -> {
            if (!actor.isCurrent()) return;
            if (failure != null) {
                logStorageFailure(failure);
                result.accept(storageUnavailable());
                return;
            }
            ChatChannelDefinition channel = prepared.channel();
            if (channel == null) {
                result.accept(linkResult("result.no-channel"));
                return;
            }
            boolean privateLink = channel.mode() == ChatChannelMode.PRIVATE;
            if ((selected == null && channelChanges.getOrDefault(id, 0L) != channelAtSubmission)
                    || (privateLink && !Objects.equals(privateTarget, replyTargetId(id)))) {
                result.accept(linkResult("result.channel-changed-while-sending"));
                return;
            }
            var currentParty = channel.mode() == ChatChannelMode.PARTY ? partyOf(sender) : null;
            if (channel.mode() == ChatChannelMode.PARTY
                    && (party == null || currentParty == null || !party.id().equals(currentParty.id()))) {
                result.accept(linkResult("result.party-required"));
                return;
            }
            ChatOperationResult validation = validateBeforeSend(sender, label, channel, privateLink, prepared.mute());
            if (!validation.success()) {
                result.accept(validation);
                return;
            }
            RegionChatAccess regionAccess = regionChatAccess(channel);
            if (regionAccess != null && !regionAccess.canSendPublic(sender)) {
                result.accept(linkResult("result.region-speak-denied"));
                return;
            }
            List<Player> recipients = new ArrayList<>();
            for (ChatConnection connection : audience) {
                if (connection.isCurrent()
                        && canReceiveLink(channel, sender, connection.player(), privateTarget, prepared.routes(), regionAccess)) {
                    recipients.add(connection.player());
                }
            }
            result.accept(send.apply(channel, recipients));
        })) result.accept(storageUnavailable());
    }

    void checkLinkAudience(ChatChannelDefinition channel, Player sender, Player viewer, Consumer<Boolean> result) {
        UUID senderId = sender.getUniqueId();
        UUID viewerId = viewer.getUniqueId();
        var actor = new ChatConnection(viewer, AuthAccess.snapshot(viewerId));
        UUID target = channel.mode() == ChatChannelMode.PRIVATE ? replyTargetId(senderId) : null;
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(senderId);
        ids.add(viewerId);
        if (target != null) ids.add(target);
        if (!publicDelivery.offer(() -> loadRouteChunk(ids, channel.id(), null), routes -> true,
            routes -> ignoreWritesPending.stream().noneMatch(ids::contains), (routes, failure) -> {
                if (!actor.isCurrent()) return;
                if (failure != null) logStorageFailure(failure);
                result.accept(failure == null
                    && (channel.mode() != ChatChannelMode.PRIVATE || Objects.equals(target, replyTargetId(senderId)))
                    && canReceiveLink(channel, sender, viewer, target, routes, regionChatAccess(channel)));
            })) result.accept(false);
    }

    private record PreparedLink(ChatChannelDefinition channel, ChatMuteRecord mute, Map<UUID, RouteState> routes) {}

    public CompletionStage<Boolean> receiveExternal(String channelId, UUID linkedPlayerId, String format,
                                                     String rawMessage, Set<UUID> mentions) {
        ChatChannelDefinition channel = configuration.channel(channelId);
        if (isStopping() || channel == null || !channel.permitsExternalRelay() || rawMessage == null
                || rawMessage.isBlank() || rawMessage.length() > configuration.maxLength()
                || !canSendExternal(channel, linkedPlayerId)) {
            return CompletableFuture.completedFuture(false);
        }
        ContentModerator.ModerationResult moderated = contentModerator.filter(rawMessage);
        if (moderated.blocked()) return CompletableFuture.completedFuture(false);
        String text = format.replace("{message}", ChatColor.stripColor(moderated.text()));
        RegionChatAccess regionAccess = regionChatAccess(channel);
        var audience = captureAudience(channel, null, linkedPlayerId, Set.of());
        if (regionAccess != null) audience.removeIf(connection -> !regionAccess.canReceivePublic(connection.player()));
        List<UUID> ids = audience.stream().map(connection -> connection.snapshot().playerId()).toList();
        Set<UUID> mentioned = Set.copyOf(mentions);
        var result = new CompletableFuture<Boolean>();
        externalResults.add(result);
        if (!publicDelivery.offer(() -> {
            ChatMuteRecord mute = linkedPlayerId == null ? null : loadActiveMute(linkedPlayerId);
            return new ExternalRoute(mute, loadDeliveryRoutes(ids, channel.id(), linkedPlayerId));
        }, route -> true, route -> (linkedPlayerId == null || !pendingMuteWrites.containsKey(linkedPlayerId))
                && ignoreWritesPending.stream().noneMatch(route.routes()::containsKey), (route, failure) -> {
            externalResults.remove(result);
            if (failure != null) {
                logStorageFailure(failure);
                result.complete(false);
                return;
            }
            try {
                if (isStopping() || (route.mute() != null && route.mute().active(Instant.now()))
                        || !canSendExternal(channel, linkedPlayerId)) {
                    result.complete(false);
                    return;
                }
                deliverExternal(channel, linkedPlayerId, text, mentioned, audience, route.routes());
                result.complete(true);
            } catch (RuntimeException | LinkageError error) {
                result.completeExceptionally(error);
            }
        })) {
            externalResults.remove(result);
            result.complete(false);
        }
        return result.copy();
    }

    private boolean canSendExternal(ChatChannelDefinition channel, UUID linkedPlayerId) {
        RegionChatAccess regionAccess = regionChatAccess(channel);
        boolean requiresPermission = !channel.sendPermission().isBlank();
        Player linked = linkedPlayerId != null && (requiresPermission || regionAccess != null)
            ? Bukkit.getPlayer(linkedPlayerId) : null;
        if (requiresPermission && (linked == null || !linked.isOnline()
                || !AuthAccess.admitted(linkedPlayerId) || !linked.hasPermission(channel.sendPermission()))) return false;
        return linked == null || regionAccess == null || regionAccess.canSendPublic(linked);
    }

    private void deliverExternal(ChatChannelDefinition channel, UUID senderId, String text, Set<UUID> mentions,
                                 List<ChatConnection> audience, Map<UUID, RouteState> routes) {
        RegionChatAccess regionAccess = regionChatAccess(channel);
        RouteState sender = senderId == null ? null : routes.get(senderId);
        boolean mentionsEnabled = configuration.functions().mentionEnabled() && !mentions.isEmpty();
        for (ChatConnection connection : audience) {
            Player recipient = connection.player();
            UUID id = connection.snapshot().playerId();
            RouteState route = routes.get(id);
            if (!connection.isCurrent() || !canReceiveAdmittedAudience(channel, null, senderId, recipient)
                    || !canReceiveRoute(route, sender, senderId, recipient)
                    || (regionAccess != null && !regionAccess.canReceivePublic(recipient))) continue;
            recipient.sendMessage(text);
            if (mentionsEnabled && mentions.contains(id) && !id.equals(senderId) && route.player().acceptsMentions()) {
                queueUnreadMention(id);
                sendSystemCard(recipient, "mention", Map.of("message", message("links.external-mention")));
            }
        }
    }

    private record ExternalRoute(ChatMuteRecord mute, Map<UUID, RouteState> routes) {}

    /**
     * 红包服务实例，供玩家/管理员命令调用。未接入时返回 {@code null}。
     */
    public RedPacketService redPacketService() {
        return redPacketService;
    }

    public void start(@org.jetbrains.annotations.Nullable String initializedNetworkId) throws Exception {
        networkId = initializedNetworkId;
        if (networkId != null) {
            crossServerChannel = crossServer.openChannelCompleting("chat-v2", configuration.crossServer(),
                delivery -> handleRemotePayload(delivery.payload(), delivery.nodeId(), delivery.timestamp()));
            var journal = configuration.crossServer().enabled() ? repository.relayJournal() : null;
            if (journal != null) {
                chatRelay = new ChatRelay(journal, nodeId(),
                    this::onPrimary, this::handleRelayPayload, publicDelivery::startAdmittedRemoteBatch, logger);
            }
        } else if (configuration.crossServer().enabled()) {
            logger.warning("Chat 跨服需要各节点共用同一 MySQL 数据库，本节点保持本地聊天。");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPaperChatEventIfAvailable();
        initMentionCompletionReflection();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AuthSnapshot session = AuthAccess.snapshot(player.getUniqueId());
            if (!session.admitted()) continue;
            var connection = new ChatConnection(player, session);
            admittedAudience.put(session.playerId(), connection);
            touchProfile(player, connection);
        }
        publishAudience();
        if (chatRelay != null) chatRelay.start(admittedAudience.keySet());
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runCleanup, CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
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
        stopping = true;
        moderationCallbacks.close();
        for (var result : moderationMainResults) result.completeExceptionally(new java.util.concurrent.CancellationException("Chat is stopping"));
        for (var result : moderationResults) result.completeExceptionally(new java.util.concurrent.CancellationException("Chat is stopping"));
        CompletionStage<Void> relayStopped = chatRelay == null ? CompletableFuture.completedFuture(null) : chatRelay.stopped();
        CompletionStage<Void> rewardsStopped = redPacketService == null ? CompletableFuture.completedFuture(null) : redPacketService.databaseStopped();
        databaseStopped = CompletableFuture.allOf(databaseStopped.toCompletableFuture(),
            ioExecutor.stopped().toCompletableFuture(), publicDelivery.stopped().toCompletableFuture(),
            relayStopped.toCompletableFuture(), rewardsStopped.toCompletableFuture(), moderationCallbacks.stopped().toCompletableFuture());
        ioExecutor.shutdown();
        if (chatRelay != null) {
            chatRelay.close();
            chatRelay = null;
        }
        publicDelivery.close();
        List.copyOf(externalResults).forEach(result -> result.complete(false));
        externalResults.clear();
        if (links != null) links.close();
        if (redPacketService != null) {
            redPacketService.shutdown();
            redPacketService = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        HandlerList.unregisterAll(this);
        paperChatEventRegistered = false;
        paperChatEventObserved = false;
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        states.clear();
        profiles.clear();
        mutes.clear();
        replyTargets.clear();
        lastMessageTimes.clear();
        lastDuplicateStamps.clear();
        processedEnvelopeKeys.clear();
        remoteInFlight.clear();
        channelChanges.clear();
        admittedAudience.clear();
        publishedAudience = ChatAudience.EMPTY;
    }

    public CompletionStage<Void> databaseStopped() { return databaseStopped.toCompletableFuture().copy(); }

    public boolean isStopping() { return stopping || ioExecutor.isShutdown(); }

    public void setReloadGate(ChatReloadGate gate) { reloadGate = gate; }

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
        return chatRelay != null || crossServerChannel != null && crossServerChannel.isActive();
    }

    public List<String> channelIds() {
        return new ArrayList<>(configuration.channels().keySet());
    }

    public ChatPlayerState getCachedState(UUID playerUuid) {
        return playerUuid == null ? null : states.get(playerUuid);
    }

    public ChatPlayerState readState(UUID playerUuid) {
        return state(playerUuid);
    }

    public ChatMuteRecord getCachedMute(UUID playerUuid) {
        return playerUuid == null ? null : mutes.get(playerUuid);
    }

    public CompletionStage<Boolean> queryMute(UUID playerUuid) {
        return readModeration(() -> loadActiveMute(playerUuid) != null);
    }

    public CompletionStage<List<MuteInfo>> activeMutes(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid mute page"));
        }
        return readModeration(() -> repository.loadActiveMutes(Instant.now(), limit, offset));
    }

    private <T> CompletionStage<T> readModeration(java.util.concurrent.Callable<T> query) {
        if (isStopping() || !moderationReadSlots.tryAcquire()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Chat moderation reads unavailable"));
        }
        return moderationIo(query, false).whenComplete((value, failure) -> moderationReadSlots.release()).copy();
    }

    public String replyTargetName(UUID playerUuid) {
        ReplyTarget target = playerUuid == null ? null : replyTargets.get(playerUuid);
        return target == null ? "" : target.name();
    }

    private UUID replyTargetId(UUID playerUuid) {
        ReplyTarget target = replyTargets.get(playerUuid);
        return target == null ? null : target.id();
    }

    private record ReplyTarget(UUID id, String name) {}

    public String channelDisplayName(String channelId) {
        ChatChannelDefinition channel = channel(channelId);
        return channel == null ? "" : channel.displayName();
    }

    public Collection<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) continue;
            names.add(player.getName());
        }
        return names;
    }

    public ChatOperationResult changeChannel(Player player, String channelId) {
        ChatChannelDefinition channel = channel(channelId);
        if (channel == null) {
            return ChatOperationResult.failure(message("result.unknown-channel", channelId));
        }
        if (!channel.sendPermission().isBlank() && !player.hasPermission(channel.sendPermission())) {
            return ChatOperationResult.failure(message("result.channel-no-permission"));
        }
        if (channel.mode() == ChatChannelMode.PARTY && partyOf(player) == null) {
            return ChatOperationResult.failure(message("result.party-required"));
        }
        ChatPlayerState previous = state(player.getUniqueId());
        if (previous == null || !saveState(previous.withCurrentChannel(channel.id(), Instant.now()), StateField.CHANNEL)) {
            return storageUnavailable();
        }
        channelChanges.merge(player.getUniqueId(), 1L, Long::sum);
        refreshTab(player, "chat-channel");
        return ChatOperationResult.success(message("result.channel-switched", channel.displayName()));
    }

    public ChatOperationResult setAcceptsPrivate(Player player, Boolean enabled) {
        ChatPlayerState previous = state(player.getUniqueId());
        if (previous == null) return storageUnavailable();
        boolean nextValue = enabled == null ? !previous.acceptsPrivateMessages() : enabled.booleanValue();
        ChatPlayerState updated = previous.withAcceptsPrivateMessages(nextValue, Instant.now());
        if (!saveState(updated, StateField.PRIVATE_MESSAGES)) return storageUnavailable();
        refreshTab(player, "chat-toggle-private");
        return ChatOperationResult.success(message("result.private-toggle", nextValue ? "开启" : "关闭"));
    }

    public ChatOperationResult setAcceptsMentions(Player player, Boolean enabled) {
        ChatPlayerState previous = state(player.getUniqueId());
        if (previous == null) return storageUnavailable();
        boolean nextValue = enabled == null ? !previous.acceptsMentions() : enabled.booleanValue();
        ChatPlayerState updated = previous.withAcceptsMentions(nextValue, Instant.now());
        if (!saveState(updated, StateField.MENTIONS)) return storageUnavailable();
        refreshTab(player, "chat-toggle-mentions");
        return ChatOperationResult.success(message("result.mentions-toggle", nextValue ? "开启" : "关闭"));
    }

    public ChatOperationResult setSocialSpy(Player player, Boolean enabled) {
        if (!player.hasPermission(SOCIAL_SPY_PERMISSION)) {
            return ChatOperationResult.failure(message("result.socialspy-no-permission"));
        }
        ChatPlayerState previous = state(player.getUniqueId());
        if (previous == null) return storageUnavailable();
        boolean nextValue = enabled == null ? !previous.socialSpyEnabled() : enabled.booleanValue();
        ChatPlayerState updated = previous.withSocialSpyEnabled(nextValue, Instant.now());
        if (!saveState(updated, StateField.SOCIAL_SPY)) return storageUnavailable();
        refreshTab(player, "chat-social-spy");
        return ChatOperationResult.success(message("result.socialspy-toggle", nextValue ? "开启" : "关闭"));
    }

    public ChatOperationResult setSocialSpy(String playerName, boolean enabled, String operatorName) {
        ChatPlayerProfile profile = resolveProfileByName(playerName);
        if (profile == null) {
            return ChatOperationResult.failure(message("result.player-not-found", playerName));
        }
        ChatPlayerState previous = state(profile.playerUuid());
        if (previous == null) return storageUnavailable();
        ChatPlayerState updated = previous.withSocialSpyEnabled(enabled, Instant.now());
        if (!saveState(updated, StateField.SOCIAL_SPY)) return storageUnavailable();
        Player online = Bukkit.getPlayer(profile.playerUuid());
        if (online != null) {
            online.sendMessage(PREFIX + ChatColor.YELLOW + message("result.socialspy-notify", operatorName, enabled ? "开启" : "关闭"));
            refreshTab(online, "chat-admin-spy");
        }
        return ChatOperationResult.success(message("result.socialspy-set", profile.lastKnownName(), enabled));
    }

    public void ignore(Player player, String targetName, Consumer<ChatOperationResult> result) {
        queueIgnore(player, targetName, true, result);
    }

    public void unignore(Player player, String targetName, Consumer<ChatOperationResult> result) {
        queueIgnore(player, targetName, false, result);
    }

    private void queueIgnore(Player player, String targetName, boolean ignored, Consumer<ChatOperationResult> result) {
        ChatConnection actor = new ChatConnection(player, AuthAccess.snapshot(player.getUniqueId()));
        if (!actor.isCurrent()) return;
        if (!pendingIgnoreCommands.tryAcquire()) {
            result.accept(storageUnavailable());
            return;
        }
        if (!ignoreWritesPending.add(actor.snapshot().playerId())) {
            pendingIgnoreCommands.release();
            result.accept(storageUnavailable());
            return;
        }
        IgnoreCommand command = new IgnoreCommand(actor, targetName, ignored, result);
        try {
            Player online = Bukkit.getPlayerExact(targetName);
            ChatPlayerProfile localTarget = online == null ? null
                : new ChatPlayerProfile(online.getUniqueId(), online.getName(), Instant.now(), nodeId());
            executeIgnore(command, () -> resolveIgnore(command, localTarget));
        } catch (RuntimeException failure) {
            failIgnore(command, failure);
        }
    }

    private void executeIgnore(IgnoreCommand command, Runnable work) {
        try {
            ioExecutor.execute(() -> {
                try {
                    if (isCurrentIgnoreSession(command.actor().snapshot())) work.run();
                    else finishIgnore(command, null);
                } catch (RuntimeException failure) {
                    failIgnore(command, failure);
                }
            });
        } catch (RejectedExecutionException stopped) {
            finishIgnore(command, storageUnavailable());
        }
    }

    private void failIgnore(IgnoreCommand command, RuntimeException failure) {
        logger.log(java.util.logging.Level.WARNING, "处理聊天忽略命令失败", failure);
        finishIgnore(command, storageUnavailable());
    }

    private void finishIgnore(IgnoreCommand command, ChatOperationResult outcome) {
        try {
            onPrimary(() -> {
                try {
                    if (ioExecutor.isShutdown() || outcome == null) return;
                    if (outcome.success()) publishCrossServer("axs_chat_policy");
                    if (!command.actor().isCurrent()) return;
                    if (outcome.success()) refreshTab(command.actor().player(), command.ignored() ? "chat-ignore" : "chat-unignore");
                    command.result().accept(outcome);
                } finally {
                    releaseIgnore(command);
                }
            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException stopped) {
            releaseIgnore(command);
        }
    }

    private void releaseIgnore(IgnoreCommand command) {
        deliveryPolicyVersion.incrementAndGet();
        if (ignoreWritesPending.remove(command.actor().snapshot().playerId())) pendingIgnoreCommands.release();
    }

    private boolean isCurrentIgnoreSession(AuthSnapshot previous) {
        AuthSnapshot current = AuthAccess.snapshot(previous.playerId());
        return !ioExecutor.isShutdown() && current.admitted() && current.generation() == previous.generation()
            && Objects.equals(current.connectionId(), previous.connectionId());
    }

    private void resolveIgnore(IgnoreCommand command, ChatPlayerProfile target) {
        if (target == null) {
            for (ChatPlayerProfile profile : profiles.values()) {
                if (profile.lastKnownName().equalsIgnoreCase(command.targetName())) {
                    target = profile;
                    break;
                }
            }
        }
        if (target == null) {
            try {
                target = repository.findProfileByName(command.targetName()).orElse(null);
            } catch (SQLException exception) {
                logStorageFailure(exception);
            }
        }
        if (target != null) {
            updateIgnore(command, target);
        } else {
            onPrimary(() -> {
                try {
                    resolveOfflineIgnore(command);
                } catch (RuntimeException failure) {
                    failIgnore(command, failure);
                }
            });
        }
    }

    private void resolveOfflineIgnore(IgnoreCommand command) {
        if (ioExecutor.isShutdown() || !command.actor().isCurrent()) {
            finishIgnore(command, null);
            return;
        }
        for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline == null) continue;
            String name = offline.getName();
            if (name == null || !name.equalsIgnoreCase(command.targetName())) continue;
            ChatPlayerProfile target = new ChatPlayerProfile(offline.getUniqueId(), name, Instant.now(), "");
            executeIgnore(command, () -> {
                try {
                    repository.upsertProfile(target);
                } catch (SQLException exception) {
                    logStorageFailure(exception);
                }
                updateIgnore(command, target);
            });
            return;
        }
        finishIgnore(command, ChatOperationResult.failure(message("result.player-not-found", command.targetName())));
    }

    private void updateIgnore(IgnoreCommand command, ChatPlayerProfile target) {
        AuthSnapshot actor = command.actor().snapshot();
        boolean ignored = command.ignored();
        UUID playerId = actor.playerId();
        profiles.put(target.playerUuid(), target);
        if (ignored && playerId.equals(target.playerUuid())) {
            finishIgnore(command, ChatOperationResult.failure(message("result.ignore-self")));
            return;
        }
        if (!isCurrentIgnoreSession(actor)) {
            finishIgnore(command, null);
            return;
        }
        try {
            if (!repository.setIgnored(playerId, target.playerUuid(), ignored)) {
                finishIgnore(command, ChatOperationResult.failure(message(
                    ignored ? "result.already-ignored" : "result.not-ignored", target.lastKnownName())));
                return;
            }
            state(playerId);
            finishIgnore(command, ChatOperationResult.success(
                message(ignored ? "result.ignored" : "result.unignored", target.lastKnownName())));
        } catch (SQLException exception) {
            logStorageFailure(exception);
            finishIgnore(command, storageUnavailable());
        }
    }

    private record IgnoreCommand(ChatConnection actor, String targetName, boolean ignored,
                                 Consumer<ChatOperationResult> result) {}

    public void sendPrivateMessage(Player sender, String targetName, String message, Consumer<ChatOperationResult> result) {
        ChatChannelDefinition channel = privateChannel();
        if (channel == null) {
            result.accept(ChatOperationResult.failure(message("result.no-private-channel")));
            return;
        }
        Player online = Bukkit.getPlayerExact(targetName);
        ChatPlayerProfile target = online == null ? null
            : new ChatPlayerProfile(online.getUniqueId(), online.getName(), Instant.now(), nodeId());
        queueChatMessage(new ChatConnection(sender, AuthAccess.snapshot(sender.getUniqueId())), message, channel,
            new PrivateRecipient(targetName, false, target), result);
    }

    public void reply(Player sender, String message, Consumer<ChatOperationResult> result) {
        ChatChannelDefinition channel = privateChannel();
        if (channel == null) {
            result.accept(ChatOperationResult.failure(message("result.no-private-channel")));
            return;
        }
        queueChatMessage(new ChatConnection(sender, AuthAccess.snapshot(sender.getUniqueId())), message, channel,
            new PrivateRecipient(null, true, null), result);
    }

    public CompletionStage<ChatOperationResult> mutePlayer(String playerName, Instant expiresAt, String reason, String mutedBy) {
        return changeMute(playerName, expiresAt, reason, mutedBy, true);
    }

    public CompletionStage<ChatOperationResult> unmutePlayer(String playerName, String operator) {
        return changeMute(playerName, null, "", operator, false);
    }

    private CompletionStage<ChatOperationResult> changeMute(String playerName, Instant expiry, String reason, String operator, boolean mute) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(new IllegalStateException("Submit moderation on the server thread"));
        if (isStopping() || !moderationSlots.tryAcquire()) return CompletableFuture.completedFuture(storageUnavailable());
        var result = new CompletableFuture<ChatOperationResult>();
        moderationResults.add(result);
        result.whenComplete((value, error) -> { moderationResults.remove(result); moderationSlots.release(); });
        if (playerName == null || playerName.isBlank()) { result.complete(ChatOperationResult.failure(message("result.player-not-found", playerName))); return result.copy(); }
        Player online = Bukkit.getPlayerExact(playerName);
        ChatPlayerProfile cached = online == null ? profiles.values().stream()
            .filter(profile -> playerName.equalsIgnoreCase(profile.lastKnownName())).findFirst().orElse(null)
            : new ChatPlayerProfile(online.getUniqueId(), online.getName(), Instant.now(), nodeId());
        CompletionStage<ChatPlayerProfile> target = cached != null ? CompletableFuture.completedFuture(cached)
            : moderationIo(() -> repository.findProfileByName(playerName).orElse(null), false)
                .thenCompose(found -> found != null ? CompletableFuture.completedFuture(found)
                    : moderationMain(() -> resolveOfflineProfileByName(playerName)));
        target.thenCompose(profile -> {
            if (profile == null) return CompletableFuture.completedFuture(ChatOperationResult.failure(message("result.player-not-found", playerName)));
            UUID id = profile.playerUuid();
            pendingMuteWrites.merge(id, 1, Integer::sum);
            deliveryPolicyVersion.incrementAndGet();
            return moderationIo(() -> {
                Instant now = Instant.now();
                ChatMuteRecord record = mute ? new ChatMuteRecord(id, operator == null ? "" : operator,
                    reason == null ? "" : reason, now, expiry) : null;
                ChatModerationReceipt receipt = new ChatModerationReceipt(UUID.randomUUID(), id, profile.lastKnownName(),
                    mute ? "MUTE" : "UNMUTE", operator == null ? "" : operator, reason == null ? "" : reason, nodeId(), now, expiry);
                if (mute) repository.saveMute(record, receipt); else repository.deleteMute(id, receipt);
                return new CommittedMute(profile, record, receipt);
            }, true).whenComplete((value, failure) -> {
                deliveryPolicyVersion.incrementAndGet();
                pendingMuteWrites.compute(id, (key, count) -> count == null || count <= 1 ? null : count - 1);
            }).thenApply(committed -> {
                if (!isStopping()) try {
                    onPrimary(() -> moderationCallbacks.call(() -> {
                        if (isStopping()) return null;
                        if (committed.record() == null) mutes.remove(id); else mutes.put(id, committed.record());
                        publishCrossServer("axs_chat_policy");
                        publishCrossServer("axs_mod|" + committed.receipt().id());
                        notifyMuteChange(committed.receipt(), committed.record());
                        return null;
                    }, null));
                } catch (RuntimeException closing) { if (!isStopping()) logger.warning("禁言已提交，通知发送失败: " + closing.getMessage()); }
                return ChatOperationResult.success(message(mute ? "result.muted" : "result.unmuted", profile.lastKnownName())
                    + " " + message("moderation.receipt-id", committed.receipt().id()));
            });
        }).whenComplete((value, failure) -> {
            if (failure != null) {
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof SQLException sql) { logStorageFailure(sql); result.complete(storageUnavailable()); }
                else result.completeExceptionally(cause);
            } else result.complete(value);
        });
        return result.copy();
    }

    private record CommittedMute(ChatPlayerProfile profile, ChatMuteRecord record, ChatModerationReceipt receipt) {}

    private <T> CompletableFuture<T> moderationIo(java.util.concurrent.Callable<T> task, boolean write) {
        var result = new CompletableFuture<T>();
        try {
            ioExecutor.execute(() -> {
                try {
                    if (!write && isStopping()) throw new java.util.concurrent.CancellationException("Chat is stopping");
                    result.complete(task.call());
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException stopped) { result.completeExceptionally(stopped); }
        return result;
    }

    private <T> CompletionStage<T> moderationMain(Supplier<T> task) {
        var result = new CompletableFuture<T>();
        moderationMainResults.add(result);
        result.whenComplete((value, error) -> moderationMainResults.remove(result));
        if (isStopping()) result.completeExceptionally(new java.util.concurrent.CancellationException("Chat is stopping"));
        else try {
            onPrimary(() -> {
                boolean ran = moderationCallbacks.call(() -> {
                    try { result.complete(task.get()); } catch (Throwable failure) { result.completeExceptionally(failure); }
                    return true;
                }, false);
                if (!ran) result.completeExceptionally(new java.util.concurrent.CancellationException("Chat is stopping"));
            });
        } catch (RuntimeException closing) { result.completeExceptionally(closing); }
        return result;
    }

    public void replyModeration(CommandSender sender, ChatOperationResult result, Throwable failure) {
        if (isStopping()) return;
        onPrimary(() -> moderationCallbacks.call(() -> {
            if (isStopping() || !sender.hasPermission("arcartxsuite.admin")) return null;
            if (sender instanceof Player player && (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                || !AuthAccess.admitted(player.getUniqueId()))) return null;
            ChatOperationResult reply = failure == null ? result : storageUnavailable();
            sender.sendMessage((messages == null ? "" : messages.get("prefix")) + (reply.success() ? ChatColor.GREEN : ChatColor.RED) + reply.message());
            return null;
        }, null));
    }

    public void inspectModeration(CommandSender operator, String action, String argument) {
        moderation.inspect(operator, action, argument);
    }

    private void notifyMuteChange(ChatModerationReceipt receipt, ChatMuteRecord record) {
        Player player = Bukkit.getPlayer(receipt.playerId());
        if (player == null || !player.isOnline()
                || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(receipt.playerId())) return;
        if (record == null) {
            player.sendMessage(PREFIX + ChatColor.GREEN + message("result.unmuted-notify"));
            refreshTab(player, "chat-unmuted");
            return;
        }
        String remaining = record.remainingText(Instant.now());
        boolean sent = sendSystemCard(player, "mute", Map.of(
            "message", "你已被禁言",
            "reason", record.reason().isBlank() ? "无" : record.reason(),
            "mutedBy", record.mutedBy(),
            "remaining", remaining
        ));
        if (!sent) player.sendMessage(PREFIX + ChatColor.RED + message("result.muted-notify", remaining));
        refreshTab(player, "chat-muted");
    }

    public void showHistory(Player viewer, String channel) {
        moderation.history(viewer, channel);
    }

    public void reportMessage(Player viewer, String id, String reason) {
        moderation.report(viewer, id, reason);
    }


    @EventHandler(ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        ChatReloadGate gate = reloadGate;
        if (gate != null && gate.reject(event)) return;
        if (configuration.forceChatTakeover()) {
            forceCancelLegacyChatEvent(event);
            return;
        }
        // Paper copies the legacy cancellation into AsyncChatEvent. Let its modern
        // handler own ordinary native messages when it is available.
        if (paperChatEventRegistered) return;
        event.setCancelled(true);
        dispatchOrDeferToPaper(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAsyncPlayerChatForceTakeover(AsyncPlayerChatEvent event) {
        ChatReloadGate gate = reloadGate;
        if (gate != null && gate.reject(event)) return;
        if (!configuration.forceChatTakeover()) {
            return;
        }
        forceCancelLegacyChatEvent(event);
        dispatchOrDeferToPaper(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAsyncPlayerChatForceFinalCancel(AsyncPlayerChatEvent event) {
        ChatReloadGate gate = reloadGate;
        if (gate != null && gate.reject(event)) return;
        if (configuration.forceChatTakeover()) {
            forceCancelLegacyChatEvent(event);
        }
    }

    private void dispatchOrDeferToPaper(Player player, String message) {
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
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
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        Player player=Bukkit.getPlayer(event.snapshot().playerId());
        if(player==null) return;
        var connection = new ChatConnection(player, event.snapshot());
        admittedAudience.put(event.snapshot().playerId(), connection);
        publishAudience();
        if (chatRelay != null) chatRelay.audienceChanged(admittedAudience.keySet());
        touchProfile(player, connection);
        updateMentionCompletions(player, true);
        broadcastCompletionPlayerList();
        RedPacketService service = redPacketService;
        if (service != null) {
            service.onPlayerJoin(player);
        }
    }

    @EventHandler
    public void onRestriction(xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent event) {
        UUID uuid=event.previous().playerId();
        withdrawAudience(uuid);
        replyTargets.remove(uuid);
        moderation.forget(uuid);
        if (links != null) links.clearLocation(uuid);
        Player player=Bukkit.getPlayer(uuid);
        if(player!=null) updateMentionCompletions(player,false);
        broadcastCompletionPlayerList();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (AuthAccess.admitted(event.getPlayer().getUniqueId())) touchProfile(event.getPlayer(), null);
        UUID uuid = event.getPlayer().getUniqueId();
        withdrawAudience(uuid);
        states.remove(uuid);
        mutes.remove(uuid);
        replyTargets.remove(uuid);
        moderation.forget(uuid);
        if (links != null) links.clearLocation(uuid);
        lastMessageTimes.remove(uuid);
        lastDuplicateStamps.remove(uuid);
        channelChanges.remove(uuid);
        updateMentionCompletions(event.getPlayer(), false);
        broadcastCompletionPlayerList();
    }

    private ChatOperationResult dispatchChannelMessage(Player sender, String rawMessage, ChatChannelDefinition channel,
            ChatMuteRecord mute, List<ChatConnection> audience, Map<UUID, RouteState> routes) {
        String message = rawMessage;
        ChatOperationResult validation = validateBeforeSend(sender, message, channel, false, mute);
        if (!validation.success()) {
            return validation;
        }
        RegionChatAccess regionAccess = regionChatAccess(channel);
        if (regionAccess != null && !regionAccess.canSendPublic(sender)) {
            return ChatOperationResult.failure(message("result.region-speak-denied"));
        }

        String processedMessage = applyCustomComponents(message);
        ContentModerator.ModerationResult moderationResult = contentModerator.filter(processedMessage);
        if (moderationResult.blocked()) {
            boolean cardSent = sendSystemCard(sender, "filter", Map.of("message", message("result.filtered")));
            return cardSent ? ChatOperationResult.failureCardNotified(message("result.filtered")) : ChatOperationResult.failure(message("result.filtered"));
        }
        processedMessage = moderationResult.text();

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
            nodeId(),
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
            processedMessage,
            channel.audienceKey()
        );
        rememberEnvelope(envelope.dedupeKey());
        boolean relay = channel.permitsCrossServer() && crossServerActive();
        Set<UUID> delivered = deliverPreparedPublic(envelope, channel, sender, audience, routes, relay);
        if (relay) publishChatEnvelope(sender, envelope, delivered);
        trackMessageFingerprint(sender.getUniqueId(), processedMessage);
        publishChatEvent(sender, channel.id(), "channel");
        if (channel.permitsExternalRelay()) {
            Bukkit.getPluginManager().callEvent(new xuanmo.arcartxsuite.api.event.chat.PublicChatMessageEvent(
                sender, channel.id(), processedMessage));
        }
        return ChatOperationResult.success(message("result.chat-sent"));
    }

    private ChatOperationResult dispatchPrivateMessage(
        Player sender,
        ChatPlayerProfile targetProfile,
        String rawMessage,
        ChatChannelDefinition channel,
        boolean replyMode,
        ChatMuteRecord mute,
        List<ChatConnection> audience,
        Map<UUID, RouteState> routes
    ) {
        if (targetProfile == null) {
            return ChatOperationResult.failure(message("result.target-missing"));
        }
        if (sender.getUniqueId().equals(targetProfile.playerUuid())) {
            return ChatOperationResult.failure(message("result.self-private"));
        }
        String message = sanitizeAndValidateOutgoingMessage(sender, rawMessage);
        if (message == null) {
            return ChatOperationResult.failure(message("result.empty-message"));
        }
        ChatOperationResult validation = validateBeforeSend(sender, message, channel, true, mute);
        if (!validation.success()) {
            return validation;
        }

        ChatPlayerState senderState = routedState(routes, sender.getUniqueId());
        if (senderState == null) return storageUnavailable();
        if (senderState.ignores(targetProfile.playerUuid())) {
            return ChatOperationResult.failure(message("result.target-ignored"));
        }
        Player localTarget = onlineRecipient(audience, targetProfile.playerUuid());
        if (localTarget != null) {
            if(!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(localTarget.getUniqueId()))
                return ChatOperationResult.failure(message("result.target-offline"));
            ChatPlayerState targetState = routedState(routes, localTarget.getUniqueId());
            if (targetState == null) return storageUnavailable();
            if (!canReceiveChannel(channel, localTarget)) {
                return ChatOperationResult.failure(message("result.target-rejects"));
            }
            if (!targetState.acceptsPrivateMessages()) {
                return ChatOperationResult.failure(message("result.target-rejects"));
            }
            if (targetState.ignores(sender.getUniqueId())) {
                return ChatOperationResult.failure(message("result.target-ignored"));
            }
        } else if (Bukkit.getPlayer(targetProfile.playerUuid()) != null || !channel.permitsCrossServer() || !crossServerActive()) {
            return ChatOperationResult.failure(message("result.target-offline"));
        }

        String processedMessage = applyCustomComponents(message);
        ContentModerator.ModerationResult moderationResult = contentModerator.filter(processedMessage);
        if (moderationResult.blocked()) {
            boolean cardSent = sendSystemCard(sender, "filter", Map.of("message", message("result.filtered")));
            return cardSent ? ChatOperationResult.failureCardNotified(message("result.filtered")) : ChatOperationResult.failure(message("result.filtered"));
        }
        processedMessage = moderationResult.text();

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
            nodeId(),
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
            processedMessage,
            channel.audienceKey()
        );
        rememberEnvelope(envelope.dedupeKey());
        Runnable addressed = () -> {
            deliverPrivateEnvelope(envelope, channel, sender, localTarget, audience, routes);
            replyTargets.put(sender.getUniqueId(), new ReplyTarget(targetProfile.playerUuid(), targetProfile.lastKnownName()));
            if (localTarget != null) replyTargets.put(localTarget.getUniqueId(), new ReplyTarget(sender.getUniqueId(), sender.getName()));
            refreshTab(sender, replyMode ? "chat-reply" : "chat-msg");
            if (localTarget != null) refreshTab(localTarget, "chat-msg-target");
        };
        addressed.run();
        trackMessageFingerprint(sender.getUniqueId(), processedMessage);
        if (localTarget == null) {
            publishChatEnvelope(sender, envelope, Set.of(), () ->
                sender.sendMessage(PREFIX + ChatColor.GREEN + message("result.private-relay-accepted")));
            return new ChatOperationResult(true, "", true);
        }
        return ChatOperationResult.success(message("result.private-sent"));
    }

    void prepareRedPacketSend(Player sender, String wish, BiConsumer<RedPacketPermission, ChatOperationResult> ready) {
        UUID id = sender.getUniqueId();
        var actor = new ChatConnection(sender, AuthAccess.snapshot(id));
        long channelAtSubmission = channelChanges.getOrDefault(id, 0L);
        if (!publicDelivery.offer(() -> {
            long revision = deliveryPolicyVersion.get();
            var state = repository.loadState(id, configuration.defaultChannelId());
            return new RedPacketPermission(configuration.channel(state.currentChannelId()), loadActiveMute(id), revision);
        }, permission -> true, permission -> !pendingMuteWrites.containsKey(id), (permission, failure) -> {
            if (!actor.isCurrent()) {
                ready.accept(null, linkResult("result.player-offline"));
            } else if (failure != null) {
                logStorageFailure(failure);
                ready.accept(null, storageUnavailable());
            } else if (channelChanges.getOrDefault(id, 0L) != channelAtSubmission) {
                ready.accept(null, linkResult("result.channel-changed-while-sending"));
            } else if (!RedPacketService.usableChannel(permission.channel(), sender)) {
                ready.accept(null, linkResult("result.no-channel"));
            } else {
                ready.accept(permission, validateRedPacketSend(sender, wish, permission));
            }
        })) ready.accept(null, storageUnavailable());
    }

    boolean redPacketPolicyCurrent(UUID senderId, RedPacketPermission permission) {
        return !isStopping() && permission.revision() == deliveryPolicyVersion.get()
            && !pendingMuteWrites.containsKey(senderId);
    }

    ChatOperationResult validateRedPacketSend(Player sender, String wish, RedPacketPermission permission) {
        if (!redPacketPolicyCurrent(sender.getUniqueId(), permission)) {
            return linkResult("result.channel-changed-while-sending");
        }
        var validation = validateBeforeSend(sender, wish, permission.channel(), false, permission.mute());
        if (!validation.success()) return validation;
        var regions = regionChatAccess(permission.channel());
        return regions != null && !regions.canSendPublic(sender) ? linkResult("result.region-speak-denied") : validation;
    }

    record RedPacketPermission(ChatChannelDefinition channel, ChatMuteRecord mute, long revision) {}

    private ChatOperationResult validateBeforeSend(Player sender, String message, ChatChannelDefinition channel,
                                                   boolean privateMessage, ChatMuteRecord muteRecord) {
        if (sender == null || !sender.isOnline()) {
            return ChatOperationResult.failure(message("result.player-offline"));
        }
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(sender.getUniqueId()))
            return ChatOperationResult.failure("请先完成登录验证。");
        if (!channel.sendPermission().isBlank() && !sender.hasPermission(channel.sendPermission())) {
            return ChatOperationResult.failure(message("result.channel-speak-no-permission"));
        }
        if (privateMessage && !sender.hasPermission(PRIVATE_PERMISSION)) {
            return ChatOperationResult.failure(message("result.private-no-permission"));
        }
        if (channel.mode() == ChatChannelMode.PARTY && partyOf(sender) == null) {
            return ChatOperationResult.failure(message("result.party-required"));
        }
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
                ? ChatOperationResult.failureCardNotified(message("result.mute-remaining", muteRecord.remainingText(Instant.now())))
                : ChatOperationResult.failure(message("result.mute-remaining", muteRecord.remainingText(Instant.now())));
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = configuration.cooldownMillis();
        if (cooldownMillis > 0L) {
            long lastAt = lastMessageTimes.getOrDefault(sender.getUniqueId(), 0L);
            if (now - lastAt < cooldownMillis) {
                return ChatOperationResult.failure(message("result.too-fast"));
            }
        }

        long duplicateWindowMillis = configuration.duplicateWindowMillis();
        if (duplicateWindowMillis > 0L) {
            String normalized = ChatFormatSupport.normalizeForDuplicateCheck(message);
            DuplicateStamp previous = lastDuplicateStamps.get(sender.getUniqueId());
            if (previous != null && previous.normalizedMessage().equals(normalized) && now - previous.atMillis() < duplicateWindowMillis) {
                return ChatOperationResult.failure(message("result.duplicate"));
            }
        }
        return ChatOperationResult.success("ok");
    }

    String moderateRedPacketWish(String wish) {
        String text = wish == null ? "" : wish.strip();
        if (text.length() > Math.min(255, configuration.maxLength())) return null;
        if (text.isEmpty()) return "";
        ContentModerator.ModerationResult result = contentModerator.filter(text);
        return result.blocked() || result.text().length() > 255 ? null : result.text();
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
            sender.sendMessage(PREFIX + message("result.message-too-long", configuration.maxLength()));
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

        Optional<String> itemJson = itemStackBridge.itemToPreviewJson(itemStack);
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

    private Map<UUID, RouteState> loadDeliveryRoutes(List<UUID> audience, String channelId, UUID senderUuid)
            throws SQLException {
        return loadDeliveryRoutes(audience, channelId, senderUuid, null);
    }

    private Map<UUID, RouteState> loadDeliveryRoutes(List<UUID> audience, String channelId, UUID senderUuid,
                                                     ChatRepository.GuardedRoutes guard) throws SQLException {
        Map<UUID, RouteState> routes = new LinkedHashMap<>();
        for (int start = 0; start < audience.size(); start += 150) {
            Set<UUID> ids = new LinkedHashSet<>(audience.subList(start, Math.min(start + 150, audience.size())));
            routes.putAll(loadRouteChunk(ids, channelId, guard));
        }
        if (senderUuid != null && !routes.containsKey(senderUuid)) {
            routes.putAll(loadRouteChunk(Set.of(senderUuid), channelId, guard));
        }
        return routes;
    }

    private Map<UUID, RouteState> loadRouteChunk(Set<UUID> ids, String channelId, ChatRepository.GuardedRoutes guard)
            throws SQLException {
        String defaultChannel = configuration.defaultChannelId();
        return guard == null ? repository.loadRouteStates(ids, channelId, defaultChannel)
            : guard.loadRouteStates(ids, channelId, defaultChannel);
    }

    private Set<UUID> deliverPreparedPublic(ChatEnvelope envelope, ChatChannelDefinition channel, Player localSender,
            List<ChatConnection> audience, Map<UUID, RouteState> routes, boolean recordRecipients) {
        return deliverPreparedPublic(envelope, channel, localSender, audience, routes, recordRecipients, null);
    }

    private Set<UUID> deliverPreparedPublic(ChatEnvelope envelope, ChatChannelDefinition channel, Player localSender,
            List<ChatConnection> audience, Map<UUID, RouteState> routes, boolean recordRecipients, Set<UUID> sentInAttempt) {
        UUID senderUuid = parseUuid(envelope.senderUuid());
        RouteState senderRoute = routes.get(senderUuid);
        Set<UUID> delivered = recordRecipients ? new LinkedHashSet<>() : Set.of();
        PublicPresentation presentation = new PublicPresentation(envelope, channel);
        boolean party = channel.mode() == ChatChannelMode.PARTY;
        ChatModeration.Seen sharedHistory = party ? null : moderation.describe(channel, envelope, null);
        RegionChatAccess regionAccess = regionChatAccess(channel);
        for (ChatConnection connection : audience) {
            Player recipient = connection.player();
            UUID recipientId = connection.snapshot().playerId();
            if (sentInAttempt != null && sentInAttempt.contains(recipientId)) continue;
            if (!connection.isCurrent() || !canReceiveAdmittedAudience(channel, localSender, senderUuid, recipient)) continue;
            RouteState route = routes.get(recipientId);
            if (!canReceiveRoute(route, senderRoute, senderUuid, recipient)) {
                continue;
            }
            if (regionAccess != null && !regionAccess.canReceivePublic(recipient)) continue;
            if (!sendMentionCardIfNeeded(recipient, envelope, route.player(), presentation)
                && !presentation.sendItemCard(recipient)) {
                presentation.sendText(recipient);
            }
            if (recordRecipients) delivered.add(recipientId);
            if (sentInAttempt != null) sentInAttempt.add(recipientId);
            if (party) moderation.record(recipient, channel, envelope);
            else moderation.record(recipient, sharedHistory);
        }
        sendConsole(envelope.consoleText(), envelope.itemPreview());
        return delivered;
    }

    private void deliverPrivateEnvelope(ChatEnvelope envelope, ChatChannelDefinition channel, Player localSender,
            Player localTarget, List<ChatConnection> audience, Map<UUID, RouteState> routes) {
        deliverPrivateEnvelope(envelope, channel, localSender, localTarget, audience, routes, null);
    }

    private void deliverPrivateEnvelope(ChatEnvelope envelope, ChatChannelDefinition channel, Player localSender,
            Player localTarget, List<ChatConnection> audience, Map<UUID, RouteState> routes, Set<UUID> sentInAttempt) {
        if (localSender != null && localSender.isOnline() && xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(localSender.getUniqueId())) {
            boolean cardSent = sendPrivateCard(localSender, envelope, true);
            if (!cardSent) {
                if (!sendItemPreviewCard(localSender, envelope, privateChannel())) {
                    sendChatComponents(localSender, envelope.renderedText(), envelope.itemPreview(), true);
                }
            }
            moderation.record(localSender, channel, envelope);
        }

        if (localTarget != null && localTarget.isOnline() && AuthAccess.admitted(localTarget.getUniqueId())
                && (sentInAttempt == null || !sentInAttempt.contains(localTarget.getUniqueId()))) {
            boolean cardSent = sendPrivateCard(localTarget, envelope, false);
            if (!cardSent) {
                if (!sendItemPreviewCard(localTarget, envelope, privateChannel())) {
                    sendChatComponents(localTarget, envelope.renderedTargetText(), envelope.itemPreview(), true);
                }
            }
            if (sentInAttempt != null) sentInAttempt.add(localTarget.getUniqueId());
            moderation.record(localTarget, channel, envelope);
        }

        UUID senderUuid = parseUuid(envelope.senderUuid());
        UUID targetUuid = parseUuid(envelope.targetUuid());
        BaseComponent[] spyComponents = null;
        for (ChatConnection connection : audience) {
            Player viewer = connection.player();
            if (sentInAttempt != null && sentInAttempt.contains(viewer.getUniqueId())) continue;
            if (!connection.isCurrent() || !viewer.hasPermission(SOCIAL_SPY_PERMISSION)) continue;
            if (senderUuid != null && senderUuid.equals(viewer.getUniqueId())) {
                continue;
            }
            if (targetUuid != null && targetUuid.equals(viewer.getUniqueId())) {
                continue;
            }
            ChatPlayerState state = routedState(routes, viewer.getUniqueId());
            if (state == null || !state.socialSpyEnabled()) {
                continue;
            }
            if (spyComponents == null) {
                spyComponents = ChatFormatSupport.buildComponents(envelope.renderedSpyText(), envelope.itemPreview(), true);
            }
            viewer.spigot().sendMessage(spyComponents);
            if (sentInAttempt != null) sentInAttempt.add(viewer.getUniqueId());
        }
        sendConsole(envelope.consoleText(), envelope.itemPreview());
    }

    private CompletionStage<Boolean> handleRemotePayload(String payload, String sourceNode, long timestamp) {
        if (payload == null || networkId == null || !payload.startsWith(networkId + "|")) {
            return RELAY_HANDLED;
        }
        return handleRelayPayload(payload.substring(networkId.length() + 1), sourceNode, timestamp);
    }

    private CompletionStage<Boolean> handleRelayPayload(String payload, String sourceNode, long timestamp) {
        if ("axs_chat_policy".equals(payload)) {
            deliveryPolicyVersion.incrementAndGet();
            return RELAY_HANDLED;
        }
        if (payload.startsWith("axs_mod|")) {
            return handleRemoteModeration(parseUuid(payload.substring("axs_mod|".length())), sourceNode);
        }
        if (payload.startsWith(RedPacketService.CROSS_PREFIX)) {
            RedPacketService service = redPacketService;
            if (service != null) {
                service.handleRemotePayload(payload, sourceNode);
            }
            return RELAY_HANDLED;
        }
        return handleRemoteChat(payload, sourceNode, timestamp);
    }

    private CompletionStage<Boolean> handleRemoteChat(String payload, String sourceNode, long timestamp) {
        if (!publicDelivery.hasRoom()) return null;
        ChatEnvelopeCodec.Delivery delivery;
        try {
            delivery = ChatEnvelopeCodec.decodeDelivery(payload);
        } catch (org.bukkit.configuration.InvalidConfigurationException | RuntimeException exception) {
            this.logger.warning("解析跨服聊天消息失败: " + exception.getMessage());
            return RELAY_HANDLED;
        }
        if (!sourceNode.equals(delivery.envelope().originNodeId())) return RELAY_HANDLED;
        return handleRemoteEnvelope(delivery.envelope(), delivery.originRecipients(), timestamp);
    }

    private CompletionStage<Boolean> handleRemoteModeration(UUID id, String sourceNode) {
        if (id == null) return RELAY_HANDLED;
        String key = "mute:" + id + ":" + sourceNode;
        if (processedEnvelopeKeys.containsKey(key)) return RELAY_HANDLED;
        var pending = remoteInFlight.get(key);
        if (pending != null) return pending;

        var finished = new CompletableFuture<Boolean>();
        if (!publicDelivery.offerRemote(() -> {
            ChatModerationReceipt receipt = repository.findModerationReceipt(id).orElse(null);
            if (receipt == null || !receipt.node().equals(sourceNode)) return null;
            if (pendingMuteWrites.containsKey(receipt.playerId())) {
                throw new SQLTransientException("Moderation write pending");
            }
            return new RemoteModeration(receipt, loadActiveMute(receipt.playerId()));
        }, result -> {
            if (result != null && rememberEnvelope(key)) applyRemoteModeration(result);
            remoteInFlight.remove(key);
            finished.complete(true);
        }, () -> {
            remoteInFlight.remove(key);
            finished.complete(false);
        })) return null;
        remoteInFlight.put(key, finished);
        return finished;
    }

    private void applyRemoteModeration(RemoteModeration result) {
        ChatModerationReceipt receipt = result.receipt();
        if (Bukkit.getPlayer(receipt.playerId()) == null) return;
        ChatMuteRecord active = result.active();
        if (active != null && !active.active(Instant.now())) active = null;
        cacheMute(receipt.playerId(), active);
        boolean current = "MUTE".equals(receipt.action())
            ? active != null && active.createdAt().equals(receipt.createdAt()) && active.mutedBy().equals(receipt.operator())
            : "UNMUTE".equals(receipt.action()) && active == null;
        if (current) notifyMuteChange(receipt, active);
    }

    private record RemoteModeration(ChatModerationReceipt receipt, ChatMuteRecord active) {}

    private CompletionStage<Boolean> handleRemoteEnvelope(ChatEnvelope envelope, Set<UUID> originRecipients,
                                                            long timestamp) {
        if (envelope == null || processedEnvelopeKeys.containsKey(envelope.dedupeKey())) return RELAY_HANDLED;
        CompletableFuture<Boolean> pending = remoteInFlight.get(envelope.dedupeKey());
        if (pending != null) return pending;
        ChatChannelDefinition channel = configuration.channel(envelope.channelId());
        UUID senderId = parseUuid(envelope.senderUuid());
        if (channel == null || !channel.permitsCrossServer() || senderId == null
                || !channel.audienceKey().equals(envelope.audienceKey())
                || envelope.privateMessage() != (channel.mode() == ChatChannelMode.PRIVATE)
                || envelope.staffMessage() != (channel.mode() == ChatChannelMode.STAFF)) {
            return RELAY_HANDLED;
        }
        Set<UUID> recoveryAudience = null;
        if (chatRelay != null && chatRelay.isRecovery(timestamp)) {
            if (chatRelay.waitingForRejoin(admittedAudience.keySet())) return null;
            recoveryAudience = chatRelay.previousAudience();
        }
        if (envelope.privateMessage()) return queueRemotePrivate(envelope, channel, senderId, recoveryAudience);

        List<ChatConnection> audience = captureAudience(channel, null, senderId, originRecipients);
        restrictRecoveredAudience(audience, recoveryAudience);
        Set<UUID> sentInAttempt = new java.util.HashSet<>();
        return queueRemoteDelivery(envelope, channel, senderId, audience,
            routes -> deliverPreparedPublic(envelope, channel, null, audience, routes, false, sentInAttempt));
    }

    private CompletionStage<Boolean> queueRemotePrivate(ChatEnvelope envelope, ChatChannelDefinition channel,
                                                         UUID senderId, Set<UUID> recoveryAudience) {
        UUID targetId = parseUuid(envelope.targetUuid());
        if (targetId == null || (recoveryAudience != null && !recoveryAudience.contains(targetId))
                || !canReceiveChannel(channel, Bukkit.getPlayer(targetId))) return RELAY_HANDLED;
        List<ChatConnection> audience = captureAudience(null, null, senderId, Set.of());
        restrictRecoveredAudience(audience, recoveryAudience);
        Set<UUID> sentInAttempt = new java.util.HashSet<>();
        return queueRemoteDelivery(envelope, channel, senderId, audience, routes -> {
            Player target = onlineRecipient(audience, targetId);
            if (!canReceiveChannel(channel, target)) return;
            ChatPlayerState targetState = routedState(routes, targetId);
            ChatPlayerState senderState = routedState(routes, senderId);
            if (targetState == null || senderState == null || !targetState.acceptsPrivateMessages()
                || targetState.ignores(senderId) || senderState.ignores(targetId)) return;
            deliverPrivateEnvelope(envelope, channel, null, target, audience, routes, sentInAttempt);
            replyTargets.put(targetId, new ReplyTarget(senderId, envelope.senderName()));
            refreshTab(target, "chat-remote-msg");
        });
    }

    private static void restrictRecoveredAudience(List<ChatConnection> audience, Set<UUID> recoveryAudience) {
        if (recoveryAudience != null) {
            audience.removeIf(connection -> !recoveryAudience.contains(connection.snapshot().playerId()));
        }
    }

    private CompletionStage<Boolean> queueRemoteDelivery(ChatEnvelope envelope, ChatChannelDefinition channel,
            UUID senderId, List<ChatConnection> audience, Consumer<Map<UUID, RouteState>> deliver) {
        List<UUID> ids = audience.stream().map(connection -> connection.snapshot().playerId()).toList();
        String key = envelope.dedupeKey();
        var receipt = new CompletableFuture<Boolean>();
        Consumer<Map<UUID, RouteState>> complete = routes -> {
            deliver.accept(routes);
            rememberEnvelope(key);
            remoteInFlight.remove(key);
            receipt.complete(true);
        };
        Runnable abandoned = () -> {
            remoteInFlight.remove(key);
            receipt.complete(false);
        };
        boolean offered;
        if (guardRemoteIgnores && !ids.isEmpty()) {
            Set<UUID> actors = new LinkedHashSet<>(ids);
            actors.add(senderId);
            offered = publicDelivery.offerGuardedRemote(actors, ids.size(),
                guard -> actors.size() <= 150 ? loadRouteChunk(actors, channel.id(), guard)
                    : loadDeliveryRoutes(ids, channel.id(), senderId, guard),
                complete, abandoned);
        } else {
            offered = publicDelivery.offerRemote(() -> loadDeliveryRoutes(ids, channel.id(), senderId), complete, abandoned);
        }
        if (!offered) return null;
        remoteInFlight.put(key, receipt);
        return receipt;
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
            this.logger.warning("注册 Paper AsyncChatEvent 监听失败，已回退到 Bukkit 旧聊天事件: " + exception.getMessage());
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
        ChatReloadGate gate = reloadGate;
        if (gate != null && gate.reject(event)) return;
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
            this.logger.warning("处理 Paper AsyncChatEvent 失败: " + exception.getMessage());
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
            this.logger.warning("强制取消 Paper AsyncChatEvent 失败: " + exception.getMessage());
        }
    }

    private void forceCancelLegacyChatEvent(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        try {
            event.getRecipients().clear();
        } catch (UnsupportedOperationException ignored) {
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("清空 Bukkit 聊天接收者失败: " + exception.getMessage());
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
            this.logger.info("@补全: 已启用 (Paper API)");
            return;
        } catch (NoSuchMethodException ignored) {}

        // Strategy 2: NMS ClientboundCustomChatCompletionsPacket (Spigot / Mohist 1.19.1+)
        try {
            Class<?> packetClass = tryLoadClass(
                "net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket",
                "net.minecraft.class_2797" // Mohist/Arclight SRG 映射名
            );
            if (packetClass == null) throw new ClassNotFoundException("ClientboundCustomChatCompletionsPacket");
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
            this.logger.info("@补全: 已启用 (NMS 发包)");
        } catch (Exception ex) {
            this.logger.warning("@补全: 不可用 (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
        }
    }

    @FunctionalInterface
    private interface NmsPacketSender {
        void send(Player player, Object packet) throws Exception;
    }

    private static Class<?> tryLoadClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
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
                    if (!player.isOnline() || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
                    Collection<String> newEntry = List.of("@" + player.getName());
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) continue;
                        if (!online.getUniqueId().equals(player.getUniqueId())) {
                            completionAdder.accept(online, newEntry);
                        }
                    }
                    List<String> existing = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) continue;
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
                    this.logger.warning("@补全更新失败: " + ex.getMessage());
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
                    if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) continue;
                    names.add(online.getName());
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) continue;
                    packetBridge.sendPacket(online, completionUiId, "updatePlayers", names);
                }
            } catch (Exception ex) {
                if (configuration.debug()) {
                    this.logger.warning("@补全玩家列表广播失败: " + ex.getMessage());
                }
            }
        }, 5L);
    }

    private void scheduleChatMessage(Player player, String message) {
        ChatConnection sender = new ChatConnection(player, AuthAccess.snapshot(player.getUniqueId()));
        if (!sender.snapshot().admitted()) return;
        queueChatMessage(sender, message, null, result -> {
            if (sender.isCurrent() && !result.success() && !result.cardNotified()) {
                player.sendMessage(PREFIX + ChatColor.RED + result.message());
            }
        });
    }

    private void withdrawAudience(UUID id) {
        if (admittedAudience.remove(id) != null) {
            publishAudience();
            if (chatRelay != null) chatRelay.audienceChanged(admittedAudience.keySet());
        }
    }

    private void publishAudience() {
        publishedAudience = new ChatAudience(List.copyOf(admittedAudience.values()), List.copyOf(admittedAudience.keySet()));
    }

    private void queueChatMessage(ChatConnection sender, String rawMessage, ChatChannelDefinition selected,
                                  Consumer<ChatOperationResult> result) {
        queueChatMessage(sender, rawMessage, selected, null, result);
    }

    private void queueChatMessage(ChatConnection sender, String rawMessage, ChatChannelDefinition selected,
                                  PrivateRecipient privateRecipient, Consumer<ChatOperationResult> result) {
        boolean nativeInput = selected == null && privateRecipient == null;
        if (!sender.snapshot().admitted() || (!nativeInput && !sender.isCurrent())) return;
        String text = rawMessage == null ? "" : rawMessage.trim();
        if (text.isBlank() || text.length() > configuration.maxLength()) {
            onPrimary(() -> {
                if (!sender.isCurrent()) return;
                sanitizeAndValidateOutgoingMessage(sender.player(), text);
                result.accept(ChatOperationResult.failure(message("result.empty-message")));
            });
            return;
        }
        UUID id = sender.snapshot().playerId();
        long channelAtSubmission = channelChanges.getOrDefault(id, 0L);
        String targetName = privateRecipient == null ? null : privateRecipient.name();
        boolean reply = privateRecipient != null && privateRecipient.reply();
        ChatAudience snapshot = publishedAudience;
        List<ChatConnection> audience = nativeInput ? snapshot.connections()
            : captureAudience(privateRecipient == null ? selected : null, sender.player(), id, Set.of());
        List<UUID> ids = nativeInput ? snapshot.playerIds()
            : audience.stream().map(connection -> connection.snapshot().playerId()).toList();
        if (!publicDelivery.offer(() -> {
            ChatChannelDefinition channel = selected;
            if (channel == null) {
                ChatPlayerState state = repository.loadState(id, configuration.defaultChannelId());
                channel = configuration.channel(state.currentChannelId());
            }
            if (channel == null) return new PreparedChat(null, null, Map.of(), null, "result.no-channel", null);
            ChatMuteRecord mute = repository.loadMute(id).orElse(null);
            if (channel.mode() == ChatChannelMode.PRIVATE) {
                UUID targetId = targetName == null ? replyTargetId(id) : null;
                if (targetName == null && targetId == null) {
                    String error = reply ? "result.no-reply-target" : "result.private-no-target";
                    return new PreparedChat(channel, mute, Map.of(), null, error, null);
                }
                ChatPlayerProfile exactLocal = privateRecipient == null ? null : privateRecipient.exactLocal();
                ChatPlayerProfile target = exactLocal == null ? loadPrivateProfile(targetName, targetId) : exactLocal;
                if (target == null) {
                    String error = targetName != null ? "result.player-not-found"
                        : reply ? "result.reply-target-missing" : "result.private-target-unavailable";
                    return new PreparedChat(channel, mute, Map.of(), null, error, targetId);
                }
                return new PreparedChat(channel, mute, loadDeliveryRoutes(ids, channel.id(), id), target, null, targetId);
            }
            return new PreparedChat(channel, mute, loadDeliveryRoutes(ids, channel.id(), id), null, null, null);
        }, prepared -> targetName != null || prepared.channel() == null || prepared.channel().mode() != ChatChannelMode.PRIVATE
            || Objects.equals(prepared.replyTarget(), replyTargetId(id)), prepared -> !pendingMuteWrites.containsKey(id) && ignoreChangesSettled(prepared), (prepared, failure) -> {
            if (!sender.isCurrent()) return;
            if (selected == null && channelChanges.getOrDefault(id, 0L) != channelAtSubmission) {
                result.accept(ChatOperationResult.failure(message("result.channel-changed-while-sending")));
                return;
            }
            if (failure != null) {
                logStorageFailure(failure);
                result.accept(storageUnavailable());
            } else if (prepared.error() != null) {
                result.accept(ChatOperationResult.failure(message(prepared.error(), targetName)));
            } else if (prepared.channel().mode() == ChatChannelMode.PRIVATE) {
                result.accept(dispatchPrivateMessage(sender.player(), prepared.target(), text, prepared.channel(),
                    reply, prepared.mute(), audience, prepared.routes()));
            } else {
                result.accept(dispatchChannelMessage(sender.player(), text, prepared.channel(), prepared.mute(), audience, prepared.routes()));
            }
        })) onPrimary(() -> {
            if (sender.isCurrent()) result.accept(storageUnavailable());
        });
    }

    private boolean ignoreChangesSettled(PreparedChat prepared) {
        for (UUID owner : ignoreWritesPending) {
            if (prepared.routes().containsKey(owner)
                || (prepared.target() != null && prepared.target().playerUuid().equals(owner))) return false;
        }
        return true;
    }

    void onPrimary(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    private ChatPlayerProfile loadPrivateProfile(String name, UUID targetId) throws SQLException {
        if (targetId != null) {
            ChatPlayerProfile known = profiles.get(targetId);
            return known == null ? repository.loadProfile(targetId).orElse(null) : known;
        }
        return repository.findProfileByName(name).orElse(null);
    }

    private static ChatPlayerState routedState(Map<UUID, RouteState> routes, UUID id) {
        RouteState route = routes.get(id);
        return route == null ? null : route.player();
    }

    private static Player onlineRecipient(List<ChatConnection> audience, UUID id) {
        for (ChatConnection connection : audience) {
            if (connection.snapshot().playerId().equals(id) && connection.isCurrent()) return connection.player();
        }
        return null;
    }

    private List<ChatConnection> captureAudience(ChatChannelDefinition channel, Player sender, UUID senderId,
                                                 Set<UUID> seenAtOrigin) {
        List<ChatConnection> audience = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (seenAtOrigin.contains(id)) continue;
            AuthSnapshot snapshot = AuthAccess.snapshot(id);
            if (snapshot.admitted() && (channel == null || canReceiveAdmittedAudience(channel, sender, senderId, player))) {
                audience.add(new ChatConnection(player, snapshot));
            }
        }
        return audience;
    }

    private record PreparedChat(ChatChannelDefinition channel, ChatMuteRecord mute, Map<UUID, RouteState> routes,
                                ChatPlayerProfile target, String error, UUID replyTarget) {}

    private record PrivateRecipient(String name, boolean reply, ChatPlayerProfile exactLocal) {}

    private record ChatAudience(List<ChatConnection> connections, List<UUID> playerIds) {
        private static final ChatAudience EMPTY = new ChatAudience(List.of(), List.of());
    }

    private record ChatConnection(Player player, AuthSnapshot snapshot) {
        boolean isCurrent() {
            UUID id = snapshot.playerId();
            return player.isOnline() && Bukkit.getPlayer(id) == player && isAdmitted();
        }

        boolean isAdmitted() {
            if (!snapshot.admitted()) return false;
            AuthSnapshot now = AuthAccess.snapshot(snapshot.playerId());
            return now.admitted() && Objects.equals(now.connectionId(), snapshot.connectionId())
                && now.generation() == snapshot.generation();
        }
    }

    boolean routeRedPacket(ChatChannelDefinition channel, Player sender, UUID senderId, Player singleViewer,
                           Consumer<List<Player>> deliver) {
        List<ChatConnection> audience = singleViewer == null ? captureAudience(channel, sender, senderId, Set.of())
            : List.of(new ChatConnection(singleViewer, AuthAccess.snapshot(singleViewer.getUniqueId())));
        if (audience.isEmpty()) {
            deliver.accept(List.of());
            return true;
        }
        List<UUID> ids = audience.stream().map(connection -> connection.snapshot().playerId()).toList();
        return publicDelivery.offer(() -> loadDeliveryRoutes(ids, channel.id(), senderId), routes -> true,
            routes -> ignoreWritesPending.stream().noneMatch(routes::containsKey), (routes, failure) -> {
                if (failure != null) {
                    logStorageFailure(failure);
                    deliver.accept(List.of());
                    return;
                }
                RegionChatAccess regionAccess = regionChatAccess(channel);
                RouteState source = senderId == null ? null : routes.get(senderId);
                List<Player> recipients = new ArrayList<>();
                for (ChatConnection connection : audience) {
                    Player viewer = connection.player();
                    if (connection.isCurrent() && canReceiveChannel(channel, viewer)
                        && canReceiveAdmittedAudience(channel, sender, senderId, viewer)
                        && canReceiveRoute(routes.get(viewer.getUniqueId()), source, senderId, viewer)
                        && (regionAccess == null || regionAccess.canReceivePublic(viewer))) {
                        recipients.add(viewer);
                    }
                }
                deliver.accept(recipients);
            });
    }

    private boolean canReceiveRoute(RouteState route, RouteState senderRoute, UUID senderUuid, Player recipient) {
        if (route == null) return false;
        if (recipient.getUniqueId().equals(senderUuid)) return true;
        return route.subscribed() && !route.quiet() && (senderUuid == null
            || (senderRoute != null && !route.player().ignores(senderUuid) && !senderRoute.player().ignores(recipient.getUniqueId())));
    }

    private boolean canReceiveAudience(ChatChannelDefinition channel, Player localSender, UUID senderUuid, Player recipient) {
        if (recipient == null || !recipient.isOnline()) {
            return false;
        }
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(recipient.getUniqueId())) return false;
        return canReceiveAdmittedAudience(channel, localSender, senderUuid, recipient);
    }

    private boolean canReceiveAdmittedAudience(ChatChannelDefinition channel, Player localSender,
                                               UUID senderUuid, Player recipient) {
        if (channel.mode() == ChatChannelMode.PARTY) {
            AbyssPartyAccess.PartySnapshot source = partyOf(localSender);
            AbyssPartyAccess.PartySnapshot target = partyOf(recipient);
            if (source == null || target == null || !source.id().equals(target.id())) return false;
        }
        if (senderUuid != null && senderUuid.equals(recipient.getUniqueId())) {
            return true;
        }
        if (!channel.receivePermission().isBlank() && !recipient.hasPermission(channel.receivePermission())) return false;
        if (channel.range() > 0.0D) {
            if (localSender == null || channel.mode() != ChatChannelMode.NORMAL) return false;
            if (!Objects.equals(localSender.getWorld(), recipient.getWorld())) {
                return false;
            }
            return recipient.getLocation().distanceSquared(localSender.getLocation()) <= channel.range() * channel.range();
        }
        return true;
    }

    boolean canReceiveChannel(ChatChannelDefinition channel, Player recipient) {
        return recipient != null && recipient.isOnline()
            && xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(recipient.getUniqueId())
            && (channel.receivePermission().isBlank() || recipient.hasPermission(channel.receivePermission()));
    }

    private boolean sendMentionCardIfNeeded(Player recipient, ChatEnvelope envelope, ChatPlayerState recipientState,
                                             PublicPresentation presentation) {
        if (recipient == null || envelope.privateMessage() || !configuration.functions().mentionEnabled()) {
            return false;
        }
        if (recipientState == null || !recipientState.acceptsMentions()) {
            return false;
        }
        boolean mentioned = presentation.mentions(recipient);
        if (!mentioned) {
            return false;
        }
        UUID id = recipient.getUniqueId();
        if (!id.toString().equals(envelope.senderUuid())) {
            queueUnreadMention(id);
        }
        String cardId = configuration.cards().mentionCardId();
        if (cardId.isBlank()) {
            return false;
        }
        return packetBridge.sendChatCard(recipient, cardId, presentation.mentionPayload());
    }

    private Map<String, String> mentionPayload(ChatEnvelope envelope, ChatChannelDefinition channel) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("channel", channel.displayName());
        payload.put("channelId", channel.id());
        List<String> lines = wrapMessage(envelope.rawMessage());
        for (int i = 0; i < lines.size(); i++) {
            payload.put("msg" + i, lines.get(i));
        }
        return payload;
    }

    public ChatOperationResult channelPreference(Player player, String channelId, boolean quietField, boolean value) {
        ChatChannelDefinition channel = configuration.channel(channelId);
        if (channel == null || channel.mode() == ChatChannelMode.PRIVATE
                || !canReceiveChannel(channel, player)
                || (channel.mode() == ChatChannelMode.PARTY && partyOf(player) == null)) {
            return ChatOperationResult.failure(message("result.unknown-channel", channelId));
        }
        try {
            repository.setChannelPreference(player.getUniqueId(), channel.id(), quietField, value);
            deliveryPolicyChanged();
            return ChatOperationResult.success(message(quietField ? "links.quiet-changed" : "links.subscription-changed",
                channel.displayName(), value ? "开启" : "关闭"));
        } catch (SQLException exception) {
            logStorageFailure(exception);
            return storageUnavailable();
        }
    }

    private void queueUnreadMention(UUID id) {
        ioExecutor.execute(() -> {
            try {
                repository.addUnreadMention(id);
            } catch (SQLException exception) {
                logStorageFailure(exception);
            }
        });
    }

    public void readMentions(Player player) {
        UUID id = player.getUniqueId();
        var session = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id);
        ioExecutor.execute(() -> {
            if (!session.admitted()) return;
            String reply;
            try {
                reply = message("links.mentions-read", repository.takeUnreadMentions(id));
            } catch (SQLException exception) {
                logStorageFailure(exception);
                reply = message("result.storage-unavailable");
            }
            String text = reply;
            Bukkit.getScheduler().runTask(plugin, () -> {
                var current = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id);
                if (player.isOnline() && current.admitted() && current.generation() == session.generation()
                        && Objects.equals(current.connectionId(), session.connectionId())) player.sendMessage(PREFIX + text);
            });
        });
    }

    private boolean sendPrivateCard(Player recipient, ChatEnvelope envelope, boolean senderSide) {
        String cardId = configuration.cards().privateCardId();
        if (cardId.isBlank()) {
            return false;
        }
        List<String> lines = wrapMessage(envelope.rawMessage());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("targetName", envelope.targetName());
        payload.put("direction", senderSide ? "sender" : "recipient");
        for (int i = 0; i < lines.size(); i++) {
            payload.put("msg" + i, lines.get(i));
        }
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
        return packetBridge.sendChatCard(recipient, cardId, itemPreviewPayload(envelope, channel));
    }

    private Map<String, String> itemPreviewPayload(ChatEnvelope envelope, ChatChannelDefinition channel) {
        String itemName = ChatColor.stripColor(envelope.itemPreview().displayText());

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("senderName", envelope.senderName());
        payload.put("senderDisplayName", envelope.senderDisplayName());
        payload.put("itemName", itemName);
        payload.put("itemAmount", Integer.toString(envelope.itemPreview().amount()));
        payload.put("itemMaterial", envelope.itemPreview().materialKey());
        payload.put("channel", channel == null ? envelope.channelId() : channel.displayName());
        payload.put("itemJson", envelope.itemPreview().itemJson());
        return payload;
    }

    /** Lazily builds presentation shared by the recipients of this one envelope. */
    private final class PublicPresentation {
        private final ChatEnvelope envelope;
        private final ChatChannelDefinition channel;
        private Set<String> mentionedNames;
        private Map<String, String> mention;
        private Map<String, String> item;
        private BaseComponent[] components;

        PublicPresentation(ChatEnvelope envelope, ChatChannelDefinition channel) {
            this.envelope = envelope;
            this.channel = channel;
        }

        boolean mentions(Player player) {
            if (envelope.mentionAll()) return true;
            if (mentionedNames == null) {
                mentionedNames = new java.util.HashSet<>();
                for (String name : envelope.mentionedNames()) mentionedNames.add(name.toLowerCase(Locale.ROOT));
                if (!nodeId().equals(envelope.originNodeId())) {
                    mentionedNames.addAll(ChatMentionSupport.parseMentions(envelope.rawMessage(), onlinePlayerNames(), false)
                        .mentionedNames());
                }
            }
            return mentionedNames.contains(player.getName().toLowerCase(Locale.ROOT));
        }

        Map<String, String> mentionPayload() {
            if (mention == null) mention = java.util.Collections.unmodifiableMap(ChatService.this.mentionPayload(envelope, channel));
            return mention;
        }

        boolean sendItemCard(Player player) {
            String cardId = configuration.cards().itemPreviewCardId();
            if (cardId.isBlank() || envelope.itemPreview() == null || envelope.itemPreview().itemJson().isBlank()) return false;
            if (item == null) item = java.util.Collections.unmodifiableMap(itemPreviewPayload(envelope, channel));
            return packetBridge.sendChatCard(player, cardId, item);
        }

        void sendText(Player player) {
            if (components == null) {
                components = ChatFormatSupport.buildComponents(envelope.renderedText(), envelope.itemPreview(), true);
            }
            player.spigot().sendMessage(components);
        }
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
        return packetBridge.sendChatCard(recipient, cardId, payload);
    }

    private void sendChatComponents(Player recipient, String renderedText, ChatItemPreview itemPreview, boolean hoverItem) {
        recipient.spigot().sendMessage(ChatFormatSupport.buildComponents(renderedText, itemPreview, hoverItem));
    }

    private void sendConsole(String consoleText, ChatItemPreview itemPreview) {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(ChatColor.stripColor(ChatFormatSupport.plainText(consoleText, itemPreview)));
    }

    ChatChannelDefinition channel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return configuration.channel(configuration.defaultChannelId());
        }
        return configuration.channel(channelId);
    }

    /** 玩家选中的频道。无效的选择需要玩家重新切换。 */
    long channelRevision(UUID playerId) {
        return channelChanges.getOrDefault(playerId, 0L);
    }

    /** 向跨服通道发布自定义负载（红包等非聊天信封消息）。 */
    void publishCrossServer(String payload) {
        ChatRelay relay = chatRelay;
        if (relay != null && ChatRelay.fits(payload)) {
            relay.publish(UUID.randomUUID(), payload).whenComplete((accepted, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(accepted)) {
                    logger.warning("Chat 跨服状态通知未入队");
                }
            });
        } else {
            CrossServerChannel channel = crossServerChannel;
            String network = networkId;
            if (channel != null && channel.isActive() && network != null) {
                channel.publish(network + "|" + payload);
            }
        }
    }

    private void publishChatEnvelope(Player sender, ChatEnvelope envelope, Set<UUID> originRecipients) {
        publishChatEnvelope(sender, envelope, originRecipients, null);
    }

    private void publishChatEnvelope(Player sender, ChatEnvelope envelope, Set<UUID> originRecipients,
                                     Runnable acceptedAction) {
        CrossServerChannel channel = crossServerChannel;
        if (channel == null || networkId == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + message("result.relay-unavailable"));
            return;
        }
        ChatConnection connection = new ChatConnection(sender, AuthAccess.snapshot(sender.getUniqueId()));
        ChatRelay relay = chatRelay;
        String payload = ChatEnvelopeCodec.encodeForTransport(envelope, originRecipients);
        String network = networkId;
        CompletionStage<Boolean> receipt;
        if (relay != null && ChatRelay.fits(payload)) {
            receipt = relay.publish(UUID.fromString(envelope.messageId()), payload);
        } else if (relay != null) {
            // Messages above the journal size limit require durable Redis delivery.
            receipt = channel.publishDurably(network + "|" + payload);
        } else {
            receipt = channel.publishWithReceipt(network + "|" + payload);
        }
        receipt
            .whenComplete((accepted, failure) -> {
                boolean submitted = failure == null && Boolean.TRUE.equals(accepted);
                if (submitted && acceptedAction == null) return;
                onPrimary(() -> {
                    if (crossServerChannel == channel && connection.isCurrent()) {
                        if (submitted) acceptedAction.run();
                        else sender.sendMessage(PREFIX + ChatColor.RED + message("result.relay-unavailable"));
                    }
                });
            });
    }

    private void deliveryPolicyChanged() {
        deliveryPolicyVersion.incrementAndGet();
        publishCrossServer("axs_chat_policy");
    }

    private ChatChannelDefinition privateChannel() {
        ChatChannelDefinition exact = configuration.channel("private");
        if (exact != null && exact.mode() == ChatChannelMode.PRIVATE) {
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
        try {
            ChatPlayerState loaded = repository.loadState(playerUuid, configuration.defaultChannelId());
            states.put(playerUuid, loaded);
            return loaded;
        } catch (SQLException exception) {
            states.remove(playerUuid);
            logStorageFailure(exception);
            return null;
        }
    }

    private ChatMuteRecord loadActiveMute(UUID playerUuid) throws SQLException {
        return repository.loadMute(playerUuid).filter(record -> record.active(Instant.now())).orElse(null);
    }

    private void cacheMute(UUID playerUuid, ChatMuteRecord mute) {
        if (mute == null) mutes.remove(playerUuid);
        else mutes.put(playerUuid, mute);
    }

    private ChatOperationResult storageUnavailable() {
        return ChatOperationResult.failure(message("result.storage-unavailable"));
    }

    void logStorageFailure(SQLException exception) {
        logger.warning("聊天存储操作失败: " + exception.getMessage());
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
                nodeId()
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
            this.logger.warning("按名称查找聊天玩家档案失败: " + exception.getMessage());
        }
        return resolveOfflineProfileByName(playerName);
    }

    private ChatPlayerProfile resolveOfflineProfileByName(String playerName) {
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

    private void touchProfile(Player player, ChatConnection joining) {
        ChatPlayerProfile profile = new ChatPlayerProfile(
            player.getUniqueId(),
            player.getName(),
            Instant.now(),
            nodeId()
        );
        profiles.put(profile.playerUuid(), profile);
        ioExecutor.execute(() -> {
            saveProfile(profile);
            if (joining != null && !isStopping() && joining.isAdmitted()) warmDisplayState(joining);
        });
    }

    private void warmDisplayState(ChatConnection connection) {
        UUID id = connection.snapshot().playerId();
        ChatPlayerState previous = states.get(id);
        long version = deliveryPolicyVersion.get();
        try {
            ChatPlayerState state = repository.loadState(id, configuration.defaultChannelId());
            ChatMuteRecord mute = loadActiveMute(id);
            if (isStopping()) return;
            onPrimary(() -> {
                if (isStopping() || !connection.isCurrent()) return;
                if (previous == null) states.putIfAbsent(id, state);
                else states.replace(id, previous, state);
                if (deliveryPolicyVersion.get() == version) cacheMute(id, mute);
            });
        } catch (SQLException exception) {
            logStorageFailure(exception);
        }
    }

    private void runCleanup() {
        long now = System.currentTimeMillis();
        if (links != null) links.prune();
        expireTimestamps(processedEnvelopeKeys, now - ENVELOPE_TTL_MILLIS);
        long duplicateCutoff = now - Math.max(ENVELOPE_TTL_MILLIS, configuration.duplicateWindowMillis());
        for (var entry : lastDuplicateStamps.entrySet()) {
            DuplicateStamp stamp = entry.getValue();
            if (stamp.atMillis() < duplicateCutoff) lastDuplicateStamps.remove(entry.getKey(), stamp);
        }
        long cooldownCutoff = now - Math.max(ENVELOPE_TTL_MILLIS, configuration.cooldownMillis() + ENVELOPE_TTL_MILLIS);
        expireTimestamps(lastMessageTimes, cooldownCutoff);
        if (!mutes.isEmpty()) {
            Instant cutoff = Instant.now();
            for (var entry : mutes.entrySet()) {
                ChatMuteRecord mute = entry.getValue();
                if (!mute.active(cutoff)) mutes.remove(entry.getKey(), mute);
            }
        }
        ioExecutor.execute(expiredMuteCleanup);
        // 清理离线玩家的 profile 缓存，保留在线玩家
        for (var entry : profiles.entrySet()) {
            UUID id = entry.getKey();
            if (Bukkit.getPlayer(id) == null) profiles.remove(id, entry.getValue());
        }
    }

    private static void expireTimestamps(Map<?, Long> timestamps, long cutoff) {
        for (var entry : timestamps.entrySet()) {
            Long timestamp = entry.getValue();
            if (timestamp < cutoff) timestamps.remove(entry.getKey(), timestamp);
        }
    }

    private void deleteExpiredMutes() {
        try {
            repository.deleteExpiredMutes(Instant.now());
        } catch (SQLException exception) {
            logStorageFailure(exception);
        }
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

    private boolean saveState(ChatPlayerState state, StateField field) {
        try {
            repository.saveState(state, field);
            deliveryPolicyChanged();
        } catch (SQLException exception) {
            logStorageFailure(exception);
            return false;
        }
        state(state.playerUuid());
        return true;
    }

    private void saveProfileAsync(ChatPlayerProfile profile) {
        ioExecutor.execute(() -> saveProfile(profile));
    }

    private void saveProfile(ChatPlayerProfile profile) {
        try {
            repository.upsertProfile(profile);
        } catch (SQLException exception) {
            this.logger.warning("保存聊天玩家档案失败: " + exception.getMessage());
        }
    }

    private void refreshTab(Player viewer, String reason) {
        TabRefreshable tabRefreshable = tabRefreshableProvider == null ? null : tabRefreshableProvider.get();
        if (tabRefreshable == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        tabRefreshable.requestViewerRefresh(viewer, reason);
    }

    String nodeId() {
        return crossServer.nodeId();
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

    private String resolveItemName(ItemStack itemStack) {
        if (vanillaItemNameBridge != null) {
            return ChatColor.stripColor(vanillaItemNameBridge.resolveDisplayName(itemStack));
        }
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

    /**
     * 按配置的 maxLineWidth 换行消息文本，返回各行列表。
     * 卡片宽度与高度由 UI 侧根据 Text 控件实际渲染尺寸自适应。
     */
    private List<String> wrapMessage(String text) {
        ChatCardConfiguration c = configuration.cards();
        int textWidth = measureTextWidth(text, c.charWidthFull(), c.charWidthHalf());
        if (textWidth <= c.maxLineWidth()) {
            return List.of(text);
        }
        return wrapText(text, c.maxLineWidth(), c.charWidthFull(), c.charWidthHalf());
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
