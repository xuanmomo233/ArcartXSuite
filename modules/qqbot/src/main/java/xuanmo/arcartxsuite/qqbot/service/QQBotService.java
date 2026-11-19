package xuanmo.arcartxsuite.qqbot.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotGroupConfig;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotEvent;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;

public final class QQBotService implements Listener {

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final MessageProvider messages;
    private volatile OneBotClient client;
    private final QQBotBindService bindService;
    private final QQBotRepository repository;
    private final PlaceholderResolverAPI placeholderResolver;
    private volatile QQBotCommandRouter commandRouter;
    private volatile QQBotSignInService signInService;
    private final Logger logger;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailProvider;
    private final Map<String, Long> processedMessageIds = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;
    private volatile boolean connected;
    private volatile boolean disconnectWarned;
    // 连续重连失败计数（连接成功后清零）。用于抑制刷屏：首次告警后，
    // 每达到 RECONNECT_FAIL_LOG_INTERVAL 次才打印一次摘要。
    private volatile int reconnectFailCount;
    /** 断开告警末尾追加的提示（如 SnowLuma 未安装/未运行的指引），由模块注入 */
    private volatile java.util.function.Supplier<String> disconnectHint;
    /** 首次告警后，每隔多少次重连失败才打印一次摘要（避免每 10 秒刷一条） */
    private static final int RECONNECT_FAIL_LOG_INTERVAL = 6;
    private GroupMessageListener groupMessageListener;
    private final java.util.concurrent.CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener> eventListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<Long, Long> groupLastAutoReply = new ConcurrentHashMap<>();
    private volatile QQBotMessageGateway messageGateway;
    private volatile boolean closed = true;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor io = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(
        0, 1, 5, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(64), task -> {
            var thread = new Thread(task, "QQBot-Data");
            thread.setDaemon(true);
            return thread;
        });

