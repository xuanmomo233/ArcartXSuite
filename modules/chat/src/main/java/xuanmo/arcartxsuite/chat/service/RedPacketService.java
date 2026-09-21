package xuanmo.arcartxsuite.chat.service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.config.RedPacketConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaim;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome;
import xuanmo.arcartxsuite.chat.model.RedPacketEntryRow;
import xuanmo.arcartxsuite.chat.model.RedPacketPoolEntry;
import xuanmo.arcartxsuite.chat.model.RedPacketPreset;
import xuanmo.arcartxsuite.chat.model.RedPacketReward;
import xuanmo.arcartxsuite.chat.model.RedPacketRewardKind;
import xuanmo.arcartxsuite.chat.model.RedPacketType;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;

/**
 * 红包服务，负责红包的创建、领取、过期退款、跨服广播与重启重播。
 * <p>
 * 玩家货币红包（普通/拼手气）在发送时扣款并预拆分金额；管理员红包免扣款；
 * 预设红包从 {@code chat/redpackets/} 加载奖励池，领取时按权重随机抽取。
 * 领取通过 {@link ChatRepository#claimRedPacket} 单事务完成，共享 MySQL 下跨服安全。
 */
public final class RedPacketService {

    /** 跨服红包消息前缀，用于与聊天信封区分。 */
    static final String CROSS_PREFIX = "axs_rp|";
    private static final long EXPIRY_CHECK_TICKS = 1200L; // 60s
    private static final long REBROADCAST_DELAY_TICKS = 60L; // 进服后 3s 重播
    private static final String MODULE_ID = "chat";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ChatModuleConfiguration configuration;
    private final ChatRepository repository;
    private final ChatService chatService;
    private final PacketBridgeAPI packetBridge;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final PendingRewardService pendingRewardService;
    private final ItemSourceRegistry itemSourceRegistry;
    private final VanillaItemNameBridge vanillaItemNameBridge;
    private final MessageProvider messages;
    private final ExecutorService ioExecutor;
    private final boolean crossServerStorage;
    private final File dataFolder;
    private final ItemBridgeAPI itemStackBridge;
    private final Map<UUID, Long> sendCooldowns = new ConcurrentHashMap<>();

    private volatile Map<String, RedPacketPreset> presets = Map.of();
    private BukkitTask expiryTask;

    public RedPacketService(
        JavaPlugin plugin,
        Logger logger,
        ChatModuleConfiguration configuration,
        ChatRepository repository,
        ChatService chatService,
        PacketBridgeAPI packetBridge,
        CurrencyBridgeAPI currencyManager,
        ItemRewardDispatcher itemRewardDispatcher,
        PendingRewardService pendingRewardService,
        ItemSourceRegistry itemSourceRegistry,
        VanillaItemNameBridge vanillaItemNameBridge,
        MessageProvider messages,
        File dataFolder,
        ItemBridgeAPI itemStackBridge,
        boolean crossServerStorage
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.repository = repository;
        this.chatService = chatService;
        this.packetBridge = packetBridge;
        this.currencyManager = currencyManager;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.pendingRewardService = pendingRewardService;
        this.itemSourceRegistry = itemSourceRegistry;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.messages = messages;
        this.dataFolder = dataFolder;
        this.itemStackBridge = itemStackBridge;
        this.crossServerStorage = crossServerStorage;
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AXS-Chat-RedPacket-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    private RedPacketConfiguration config() {
        return configuration.redPacket();
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    // ── 生命周期 ──────────────────────────────────────────

    public void start() {
        loadPresets();
        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweepExpired, EXPIRY_CHECK_TICKS, EXPIRY_CHECK_TICKS);
        // 重启/重载后重播未过期红包（延迟等玩家进服）
        Bukkit.getScheduler().runTaskLater(plugin, this::rebroadcastActive, 100L);
        if (config().enabled()) {
            logger.fine("红包功能已启用，预设数=" + presets.size() + "，跨服存储=" + (crossServerStorage ? "MySQL" : "本地"));
        }
    }

    public void shutdown() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        sendCooldowns.clear();
        presets = Map.of();
    }

    /** 重新加载红包预设。 */
    public int reloadPresets() {
        loadPresets();
        return presets.size();
    }

