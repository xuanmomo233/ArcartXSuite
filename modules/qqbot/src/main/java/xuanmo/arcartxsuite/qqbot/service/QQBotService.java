package xuanmo.arcartxsuite.qqbot.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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

    private static final Pattern CQ_CODE_PATTERN = Pattern.compile("\\[CQ:[^]]+]");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");

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
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    private BukkitTask cleanupTask;
    private volatile boolean connected;
    private volatile boolean disconnectWarned;
    private GroupMessageListener groupMessageListener;
    private final java.util.concurrent.CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener> eventListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<Long, Long> groupLastAutoReply = new ConcurrentHashMap<>();

    public QQBotService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        QQBotBindService bindService,
        QQBotRepository repository,
        Logger logger,
        PlaceholderResolverAPI placeholderResolver,
        MessageProvider messages
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.bindService = bindService;
        this.repository = repository;
        this.logger = logger;
        this.placeholderResolver = placeholderResolver;
    }

    /**
     * 注入 OneBot 客户端（解决 service ↔ client 循环引用）。
     * 必须在 {@link #start()} 之前调用。
     */
    public void setClient(OneBotClient client) {
        this.client = client;
        this.commandRouter = new QQBotCommandRouter(plugin, config, client, bindService, repository, logger, placeholderResolver);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 定时清理过期验证码
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            bindService.cleanupExpiredCodes();
            // 清理已处理消息 ID 缓存
            processedMessageIds.clear();
        }, 6000L, 6000L); // 每5分钟
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        processedMessageIds.clear();
    }

    public void onBotConnected() {
        connected = true;
        disconnectWarned = false;
        logger.info(ChatColor.GREEN + "[QQBot] OneBot 已连接 | client=" + (client != null) + " | 群数=" + config.groups().size());
    }

    public void onBotDisconnected() {
        connected = false;
        if (!disconnectWarned) {
            logger.warning(ChatColor.RED + "[QQBot] OneBot 连接断开");
            disconnectWarned = true;
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

    public void setMailProvider(java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> provider) {
        if (commandRouter != null) commandRouter.setMailProvider(provider);
    }

    public void setSignInService(QQBotSignInService signInService) {
        this.signInService = signInService;
        if (commandRouter != null) commandRouter.setSignInService(signInService);
    }

    public void addGroupEventListener(xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
        if (listener != null) eventListeners.add(listener);
    }

    public void removeGroupEventListener(xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
        eventListeners.remove(listener);
    }

    // ─── OneBot 事件处理 ──────────────────────────────────

    public void handleOneBotEvent(OneBotEvent event) {
        if (config.debug()) {
            logger.info("[QQBot/Debug] ← OneBot事件: type=" + event.postType()
                + (event.isGroupMessage() ? " group=" + event.groupId() + " msg=" + event.rawMessage() : ""));
        }

        // 黑名单拦截（配置静态黑名单 + 数据库动态黑名单）
        if (config.blacklist().isConfigBlacklisted(event.userId()) || repository.isBlacklisted(event.userId())) {
            if (config.debug()) {
                logger.info("[QQBot/Debug] 黑名单QQ " + event.userId() + " 的事件已被拦截");
            }
            return;
        }

        // notice 事件（入群欢迎 / 禁言同步）
        if (event.isNotice()) {
            handleNoticeEvent(event);
            return;
        }

        if (!event.isGroupMessage()) return;

        long groupId = event.groupId();
        QQBotGroupConfig groupConfig = findGroupConfig(groupId);
        if (groupConfig == null) return;

        String rawMessage = event.rawMessage().trim();
        if (rawMessage.isEmpty()) return;

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
        if (config.atToGameEnabled()) {
            handleAtToGame(event);
        }

        // 记录群活跃度
        if (signInService != null) {
            signInService.recordActivity(event.userId(), groupId);
        }

        // 消息同步：QQ → 游戏
        if (groupConfig.syncQqToGame()) {
            String nick = event.senderCard();
            if (nick == null || nick.isEmpty()) {
                nick = event.senderNickname();
            }
            if (nick == null) nick = String.valueOf(event.userId());

            // 过滤 CQ 码（图片/表情等），仅保留纯文本
            String cleanMessage = CQ_CODE_PATTERN.matcher(rawMessage).replaceAll("").trim();
            if (cleanMessage.isEmpty()) return;

            String formatted = groupConfig.qqToGameFormat()
                .replace("{nick}", nick)
                .replace("{qq}", String.valueOf(event.userId()))
                .replace("{message}", cleanMessage)
                .replace("{group}", String.valueOf(groupId));

            String colored = ChatColor.translateAlternateColorCodes('&', formatted);
            // 在主线程广播
            Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage(colored));

            // 通知 UI 服务
            if (groupMessageListener != null) {
                String finalNick = nick;
                groupMessageListener.onMessage(finalNick, cleanMessage, groupId);
            }

            // 派发给 QQBotNotifiable 监听器
            String dispatchNick = nick;
            String dispatchClean = cleanMessage;
            for (var listener : eventListeners) {
                try {
                    listener.onGroupMessage(groupId, event.userId(), dispatchNick, dispatchClean);
                } catch (Exception ex) {
                    logger.warning("[QQBot] 群事件监听器异常: " + ex.getMessage());
                }
            }
        }
    }

    // ─── Bukkit 事件监听 ──────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (config.debug()) {
            logger.info("[QQBot/Debug] AsyncPlayerChatEvent: player=" + event.getPlayer().getName()
                + " connected=" + connected + " client=" + (client != null)
                + " cancelled=" + event.isCancelled());
        }
        if (!connected) return;
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isEmpty()) return;

        // 去掉颜色代码
        String cleanMessage = COLOR_CODE_PATTERN.matcher(message).replaceAll("");
        cleanMessage = ChatColor.stripColor(cleanMessage);

        for (QQBotGroupConfig group : config.groups()) {
            if (group.syncGameToQq()) {
                String formatted = group.gameToQqFormat()
                    .replace("{player}", player.getName())
                    .replace("{display_name}", player.getDisplayName() != null ? player.getDisplayName() : player.getName())
                    .replace("{message}", cleanMessage)
                    .replace("{server}", config.serverId());
                if (config.debug()) {
                    logger.info("[QQBot/Debug] → QQ群 " + group.groupId() + ": " + formatted);
                }
                client.sendGroupMessage(group.groupId(), formatted);
            }
        }
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
            Player player = Bukkit.getPlayerExact(binding.playerName());
            if (player == null || !player.isOnline()) continue;
            Bukkit.getScheduler().runTask(plugin, () -> {
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
                    String cmd = mc.syncBanCommand()
                        .replace("{name}", playerName)
                        .replace("{duration}", mc.syncBanUseDuration() ? durationStr : "")
                        .replace("{reason}", mc.syncBanReason())
                        .trim()
                        .replaceAll("\\s+", " ");
                    Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
                    logger.info("[QQBot] 禁言同步: QQ " + bannedQq + " → 封禁玩家 " + playerName
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
        if (now - last < am.cooldownSeconds() * 1000L) return false;

        String lower = message.toLowerCase();
        for (String kw : am.keywords()) {
            if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) {
                groupLastAutoMod.put(groupId, now);
                // 撤回消息
                int msgId = event.messageId();
                if (msgId > 0) {
                    client.deleteMessage(msgId);
                }
                // 禁言发送者
                client.setGroupBan(groupId, event.userId(), am.banDurationSeconds());
                logger.info("[QQBot/AutoMod] 群 " + groupId + " 关键词拦截: QQ=" + event.userId()
                    + " 关键词=" + kw + " 禁言=" + am.banDurationSeconds() + "s");
                return true;
            }
        }
        return false;
    }

    // ─── 公共方法 ────────────────────────────────────────

    public void sendToGroup(long groupId, String message) {
        if (connected) {
            client.sendGroupMessage(groupId, message);
        }
    }

    public void sendToAllGroups(String message) {
        if (!connected) return;
        for (QQBotGroupConfig group : config.groups()) {
            client.sendGroupMessage(group.groupId(), message);
        }
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