    public QQBotService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        QQBotBindService bindService,
        QQBotRepository repository,
        Logger logger,
        PlaceholderResolverAPI placeholderResolver,
        MessageProvider messages,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailProvider
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.bindService = bindService;
        this.repository = repository;
        this.logger = logger;
        this.placeholderResolver = placeholderResolver;
        this.mailProvider = mailProvider;
    }

    /**
     * 注入 OneBot 客户端（解决 service ↔ client 循环引用）。
     * 必须在 {@link #start()} 之前调用。
     */
    public void setClient(OneBotClient client) {
        this.client = client;
        this.messageGateway = new QQBotMessageGateway(plugin, config, repository, client);
        this.commandRouter = new QQBotCommandRouter(plugin, config, client, bindService, repository, logger, placeholderResolver, mailProvider);
    }

    /**
     * 设置断开告警附加提示提供者（返回 null 表示无附加提示）。
     */
    public void setDisconnectHint(java.util.function.Supplier<String> disconnectHint) {
        this.disconnectHint = disconnectHint;
    }

    public void start() {
        closed = false;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Keep a bounded replay window; binding codes belong to the host authentication service.
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // 清理已处理消息 ID 缓存
            long now = System.nanoTime() / 1_000_000L;
            processedMessageIds.entrySet().removeIf(entry -> now - entry.getValue() >= 300000L);
        }, 6000L, 6000L); // 每5分钟
    }

    public void shutdown() {
        closed = true;
        connected = false;
        io.shutdown();
        if (commandRouter != null) commandRouter.close();
        if (messageGateway != null) messageGateway.close();
        eventListeners.clear();
        groupMessageListener = null;
        HandlerList.unregisterAll(this);
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        processedMessageIds.clear();
    }

    public <T> java.util.concurrent.CompletableFuture<T> submitIo(java.util.function.Supplier<T> task) {
        var result = new java.util.concurrent.CompletableFuture<T>();
        if (closed) {
            result.completeExceptionally(new java.util.concurrent.RejectedExecutionException("QQBot stopped"));
            return result;
        }
        try {
            io.execute(() -> {
                try {
                    if (closed) throw new java.util.concurrent.RejectedExecutionException("QQBot stopped");
                    result.complete(task.get());
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (java.util.concurrent.RejectedExecutionException failure) { result.completeExceptionally(failure); }
        return result;
    }

    public java.util.concurrent.CompletionStage<Void> stopped() {
        return java.util.concurrent.CompletableFuture.allOf(io.stopped().toCompletableFuture(),
            commandRouter == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : commandRouter.stopped().toCompletableFuture(),
            messageGateway == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : messageGateway.stopped().toCompletableFuture());
    }

    public void onBotConnected() {
        if (closed) return;
        connected = true;
        disconnectWarned = false;
        reconnectFailCount = 0;
        logger.info(ChatColor.GREEN + "OneBot 已连接 | client=" + (client != null) + " | 群数=" + config.groups().size());
    }

    public void onBotDisconnected(String reason) {
        if (closed) return;
        connected = false;
        String hint = disconnectHint != null ? disconnectHint.get() : null;
        if (!disconnectWarned) {
            int interval = config.onebot().reconnectIntervalSeconds();
            logger.warning(ChatColor.RED + "OneBot 连接断开 — " + reason
                + "。请检查实现端、正向 WS 地址与 access-token。将在 " + interval + " 秒后自动重连。" + (hint != null ? hint : ""));
            disconnectWarned = true;
            reconnectFailCount = 1;
            return;
        }
        // 首次告警之后，每隔 RECONNECT_FAIL_LOG_INTERVAL 次才打印一次摘要，
        // 避免每 reconnect-interval-seconds 秒刷一条日志。
        reconnectFailCount++;
        if (reconnectFailCount % RECONNECT_FAIL_LOG_INTERVAL == 0) {
            int interval = config.onebot().reconnectIntervalSeconds();
            long elapsed = (long) reconnectFailCount * interval;
            logger.warning(ChatColor.RED + "OneBot 仍未连接 — 已连续重连失败 "
                + reconnectFailCount + " 次（约 " + elapsed + " 秒），原因: " + reason
                + "。实现端就绪后会自动重连，也可执行 /axs reload qqbot 立即重试。"
                + (hint != null ? hint : ""));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public QQBotBindService bindService() {
        return bindService;
    }

    @FunctionalInterface
    public interface GroupMessageListener {
        void onMessage(String nick, String message, long groupId);
    }

    public void setGroupMessageListener(GroupMessageListener listener) {
        this.groupMessageListener = listener;
    }

    public void setEssentialsProvider(java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.EssentialsQueryable> provider) {
        if (commandRouter != null) commandRouter.setEssentialsProvider(provider);
    }

    public void setSignInService(QQBotSignInService signInService) {
        this.signInService = signInService;
        if (commandRouter != null) commandRouter.setSignInService(signInService);
    }

    public void addGroupEventListener(xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
        if (listener != null && !closed) eventListeners.addIfAbsent(listener);
    }

    public void removeGroupEventListener(xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
        eventListeners.remove(listener);
    }

    // ─── OneBot 事件处理 ──────────────────────────────────

    public void handleOneBotEvent(OneBotEvent event) {
        if (closed || !(event.isGroupMessage() || event.isNotice())
                || event.userId() <= 0 || findGroupConfig(event.groupId()) == null) return;
        if (config.debug()) {
            logger.info("[Debug] ← OneBot事件: type=" + event.postType()
                + " group=" + event.groupId());
        }

        // 黑名单拦截（配置静态黑名单 + 数据库动态黑名单）
        if (config.blacklist().isConfigBlacklisted(event.userId()) || repository.isBlacklisted(event.userId())) {
            if (config.debug()) {
                logger.info("[Debug] 黑名单QQ " + event.userId() + " 的事件已被拦截");
            }
            return;
        }
        if (closed) return;

        // notice 事件（入群欢迎 / 禁言同步）
        if (event.isNotice()) {
            handleNoticeEvent(event);
            return;
        }

        if (!event.isGroupMessage()) return;
        if (event.isOwnMessage()) return;

        // 消息去重：OneBot 重连重放/多端上报可能重复投递同一 message_id，
        // 重复处理会导致签到/积分/红包等指令被执行两次（set 每 5 分钟清空，覆盖重放窗口）
        int messageId = event.messageId();
        if (messageId != 0) {
            String key = event.groupId() + ":" + event.userId() + ":" + messageId;
            long now = System.nanoTime() / 1_000_000L;
            Long previous = processedMessageIds.get(key);
            if (previous != null && now - previous < 300000L) return;
            if (previous == null && processedMessageIds.size() >= 10000) return;
            processedMessageIds.put(key, now);
        }

        long groupId = event.groupId();
        QQBotGroupConfig groupConfig = findGroupConfig(groupId);
        if (groupConfig == null) return;

        String rawMessage = event.rawMessage().trim();
        if (rawMessage.isEmpty() || rawMessage.length() > 4000) return;

        // 自动 moderation（关键词拦截）
        if (maybeAutoModerate(event, rawMessage)) {
            return;
        }

        // 尝试作为指令处理
        if (groupConfig.commandsEnabled() && commandRouter.handleCommand(event)) {
            return;
        }

        // 关键词自动回复（FAQ）
        maybeAutoReply(groupId, rawMessage);

        // @游戏名 → 游戏内提示
        boolean chatLoaded = xuanmo.arcartxsuite.api.AxsCapabilities.get(
            xuanmo.arcartxsuite.api.capability.ChatMutable.class) != null;
        if (config.atToGameEnabled() && !chatLoaded) {
            handleAtToGame(event);
        }

        // 记录群活跃度
        if (signInService != null) {
            signInService.recordActivity(event.userId(), groupId);
        }

        // Public notifications are independent from the optional game-chat relay.
        String nick = event.senderCard();
        if (nick == null || nick.isEmpty()) nick = event.senderNickname();
        if (nick == null) nick = String.valueOf(event.userId());
        nick = safeChatText(nick);
        String cleanMessage = safeChatText(rawMessage);
        if (cleanMessage.isEmpty()) return;
        final String displayNick = nick;
        for (var listener : eventListeners) {
            try { listener.onGroupMessage(groupId, event.userId(), displayNick, cleanMessage); }
            catch (Exception error) { logger.warning("群消息监听器异常: " + error.getClass().getSimpleName()); }
        }
        if (groupConfig.syncQqToGame()) {
            String format = ChatColor.translateAlternateColorCodes('&', groupConfig.qqToGameFormat())
                .replace("{nick}", nick)
                .replace("{qq}", String.valueOf(event.userId()))
                .replace("{group}", String.valueOf(groupId));
            QQBotBinding binding = bindService.findByQq(event.userId());
            java.util.UUID linked = binding == null ? null : binding.playerUuid();
            java.util.Set<java.util.UUID> mentions = new java.util.HashSet<>();
            if (chatLoaded && config.atToGameEnabled()) {
                for (long target : event.atQQs()) {
                    QQBotBinding mentioned = bindService.findByQq(target);
                    if (mentioned != null) mentions.add(mentioned.playerUuid());
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) return;
                var chat = xuanmo.arcartxsuite.api.AxsCapabilities.get(
                    xuanmo.arcartxsuite.api.capability.ChatExternalRelay.class);
                if (chat != null) {
                    chat.receive(groupConfig.chatChannel(), linked, format, cleanMessage, mentions).whenComplete((accepted, failure) -> {
                        if (!closed && failure != null) logger.warning("转发群消息至 Chat 失败: " + failure.getMessage());
                    });
                    return;
                }
                if (xuanmo.arcartxsuite.api.AxsCapabilities.get(
                    xuanmo.arcartxsuite.api.capability.ChatMutable.class) != null) return;
                String formatted = format.replace("{message}", cleanMessage);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) online.sendMessage(formatted);
                }
                if (groupMessageListener != null) groupMessageListener.onMessage(displayNick, cleanMessage, groupId);
            });

        }
    }

    // ─── Bukkit 事件监听 ──────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled() || closed || !connected || !config.delivery().vanillaChatFallback()
                || xuanmo.arcartxsuite.api.AxsCapabilities.get(xuanmo.arcartxsuite.api.capability.ChatMutable.class) != null) return;
        sendPlayerMessage(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPublicChat(xuanmo.arcartxsuite.api.event.chat.PublicChatMessageEvent event) {
        if (!closed && connected) messageGateway.sendPlayer(event.player(), event.message(), null, event.channelId());
    }

    @EventHandler
    public void clearPlayerDeliveryState(PlayerQuitEvent event) {
        if (messageGateway != null) messageGateway.clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!connected) return;
        Player player = event.getPlayer();
        for (QQBotGroupConfig group : config.groups()) {
            if (group.syncGameToQq() && group.joinMessage() != null && !group.joinMessage().isEmpty()) {
                String msg = group.joinMessage()
                    .replace("{player}", player.getName())
                    .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
                client.sendGroupMessage(group.groupId(), msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!connected) return;
        Player player = event.getPlayer();
        for (QQBotGroupConfig group : config.groups()) {
            if (group.syncGameToQq() && group.quitMessage() != null && !group.quitMessage().isEmpty()) {
                String msg = group.quitMessage()
                    .replace("{player}", player.getName())
                    .replace("{online}", String.valueOf(Math.max(0, Bukkit.getOnlinePlayers().size() - 1)))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
                client.sendGroupMessage(group.groupId(), msg);
            }
        }
    }

    // ─── @游戏名 → 游戏内提示 ───────────────────────────

    private void handleAtToGame(OneBotEvent event) {
        List<Long> atQqs = event.atQQs();
        if (atQqs.isEmpty()) return;
        String nickRaw = event.senderCard();
        if (nickRaw == null || nickRaw.isEmpty()) nickRaw = event.senderNickname();
        if (nickRaw == null) nickRaw = String.valueOf(event.userId());
        final String nick = nickRaw;
        for (Long qqId : atQqs) {
            QQBotBinding binding = bindService.findByQq(qqId);
            if (binding == null) continue;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) return;
                Player player = Bukkit.getPlayerExact(binding.playerName());
                if (player == null || !player.isOnline()
                        || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
                player.sendTitle("", messages.get("player.at-title", nick), 10, 70, 20);
                player.sendMessage(messages.get("player.at-message", nick));
            });
        }
    }

    // ─── 击杀播报 ────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!connected) return;
        var bc = config.broadcast();
        if (!bc.killEnabled()) return;

        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return; // 仅播报有玩家击杀者的死亡

        String victimName = victim.getCustomName() != null
            ? ChatColor.stripColor(victim.getCustomName())
            : victim.getName();

        // 过滤逻辑：playerKillOnly 优先 → bossOnly → 全部
        if (bc.playerKillOnly()) {
            if (!(victim instanceof Player)) return;
        } else if (bc.bossOnly()) {
            if (victim instanceof Player) return;
            if (!bc.isBoss(victimName) && !bc.isBoss(victim.getName())) return;
        }

        String msg = bc.killFormat()
            .replace("{killer}", killer.getName())
            .replace("{victim}", victimName);
        for (QQBotGroupConfig group : config.groups()) {
            client.sendGroupMessage(group.groupId(), msg);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!connected) return;
        var bc = config.broadcast();
        if (!bc.deathEnabled()) return;

        Player player = event.getEntity();
        String msg = bc.deathFormat()
            .replace("{player}", player.getName());
        for (QQBotGroupConfig group : config.groups()) {
            client.sendGroupMessage(group.groupId(), msg);
        }
    }

    // ─── notice 事件处理（入群欢迎 / 禁言同步） ──────────

    private void handleNoticeEvent(OneBotEvent event) {
        long groupId = event.groupId();
        if (findGroupConfig(groupId) == null) return; // 仅处理已配置群

        // 入群欢迎
        if (event.isGroupMemberIncrease()) {
            notifyMembership(event, true);
            var wc = config.welcome();
            if (wc.enabled() && connected) {
                long newMember = event.userId();
                String msg = wc.message()
                    .replace("{qq}", String.valueOf(newMember))
                    .replace("{group}", String.valueOf(groupId));
                client.sendGroupMessageAt(groupId, newMember, msg);
            }
            return;
        }
        if (event.isGroupMemberDecrease()) { notifyMembership(event, false); return; }

        // 禁言同步：QQ 群禁言 → 游戏内封禁绑定玩家
        if (event.isGroupBan()) {
            var mc = config.moderation();
            if (mc.syncBanEnabled() && "ban".equals(event.subType())) {
                long bannedQq = event.userId();
                long durationSec = event.banDuration();
                QQBotBinding binding = bindService.findByQq(bannedQq);
                if (binding != null) {
                    String playerName = binding.playerName();
                    String durationStr = formatBanDuration(durationSec);
                    try {
                        String cmd = xuanmo.arcartxsuite.api.command.SafeCommandExecutor.builder(mc.syncBanCommand())
                            .bindPlayerName("name", playerName)
                            .bindRaw("duration", mc.syncBanUseDuration() ? durationStr : "")
                            .bindQuoted("reason", mc.syncBanReason())
                            .build();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!closed) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        });
                    } catch (IllegalArgumentException e) {
                        logger.warning("禁言同步跳过: 非法玩家名 " + playerName);
                        return;
                    }
                    logger.info("禁言同步: QQ " + bannedQq + " → 封禁玩家 " + playerName
                        + " (" + durationStr + ")");
                }
            }
        }
    }

    /** 将秒数转换为分钟字符串（如 "10m"），至少 1 分钟 */
    private static String formatBanDuration(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes + "m";
    }

    // ─── 关键词自动回复（FAQ） ────────────────────────────

    private void maybeAutoReply(long groupId, String message) {
        var ar = config.autoReply();
        if (!ar.enabled() || !connected) return;
        long now = System.currentTimeMillis();
        long last = groupLastAutoReply.getOrDefault(groupId, 0L);
        if (now - last < ar.cooldownSeconds() * 1000L) return;
        for (var rule : ar.rules()) {
            if (rule.matches(message)) {
                groupLastAutoReply.put(groupId, now);
                client.sendGroupMessage(groupId, rule.response());
                return;
            }
        }
    }

    // ─── 自动 moderation（撤回 + 禁言） ───────────────────

    private final Map<Long, Long> groupLastAutoMod = new ConcurrentHashMap<>();

    private boolean maybeAutoModerate(OneBotEvent event, String message) {
        var am = config.moderation().autoModeration();
        if (!am.enabled() || !connected) return false;
        long groupId = event.groupId();
        long now = System.currentTimeMillis();
        long last = groupLastAutoMod.getOrDefault(groupId, 0L);
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        for (String kw : am.keywords()) {
            if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase(java.util.Locale.ROOT))) {
                if (now - last < am.cooldownSeconds() * 1000L) return true;
                groupLastAutoMod.put(groupId, now);
                // 撤回消息
                int msgId = event.messageId();
                if (msgId != 0) {
                    client.deleteMessage(msgId);
                }
                // 禁言发送者
                client.setGroupBan(groupId, event.userId(), am.banDurationSeconds());
                logger.info("[AutoMod] 群 " + groupId + " 关键词拦截: QQ=" + event.userId()
                    + " 关键词=" + kw + " 禁言=" + am.banDurationSeconds() + "s");
                return true;
            }
        }
        return false;
    }

    // ─── 公共方法 ────────────────────────────────────────

    public java.util.concurrent.CompletableFuture<xuanmo.arcartxsuite.qqbot.onebot.OneBotResult> sendToGroup(long groupId, String message) {
        return sendAdminMessage(groupId, message);
    }

    public java.util.concurrent.CompletableFuture<xuanmo.arcartxsuite.qqbot.onebot.OneBotResult> sendToAllGroups(String message) {
        return sendAdminMessage(null, message);
    }

    public java.util.concurrent.CompletableFuture<xuanmo.arcartxsuite.qqbot.onebot.OneBotResult> sendAdminMessage(Long groupId, String message) {
        return messageGateway.sendSystem(groupId, message);
    }
    public java.util.concurrent.CompletableFuture<xuanmo.arcartxsuite.qqbot.onebot.OneBotResult> sendPlayerMessage(Player player, String message) {
        return messageGateway.sendPlayer(player, message, null);
    }
    public java.util.concurrent.CompletableFuture<xuanmo.arcartxsuite.qqbot.onebot.OneBotResult> sendPlayerMention(Player player, long qq, String message) {
        return messageGateway.sendPlayer(player, message, qq);
    }
    public QQBotSignInService getSignInService() { return signInService; }

    private void notifyMembership(OneBotEvent event, boolean joined) {
        for (var listener : eventListeners) {
            try {
                if (joined) listener.onMemberJoin(event.groupId(), event.userId());
                else listener.onMemberLeave(event.groupId(), event.userId());
            } catch (Exception error) { logger.warning("群成员监听器异常: " + error.getClass().getSimpleName()); }
        }
    }
    private static String safeChatText(String value) {
        String text = ChatColor.stripColor(value).replace('\n', ' ').replace('\r', ' ');
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    public QQBotConfiguration configuration() {
        return config;
    }

    // ─── 内部方法 ────────────────────────────────────────

    private QQBotGroupConfig findGroupConfig(long groupId) {
        for (QQBotGroupConfig group : config.groups()) {
            if (group.groupId() == groupId) return group;
        }
        return null;
    }
}