    private void loadPresets() {
        File directory = new File(dataFolder, config().presetsDirectory());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try {
            presets = RedPacketPresetLoader.loadPresets(
                directory, logger, itemSourceRegistry,
                new IconResolver(itemStackBridge, itemSourceRegistry),
                vanillaItemNameBridge
            );
        } catch (Exception exception) {
            logger.warning("加载红包预设失败: " + exception.getMessage());
            presets = Map.of();
        }
    }

    /** 预设 id 列表（Tab 补全用）。 */
    public List<String> presetIds() {
        return new ArrayList<>(presets.keySet());
    }

    // ── 发红包 ────────────────────────────────────────────

    /**
     * 玩家发送货币红包。
     *
     * @param sender  发送者
     * @param type    红包类型（normal / lucky）
     * @param total   总金额
     * @param count   份数
     * @param wish    祝福语（可为空串）
     * @return 操作结果
     */
    public ChatOperationResult sendPlayerPacket(Player sender, RedPacketType type, BigDecimal total, int count, String wish) {
        if (!config().enabled()) {
            return ChatOperationResult.failure(message("redpacket.disabled"));
        }
        if (!config().sendPermission().isBlank() && !sender.hasPermission(config().sendPermission())) {
            return ChatOperationResult.failure(message("redpacket.no-send-permission"));
        }
        if (type != RedPacketType.NORMAL && type != RedPacketType.LUCKY) {
            return ChatOperationResult.failure(message("redpacket.invalid-type"));
        }
        long cooldown = config().sendCooldownSeconds();
        if (cooldown > 0) {
            long last = sendCooldowns.getOrDefault(sender.getUniqueId(), 0L);
            long remain = cooldown * 1000L - (System.currentTimeMillis() - last);
            if (remain > 0) {
                return ChatOperationResult.failure(message("redpacket.cooldown", (long) Math.ceil(remain / 1000.0)));
            }
        }
        ChatChannelDefinition channel = chatService.currentChannel(sender);
        if (channel == null) {
            return ChatOperationResult.failure(message("result.no-channel"));
        }
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(config().currency());
        if (bridge == null || !bridge.available()) {
            return ChatOperationResult.failure(message("redpacket.currency-unavailable", config().currency()));
        }
        ChatOperationResult validation = validateAmounts(total, count);
        if (validation != null) {
            return validation;
        }
        int scale = currencyScale(bridge);
        List<BigDecimal> amounts = splitAmounts(type, total, count, scale);
        if (bridge.balance(sender).compareTo(total) < 0) {
            return ChatOperationResult.failure(message("redpacket.insufficient",
                currencyManager.format(config().currency(), bridge.balance(sender))));
        }
        var result = bridge.withdraw(sender, total);
        if (!result.success()) {
            return ChatOperationResult.failure(message("redpacket.withdraw-failed", result.message()));
        }

        RedPacket packet = buildPacket(
            newId(), type, sender.getUniqueId().toString(), sender.getName(), sender.getDisplayName(),
            channel.id(), config().currency(), total, amounts, count, wish, "", false,
            Duration.ofMinutes(config().expireMinutes())
        );
        sendCooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
        persistThenBroadcast(packet, List.of(), sender, () -> bridge.deposit(sender, total));
        return ChatOperationResult.success(message("redpacket.sent",
            currencyManager.format(config().currency(), total), count));
    }

    /**
     * 管理员/控制台发送免扣款货币红包。
     */
    public ChatOperationResult sendAdminPacket(CommandSender sender, RedPacketType type, BigDecimal total, int count, String wish) {
        if (!config().enabled()) {
            return ChatOperationResult.failure(message("redpacket.disabled"));
        }
        if (!config().adminPermission().isBlank() && !sender.hasPermission(config().adminPermission())) {
            return ChatOperationResult.failure(message("redpacket.no-admin-permission"));
        }
        if (type != RedPacketType.NORMAL && type != RedPacketType.LUCKY) {
            return ChatOperationResult.failure(message("redpacket.invalid-type"));
        }
        ChatOperationResult validation = validateAmounts(total, count);
        if (validation != null) {
            return validation;
        }
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(config().currency());
        if (bridge == null || !bridge.available()) {
            return ChatOperationResult.failure(message("redpacket.currency-unavailable", config().currency()));
        }
        ChatChannelDefinition channel = adminChannel(sender);
        if (channel == null) {
            return ChatOperationResult.failure(message("result.no-channel"));
        }
        Player senderPlayer = sender instanceof Player p ? p : null;
        int scale = currencyScale(bridge);
        List<BigDecimal> amounts = splitAmounts(type, total, count, scale);
        RedPacket packet = buildPacket(
            newId(), type,
            senderPlayer == null ? null : senderPlayer.getUniqueId().toString(),
            sender.getName(), senderPlayer == null ? sender.getName() : senderPlayer.getDisplayName(),
            channel.id(), config().currency(), total, amounts, count, wish, "", true,
            Duration.ofMinutes(config().expireMinutes())
        );
        persistThenBroadcast(packet, List.of(), senderPlayer, null);
        return ChatOperationResult.success(message("redpacket.sent",
            currencyManager.format(config().currency(), total), count));
    }

    /**
     * 管理员/控制台发送预设奖励池红包。
     *
     * @param sender        发送者（可为控制台）
     * @param presetId      预设 ID
     * @param countOverride 份数覆盖，{@code null} 用预设值
     */
    public ChatOperationResult sendPresetPacket(CommandSender sender, String presetId, @Nullable Integer countOverride) {
        if (!config().enabled()) {
            return ChatOperationResult.failure(message("redpacket.disabled"));
        }
        if (!config().adminPermission().isBlank() && !sender.hasPermission(config().adminPermission())) {
            return ChatOperationResult.failure(message("redpacket.no-admin-permission"));
        }
        RedPacketPreset preset = presets.get(presetId == null ? "" : presetId.toLowerCase(java.util.Locale.ROOT));
        if (preset == null || !preset.enabled()) {
            return ChatOperationResult.failure(message("redpacket.preset-not-found", presetId));
        }
        ChatChannelDefinition channel = adminChannel(sender);
        if (channel == null) {
            return ChatOperationResult.failure(message("result.no-channel"));
        }
        int count = countOverride != null ? Math.max(1, countOverride) : preset.count();
        if (!preset.replacement()) {
            int totalSlots = 0;
            for (RedPacketPoolEntry entry : preset.rewards()) {
                totalSlots += Math.max(1, entry.quantity());
            }
            count = Math.min(count, totalSlots);
        }
        count = Math.min(count, config().maxCount());

        List<RedPacketEntryRow> rows = new ArrayList<>();
        for (int i = 0; i < preset.rewards().size(); i++) {
            RedPacketPoolEntry entry = preset.rewards().get(i);
            int remaining = preset.replacement()
                ? (entry.quantity() <= 0 ? -1 : entry.quantity())
                : Math.max(1, entry.quantity());
            rows.add(RedPacketEntryRow.of(i, entry.reward(), entry.weight(), remaining));
        }
        long expireMinutes = preset.expireMinutes() > 0 ? preset.expireMinutes() : config().expireMinutes();
        Player senderPlayer = sender instanceof Player p ? p : null;
        RedPacket packet = buildPacket(
            newId(), RedPacketType.POOL,
            senderPlayer == null ? null : senderPlayer.getUniqueId().toString(),
            preset.displayName().isBlank() ? sender.getName() : preset.displayName(),
            preset.displayName().isBlank() ? sender.getName() : preset.displayName(),
            channel.id(), "", BigDecimal.ZERO, List.of(), count,
            preset.wish(), preset.id(), true, Duration.ofMinutes(expireMinutes)
        );
        persistThenBroadcast(packet, rows, senderPlayer, null);
        return ChatOperationResult.success(message("redpacket.sent-preset", preset.displayName(), count));
    }

    // ── 抢红包 ────────────────────────────────────────────

    /**
     * 玩家领取红包。DB 事务异步执行，结果回主线程发放奖励并播报。
     */
    public void claimPacket(Player player, String packetId) {
        if (!config().enabled()) {
            player.sendMessage(prefix() + ChatColor.RED + message("redpacket.disabled"));
            return;
        }
        if (!config().claimPermission().isBlank() && !player.hasPermission(config().claimPermission())) {
            player.sendMessage(prefix() + ChatColor.RED + message("redpacket.no-claim-permission"));
            return;
        }
        String normalized = packetId == null ? "" : packetId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            player.sendMessage(prefix() + ChatColor.RED + message("redpacket.not-found"));
            return;
        }
        ioExecutor.execute(() -> {
            RedPacketClaimOutcome outcome;
            try {
                outcome = repository.claimRedPacket(normalized, player.getUniqueId(), player.getName(),
                    config().senderCanClaim(), Instant.now());
            } catch (SQLException exception) {
                logger.warning("红包领取失败: " + exception.getMessage());
                notifyClaimFailure(player, RedPacketClaimOutcome.Status.NOT_FOUND, null);
                return;
            }
            RedPacketClaimOutcome finalOutcome = outcome;
            Bukkit.getScheduler().runTask(plugin, () -> handleClaimResult(player, finalOutcome));
        });
    }

    private void handleClaimResult(Player player, RedPacketClaimOutcome outcome) {
        RedPacket packet = outcome.packet();
        switch (outcome.status()) {
            case SUCCESS -> {
                RedPacketReward reward = outcome.reward();
                grantReward(player, reward);
                player.sendMessage(prefix() + ChatColor.GREEN
                    + message("redpacket.claimed", describeReward(reward)));
                if (config().announceClaims() && packet != null) {
                    announce(packet, message("redpacket.broadcast-claim",
                        player.getName(), packet.senderDisplay(), describeReward(reward)));
                    publishClaimAnnounce(packet, player, reward);
                }
                if (packet != null && RedPacket.STATUS_EXHAUSTED.equals(packet.status())) {
                    announceFinish(packet);
                }
            }
            case NOT_FOUND -> player.sendMessage(prefix() + ChatColor.RED + message("redpacket.not-found"));
            case EXPIRED -> player.sendMessage(prefix() + ChatColor.RED + message("redpacket.expired"));
            case EXHAUSTED -> player.sendMessage(prefix() + ChatColor.RED + message("redpacket.exhausted"));
            case ALREADY_CLAIMED -> player.sendMessage(prefix() + ChatColor.RED + message("redpacket.already-claimed"));
            case SENDER_DENIED -> player.sendMessage(prefix() + ChatColor.RED + message("redpacket.sender-denied"));
        }
    }

    private void notifyClaimFailure(Player player, RedPacketClaimOutcome.Status status, @Nullable RedPacket packet) {
        Bukkit.getScheduler().runTask(plugin, () ->
            handleClaimResult(player, RedPacketClaimOutcome.of(status, packet)));
    }

    /** 发放奖励：货币入账 / 物品发放（含兜底）/ 控制台指令。发放失败统一走 PendingRewardService。 */
    private void grantReward(Player player, RedPacketReward reward) {
        if (reward == null || player == null) {
            return;
        }
        switch (reward.kind()) {
            case CURRENCY -> {
                CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(reward.currencyId());
                BigDecimal amount = reward.amount() == null ? BigDecimal.ZERO : reward.amount();
                boolean ok = false;
                if (bridge != null && bridge.available() && player.isOnline()) {
                    try {
                        ok = bridge.deposit(player, amount).success();
                    } catch (Exception exception) {
                        logger.warning("红包货币入账失败: " + exception.getMessage());
                    }
                }
                if (!ok && pendingRewardService != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.currency(
                        player.getUniqueId(), MODULE_ID, reward.currencyId(), amount.doubleValue(),
                        "红包 " + reward.description()));
                }
            }
            case ITEM -> {
                ItemStack itemStack = null;
                try {
                    byte[] data = java.util.Base64.getDecoder().decode(reward.itemData());
                    itemStack = ItemSerializer.deserialize(data);
                } catch (Exception exception) {
                    logger.warning("红包物品反序列化失败: " + exception.getMessage());
                }
                if (itemStack == null) {
                    return;
                }
                if (player.isOnline() && itemRewardDispatcher != null) {
                    itemRewardDispatcher.dispatchItem(player, itemStack,
                        ItemRewardDispatcher.DispatchOptions.builder(MODULE_ID)
                            .sourceDetail("redpacket")
                            .build());
                } else if (pendingRewardService != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.itemSerialized(
                        player.getUniqueId(), MODULE_ID, reward.itemData(), "红包 " + reward.description()));
                }
            }
            case COMMAND -> {
                if (player.isOnline()) {
                    String command = reward.command().replace("{player}",
                        xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()));
                    String normalized = command.startsWith("/") ? command.substring(1) : command;
                    xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor.dispatch(normalized);
                } else if (pendingRewardService != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.command(
                        player.getUniqueId(), MODULE_ID, List.of(reward.command()),
                        "红包 " + reward.description()));
                }
            }
        }
    }

    // ── 广播与播报 ─────────────────────────────────────────

    /** 入库成功后主线程广播卡片+文字，并按需发布跨服。 */
    private void persistThenBroadcast(RedPacket packet, List<RedPacketEntryRow> entries,
                                      @Nullable Player localSender, @Nullable Runnable refundOnFailure) {
        ioExecutor.execute(() -> {
            try {
                repository.insertRedPacket(packet, entries);
            } catch (SQLException exception) {
                logger.warning("红包入库失败: " + exception.getMessage());
                if (refundOnFailure != null && localSender != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            refundOnFailure.run();
                        } catch (Exception refundError) {
                            logger.warning("红包退款失败: " + refundError.getMessage());
                        }
                        localSender.sendMessage(prefix() + ChatColor.RED + message("redpacket.send-failed"));
                    });
                }
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                broadcastLocal(packet, localSender);
                publishCreate(packet);
            });
        });
    }

    /** 本服广播：卡片 + 可点击文字，按频道接收规则过滤。 */
    void broadcastLocal(RedPacket packet, @Nullable Player localSender) {
        ChatChannelDefinition channel = chatService.channel(packet.channelId());
        if (channel == null) {
            return;
        }
        UUID senderUuid = packet.senderUuid() == null ? null : UUID.fromString(packet.senderUuid());
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!chatService.canReceivePublic(channel, localSender, senderUuid, recipient)) {
                continue;
            }
            deliverPacket(recipient, packet);
        }
    }

    /** 向单个玩家投放红包卡片与文字行。 */
    void deliverPacket(Player recipient, RedPacket packet) {
        String cardId = config().cardId();
        boolean cardSent = false;
        if (!cardId.isBlank() && packetBridge != null) {
            Map<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("packetId", packet.id());
            payload.put("senderName", packet.senderName());
            payload.put("senderDisplayName", packet.senderDisplay());
            payload.put("wish", packet.wish());
            payload.put("typeName", typeName(packet.type()));
            payload.put("count", String.valueOf(packet.totalCount()));
            payload.put("expire", String.valueOf(Duration.between(Instant.now(), packet.expiresAt()).toMinutes()));
            cardSent = packetBridge.sendChatCard(recipient, cardId, payload);
        }
        if (config().textAlongside() || !cardSent) {
            sendClickableText(recipient, packet);
        }
    }

    /** 可点击文字行：[点击领取] → /chat grab <id>。 */
    private void sendClickableText(Player recipient, RedPacket packet) {
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(
            message("redpacket.broadcast-line",
                packet.senderDisplay(), typeName(packet.type()), packet.wish())));
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(
            message("redpacket.claim-button")));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chat grab " + packet.id()));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(message("redpacket.claim-hover", packet.id())).create()));
        line.addExtra(button);
        recipient.spigot().sendMessage(line);
    }

    /** 频道内文字播报（领取/抢完）。 */
    private void announce(RedPacket packet, String text) {
        ChatChannelDefinition channel = chatService.channel(packet.channelId());
        if (channel == null) {
            return;
        }
        String rendered = prefix() + text;
        UUID senderUuid = packet.senderUuid() == null ? null : UUID.fromString(packet.senderUuid());
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (chatService.canReceivePublic(channel, null, senderUuid, recipient)) {
                recipient.sendMessage(rendered);
            }
        }
    }

    /** 抢完播报：普通/池红包只播报抢完；拼手气红包追加手气王。 */
    private void announceFinish(RedPacket packet) {
        if (!config().announceFinish()) {
            return;
        }
        String finishText = message("redpacket.broadcast-finish", packet.senderDisplay());
        if (packet.type() != RedPacketType.LUCKY) {
            announce(packet, finishText);
            return;
        }
        ioExecutor.execute(() -> {
            String bestText = "";
            try {
                List<RedPacketClaim> claims = repository.listRedPacketClaims(packet.id());
                RedPacketClaim best = null;
                for (RedPacketClaim claim : claims) {
                    if (claim.amount() == null) {
                        continue;
                    }
                    if (best == null || claim.amount().compareTo(best.amount()) > 0) {
                        best = claim;
                    }
                }
                if (best != null) {
                    bestText = message("redpacket.broadcast-luckiest",
                        best.playerName(), currencyManager.format(packet.currencyId(), best.amount()));
                }
            } catch (SQLException exception) {
                logger.warning("查询红包领取记录失败: " + exception.getMessage());
            }
            String finalText = finishText + bestText;
            Bukkit.getScheduler().runTask(plugin, () -> announce(packet, finalText));
        });
    }

    // ── 过期与重播 ─────────────────────────────────────────

    /** 定期清理过期红包：标记 expired，玩家货币红包退剩余金额。 */
    private void sweepExpired() {
        List<RedPacket> actives;
        try {
            actives = repository.loadActiveRedPackets();
        } catch (SQLException exception) {
            logger.warning("扫描红包失败: " + exception.getMessage());
            return;
        }
        Instant now = Instant.now();
        for (RedPacket packet : actives) {
            if (!packet.expired(now)) {
                continue;
            }
            boolean marked;
            try {
                marked = repository.updateRedPacketStatus(packet.id(), RedPacket.STATUS_EXPIRED);
            } catch (SQLException exception) {
                logger.warning("红包过期标记失败: " + exception.getMessage());
                continue;
            }
            // 只有成功翻转 active→expired 的节点才退款，避免共享存储下重复退款
            if (marked) {
                refundIfNeeded(packet);
            }
        }
    }

    /** 玩家货币红包过期退款：在线直接入账，离线走 PendingRewardService。 */
    private void refundIfNeeded(RedPacket packet) {
        if (!config().refundOnExpire() || packet.admin() || packet.type() == RedPacketType.POOL) {
            return;
        }
        BigDecimal remaining = packet.remainingAmount();
        if (remaining == null || remaining.signum() <= 0 || packet.senderUuid() == null) {
            return;
        }
        UUID senderUuid;
        try {
            senderUuid = UUID.fromString(packet.senderUuid());
        } catch (IllegalArgumentException exception) {
            return;
        }
        String currencyId = packet.currencyId();
        Player online = Bukkit.getPlayer(senderUuid);
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currencyId);
        if (online != null && online.isOnline() && bridge != null && bridge.available()) {
            UUID uuid = senderUuid;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    bridge.deposit(online, remaining);
                    online.sendMessage(prefix() + ChatColor.YELLOW
                        + message("redpacket.refunded", currencyManager.format(currencyId, remaining)));
                } catch (Exception exception) {
                    logger.warning("红包退款入账失败: " + exception.getMessage());
                    savePendingRefund(uuid, currencyId, remaining);
                }
            });
        } else {
            savePendingRefund(senderUuid, currencyId, remaining);
        }
    }

    private void savePendingRefund(UUID playerUuid, String currencyId, BigDecimal amount) {
        if (pendingRewardService == null) {
            logger.warning("红包退款无法暂存（PendingRewardService 不可用）: " + playerUuid + " " + amount);
            return;
        }
        pendingRewardService.savePending(PendingRewardService.PendingReward.currency(
            playerUuid, MODULE_ID, currencyId, amount.doubleValue(), "红包过期退款"));
    }

    /** 重启/重载后向全部在线玩家重播未过期红包。 */
    private void rebroadcastActive() {
        ioExecutor.execute(() -> {
            List<RedPacket> actives;
            try {
                actives = repository.loadActiveRedPackets();
            } catch (SQLException exception) {
                logger.warning("加载未过期红包失败: " + exception.getMessage());
                return;
            }
            Instant now = Instant.now();
            List<RedPacket> alive = new ArrayList<>();
            for (RedPacket packet : actives) {
                if (packet.claimable(now)) {
                    alive.add(packet);
                }
            }
            if (alive.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (RedPacket packet : alive) {
                    broadcastLocal(packet, null);
                }
            });
        });
    }

    /** 玩家进服后重播其可见的未过期红包。 */
    public void onPlayerJoin(Player player) {
        if (!config().enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ioExecutor.execute(() -> {
                List<RedPacket> actives;
                try {
                    actives = repository.loadActiveRedPackets();
                } catch (SQLException exception) {
                    return;
                }
                Instant now = Instant.now();
                List<RedPacket> alive = new ArrayList<>();
                for (RedPacket packet : actives) {
                    if (!packet.claimable(now)) {
                        continue;
                    }
                    if (!config().senderCanClaim() && packet.senderUuid() != null
                            && packet.senderUuid().equals(player.getUniqueId().toString())) {
                        continue;
                    }
                    alive.add(packet);
                }
                if (alive.isEmpty()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (RedPacket packet : alive) {
                        ChatChannelDefinition channel = chatService.channel(packet.channelId());
                        if (channel == null) {
                            continue;
                        }
                        UUID senderUuid = packet.senderUuid() == null ? null : UUID.fromString(packet.senderUuid());
                        if (chatService.canReceivePublic(channel, null, senderUuid, player)) {
                            deliverPacket(player, packet);
                        }
                    }
                });
            });
        }, REBROADCAST_DELAY_TICKS);
    }

    // ── 跨服 ─────────────────────────────────────────────

    /** 红包跨服广播仅在共享 MySQL 时启用（SQLite 各服独立，无法同步领取状态）。 */
    public boolean crossServerEnabled() {
        return crossServerStorage && chatService.crossServerActive();
    }

    private void publishCreate(RedPacket packet) {
        if (!crossServerEnabled()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("action", "create");
        yaml.set("packet-id", packet.id());
        yaml.set("type", packet.type().name());
        yaml.set("sender-name", packet.senderName());
        yaml.set("sender-display", packet.senderDisplay());
        yaml.set("channel-id", packet.channelId());
        yaml.set("wish", packet.wish());
        yaml.set("count", packet.totalCount());
        yaml.set("expire-minutes", Duration.between(Instant.now(), packet.expiresAt()).toMinutes());
        chatService.publishCrossServer(CROSS_PREFIX + yaml.saveToString());
    }

    private void publishClaimAnnounce(RedPacket packet, Player claimer, RedPacketReward reward) {
        if (!crossServerEnabled()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("action", "claim");
        yaml.set("packet-id", packet.id());
        yaml.set("channel-id", packet.channelId());
        yaml.set("player-name", claimer.getName());
        yaml.set("sender-display", packet.senderDisplay());
        yaml.set("reward-desc", describeReward(reward));
        chatService.publishCrossServer(CROSS_PREFIX + yaml.saveToString());
    }

    /** 处理远端节点发来的红包消息。返回 true 表示已消费。 */
    public boolean handleRemotePayload(String payload) {
        if (payload == null || !payload.startsWith(CROSS_PREFIX)) {
            return false;
        }
        // 本地存储非共享 MySQL 时无法参与跨服领取，直接忽略远端红包消息
        if (!crossServerEnabled()) {
            return true;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(payload.substring(CROSS_PREFIX.length()));
        } catch (Exception exception) {
            logger.warning("解析跨服红包消息失败: " + exception.getMessage());
            return true;
        }
        String action = yaml.getString("action", "");
        String channelId = yaml.getString("channel-id", "");
        ChatChannelDefinition channel = chatService.channel(channelId);
        if (channel == null) {
            return true;
        }
        switch (action) {
            case "create" -> {
                // 远端红包：DB 共享，直接按 payload 渲染卡片（写库由发起节点负责）
                RedPacketType remoteType = RedPacketType.parse(yaml.getString("type", "normal"));
                RedPacket packet = new RedPacket(
                    yaml.getString("packet-id", ""),
                    remoteType == null ? RedPacketType.NORMAL : remoteType,
                    null,
                    yaml.getString("sender-name", ""),
                    yaml.getString("sender-display", ""),
                    channelId,
                    "", BigDecimal.ZERO, BigDecimal.ZERO, List.of(),
                    Math.max(1, yaml.getInt("count", 1)), 0,
                    yaml.getString("wish", ""), "", true, RedPacket.STATUS_ACTIVE,
                    Instant.now(), Instant.now().plus(Duration.ofMinutes(Math.max(1, yaml.getLong("expire-minutes", 5)))),
                    ""
                );
                Bukkit.getScheduler().runTask(plugin, () -> broadcastLocal(packet, null));
            }
            case "claim" -> {
                String rendered = prefix() + message("redpacket.broadcast-claim-remote",
                    yaml.getString("player-name", ""), yaml.getString("sender-display", ""),
                    yaml.getString("reward-desc", ""));
                for (Player recipient : Bukkit.getOnlinePlayers()) {
                    if (chatService.canReceivePublic(channel, null, null, recipient)) {
                        recipient.sendMessage(rendered);
                    }
                }
            }
            default -> {
            }
        }
        return true;
    }

    // ── 工具 ─────────────────────────────────────────────

    private ChatOperationResult validateAmounts(BigDecimal total, int count) {
        if (total == null || total.signum() <= 0) {
            return ChatOperationResult.failure(message("redpacket.invalid-amount"));
        }
        if (count < 1 || count > config().maxCount()) {
            return ChatOperationResult.failure(message("redpacket.invalid-count", config().maxCount()));
        }
        double totalValue = total.doubleValue();
        if (totalValue < config().minTotal() || totalValue > config().maxTotal()) {
            return ChatOperationResult.failure(message("redpacket.amount-range",
                config().minTotal(), config().maxTotal()));
        }
        BigDecimal min = BigDecimal.valueOf(config().minPerClaim());
        if (min.multiply(BigDecimal.valueOf(count)).compareTo(total) > 0) {
            return ChatOperationResult.failure(message("redpacket.amount-too-small",
                config().minPerClaim()));
        }
        return null;
    }

    private int currencyScale(CurrencyBridgeAPI.CurrencyBridge bridge) {
        try {
            var definition = bridge.definition();
            return definition == null ? 2 : Math.max(0, definition.scale());
        } catch (Exception exception) {
            return 2;
        }
    }

    /**
     * 拆分红包金额。
     * <p>
     * 普通红包：均分，最后一份吸收余数；拼手气红包：二倍均值法随机拆分，保证总和等于总额且每份 ≥ minPerClaim。
     */
    private List<BigDecimal> splitAmounts(RedPacketType type, BigDecimal total, int count, int scale) {
        BigDecimal min = BigDecimal.valueOf(config().minPerClaim()).setScale(scale, RoundingMode.DOWN);
        if (min.signum() <= 0) {
            min = BigDecimal.ONE.movePointLeft(scale);
        }
        List<BigDecimal> amounts = new ArrayList<>(count);
        if (type == RedPacketType.NORMAL) {
            BigDecimal per = total.divide(BigDecimal.valueOf(count), scale, RoundingMode.DOWN);
            BigDecimal assigned = BigDecimal.ZERO;
            for (int i = 0; i < count; i++) {
                if (i == count - 1) {
                    amounts.add(total.subtract(assigned));
                } else {
                    amounts.add(per);
                    assigned = assigned.add(per);
                }
            }
            return amounts;
        }
        // 拼手气：二倍均值法
        BigDecimal remaining = total;
        for (int i = 0; i < count; i++) {
            int left = count - i;
            if (left == 1) {
                amounts.add(remaining);
                break;
            }
            BigDecimal maxAllowed = remaining.subtract(min.multiply(BigDecimal.valueOf(left - 1)));
            BigDecimal cap = remaining.divide(BigDecimal.valueOf(left), scale, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(2));
            if (cap.compareTo(maxAllowed) > 0) {
                cap = maxAllowed;
            }
            if (cap.compareTo(min) < 0) {
                cap = min;
            }
            BigDecimal amount = min.add(cap.subtract(min)
                    .multiply(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())))
                .setScale(scale, RoundingMode.DOWN);
            if (amount.compareTo(min) < 0) {
                amount = min;
            }
            amounts.add(amount);
            remaining = remaining.subtract(amount);
        }
        return amounts;
    }

    private RedPacket buildPacket(String id, RedPacketType type, @Nullable String senderUuid,
            String senderName, String senderDisplay, String channelId, String currencyId,
            BigDecimal total, List<BigDecimal> amounts, int count, String wish, String presetId,
            boolean admin, Duration ttl) {
        Instant now = Instant.now();
        return new RedPacket(
            id, type, senderUuid, senderName, senderDisplay, channelId, currencyId,
            total, total, amounts, count, 0, wish == null ? "" : wish, presetId, admin,
            RedPacket.STATUS_ACTIVE, now, now.plus(ttl), chatService.nodeId()
        );
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private ChatChannelDefinition adminChannel(CommandSender sender) {
        if (sender instanceof Player player) {
            return chatService.currentChannel(player);
        }
        String configured = config().adminChannel();
        ChatChannelDefinition channel = chatService.channel(configured.isBlank() ? null : configured);
        return channel;
    }

    private String typeName(RedPacketType type) {
        return switch (type) {
            case NORMAL -> message("redpacket.type-normal");
            case LUCKY -> message("redpacket.type-lucky");
            case POOL -> message("redpacket.type-pool");
        };
    }

    private String describeReward(RedPacketReward reward) {
        if (reward == null) {
            return "";
        }
        if (reward.kind() == RedPacketRewardKind.CURRENCY && reward.amount() != null) {
            return currencyManager.format(reward.currencyId(), reward.amount());
        }
        return reward.description();
    }

    private String prefix() {
        return messages == null ? "" : messages.get("prefix");
    }
}
