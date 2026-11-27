package xuanmo.arcartxsuite.chat.service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.config.RedPacketConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaim;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery.Status;
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
    private static final Duration REVIEW_DELAY = Duration.ofMinutes(2);
    private static final int MAX_DEFERRED_CARDS = 32;
    private static final int CARD_RETRY_BATCH = 8;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ChatModuleConfiguration configuration;
    private final ChatRepository repository;
    private final ChatService chatService;
    private final PacketBridgeAPI packetBridge;
    private final CurrencyBridgeAPI currencyManager;
    private final PendingRewardService pendingRewards;
    private final ItemSourceRegistry itemSourceRegistry;
    private final VanillaItemNameBridge vanillaItemNameBridge;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor ioExecutor;
    private final boolean crossServerStorage;
    private final File dataFolder;
    private final ItemBridgeAPI itemStackBridge;
    private final Map<UUID, Long> sendCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> pendingClaims = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSends = ConcurrentHashMap.newKeySet();
    private final Semaphore sendSlots = new Semaphore(32);
    private final Semaphore deliverySlots = new Semaphore(20);
    private final Set<Runnable> unstartedEffects = new HashSet<>();
    private final Map<String, RedPacket> deferredCards = new LinkedHashMap<>();
    private BukkitTask cardRetry;
    private final java.util.concurrent.atomic.AtomicBoolean deliveryScan = new java.util.concurrent.atomic.AtomicBoolean();

    private volatile Map<String, RedPacketPreset> presets = Map.of();
    private BukkitTask expiryTask;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback expiration =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::sweepExpired);
    private BukkitTask deliveryTask;
    private BukkitTask replayTask;
    private volatile boolean closed;

    public RedPacketService(
        JavaPlugin plugin,
        Logger logger,
        ChatModuleConfiguration configuration,
        ChatRepository repository,
        ChatService chatService,
        PacketBridgeAPI packetBridge,
        CurrencyBridgeAPI currencyManager,
        PendingRewardService pendingRewards,
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
        this.pendingRewards = pendingRewards;
        this.itemSourceRegistry = itemSourceRegistry;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.messages = messages;
        this.dataFolder = dataFolder;
        this.itemStackBridge = itemStackBridge;
        this.crossServerStorage = crossServerStorage;
        this.ioExecutor = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(), runnable -> {
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
        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, expiration, EXPIRY_CHECK_TICKS, EXPIRY_CHECK_TICKS);
        // 重启/重载后重播未过期红包（延迟等玩家进服）
        deliveryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::queueDeliveries, 20L, 40L);
        if (config().enabled()) {
            replayTask = Bukkit.getScheduler().runTaskLater(plugin, this::rebroadcastActive, 100L);
        }
        if (config().enabled()) {
            logger.fine("红包功能已启用，预设数=" + presets.size() + "，跨服存储=" + (crossServerStorage ? "MySQL" : "本地"));
        }
    }

    public void shutdown() {
        synchronized (unstartedEffects) {
            if (closed) return;
            closed = true;
            for (Runnable cancel : unstartedEffects) ioExecutor.execute(cancel);
            unstartedEffects.clear();
        }
        expiration.close();
        if (deliveryTask != null) deliveryTask.cancel();
        if (replayTask != null) replayTask.cancel();
        if (cardRetry != null) cardRetry.cancel();
        deferredCards.clear();
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        ioExecutor.shutdown();
        sendCooldowns.clear();
        presets = Map.of();
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped() {
        return java.util.concurrent.CompletableFuture.allOf(ioExecutor.stopped().toCompletableFuture(),
            expiration.stopped().toCompletableFuture());
    }

    /** A committed reservation must either reach the main thread or be cancelled on the storage worker. */
    private void afterReservation(Runnable effect, Runnable cancel) {
        boolean registered;
        synchronized (unstartedEffects) {
            registered = !closed;
            if (registered) unstartedEffects.add(cancel);
        }
        if (!registered) {
            cancel.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (unstartedEffects) {
                    if (!unstartedEffects.remove(cancel)) return;
                }
                effect.run();
            });
        } catch (RuntimeException failure) {
            synchronized (unstartedEffects) {
                if (!unstartedEffects.remove(cancel)) return;
            }
            cancel.run();
        }
    }

    private void onMain(Runnable task) {
        if (!closed) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed) task.run();
        });
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

    public void showLedger(CommandSender operator) {
        if (!canReconcile(operator)) return;
        adminSql(operator, () -> {
            Instant before = Instant.now().minus(REVIEW_DELAY);
            return new LedgerEntries(repository.loadUncertainDebits(before), repository.loadUncertainDeliveries(before));
        }, (entries, canReply) -> {
            if (!canReply) return;
            notify(operator, "redpacket.ledger-count", entries.debits().size(), entries.deliveries().size());
            for (RedPacket packet : entries.debits()) {
                notify(operator, "redpacket.ledger-entry", packet.id(), packet.senderName(),
                    packet.totalAmount().toPlainString() + " " + packet.currencyId());
            }
            for (RedPacketDelivery delivery : entries.deliveries()) {
                notify(operator, "redpacket.ledger-entry", delivery.id(), delivery.playerId(), describeReward(delivery.reward()));
            }
        }, () -> notify(operator, "result.storage-unavailable"));
    }

    public void reconcile(CommandSender operator, String id, String resolution) {
        if (!canReconcile(operator)) return;
        if (!Set.of("paid", "unpaid", "retry", "done").contains(resolution)) {
            notify(operator, "redpacket.ledger-usage");
            return;
        }
        String actorName = operator.getName();
        adminSql(operator, () -> {
            Instant before = Instant.now().minus(REVIEW_DELAY);
            ReviewResult result;
            if ("paid".equals(resolution) || "unpaid".equals(resolution)) {
                var reconciled = repository.reconcileDebit(id, "paid".equals(resolution), before);
                result = new ReviewResult(reconciled.isPresent(), reconciled.orElse(null));
            } else {
                result = new ReviewResult(repository.reconcileDelivery(id, before,
                    "retry".equals(resolution) ? Status.READY : Status.DELIVERED), null);
            }
            if (result.changed()) logger.info("红包流水核对 " + id + " " + resolution + " by " + actorName);
            return result;
        }, (result, canReply) -> {
            if (result.packet() != null && result.packet().claimable(Instant.now())) {
                broadcastLocal(result.packet());
                publishCreate(result.packet());
            }
            if (canReply) notify(operator, result.changed() ? "redpacket.ledger-resolved" : "redpacket.ledger-missing", id);
        }, () -> notify(operator, "result.storage-unavailable"));
    }

    private record LedgerEntries(List<RedPacket> debits, List<RedPacketDelivery> deliveries) {}
    private record ReviewResult(boolean changed, RedPacket packet) {}

    private <T> void adminSql(CommandSender operator, Callable<T> work, BiConsumer<T, Boolean> committed, Runnable failure) {
        if (closed || !sendSlots.tryAcquire()) {
            failure.run();
            return;
        }
        Player player = operator instanceof Player actor ? actor : null;
        AuthSnapshot admission = player == null ? null : AuthAccess.snapshot(player.getUniqueId());
        ioExecutor.execute(() -> {
            if (closed || (admission != null && !sameAdmission(admission.playerId(), admission))) {
                sendSlots.release();
                return;
            }
            Runnable completion;
            try {
                T value = work.call();
                completion = () -> committed.accept(value, canReplyToAdmin(operator, player, admission));
            } catch (Exception error) {
                logger.warning("红包管理操作失败: " + error.getMessage());
                completion = () -> {
                    if (canReplyToAdmin(operator, player, admission)) failure.run();
                };
            }
            if (closed) {
                sendSlots.release();
                return;
            }
            Runnable reply = completion;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendSlots.release();
                    if (!closed) reply.run();
                });
            } catch (RuntimeException scheduling) {
                sendSlots.release();
                if (!closed) logger.warning("红包管理结果回调失败: " + scheduling.getMessage());
            }
        });
    }

    private boolean canReplyToAdmin(CommandSender operator, Player player, AuthSnapshot admission) {
        return (player == null || sameSession(player, admission)) && hasAdminAccess(operator);
    }

    private boolean hasAdminAccess(CommandSender operator) {
        return !(operator instanceof Player player) || (AuthAccess.admitted(player.getUniqueId())
                && player.hasPermission("arcartxsuite.admin")
                && (config().adminPermission().isBlank() || player.hasPermission(config().adminPermission())));
    }

    private boolean canReconcile(CommandSender operator) {
        if (hasAdminAccess(operator)) return true;
        notify(operator, "redpacket.no-admin-permission");
        return false;
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
     * @param reply 在服务器线程返回操作结果
     */
    public void sendPlayerPacket(Player sender, RedPacketType type, BigDecimal total, int count, String wish,
                                 Consumer<ChatOperationResult> reply) {
        UUID playerId = sender.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(playerId);
        if (!admission.admitted()) {
            reply.accept(ChatOperationResult.failure("请先完成登录验证。"));
            return;
        }
        ChatOperationResult validation = validatePlayerSend(sender, type, total, count);
        if (validation != null) {
            reply.accept(validation);
            return;
        }
        String moderatedWish = chatService.moderateRedPacketWish(wish);
        if (moderatedWish == null) {
            reply.accept(failed("redpacket.invalid-wish"));
            return;
        }
        if (!sendSlots.tryAcquire()) {
            reply.accept(failed("result.storage-unavailable"));
            return;
        }
        if (!pendingSends.add(playerId)) {
            sendSlots.release();
            reply.accept(failed("result.storage-unavailable"));
            return;
        }
        Consumer<ChatOperationResult> result = outcome -> {
            pendingSends.remove(playerId);
            sendSlots.release();
            if (!closed && sameSession(sender, admission)) reply.accept(outcome);
        };
        chatService.prepareRedPacketSend(sender, moderatedWish, (permission, speaking) -> {
            if (!speaking.success()) result.accept(speaking);
            else prepareDebit(sender, admission, type, total, count, moderatedWish, permission, result);
        });
    }

    private ChatOperationResult validatePlayerSend(Player sender, RedPacketType type, BigDecimal total, int count) {
        if (closed || !config().enabled()) return failed("redpacket.disabled");
        if (!config().sendPermission().isBlank() && !sender.hasPermission(config().sendPermission())) {
            return failed("redpacket.no-send-permission");
        }
        if (type != RedPacketType.NORMAL && type != RedPacketType.LUCKY) return failed("redpacket.invalid-type");
        long remaining = config().sendCooldownSeconds() * 1000L
            - (System.currentTimeMillis() - sendCooldowns.getOrDefault(sender.getUniqueId(), 0L));
        if (remaining > 0) return failed("redpacket.cooldown", (long) Math.ceil(remaining / 1000.0));
        return validateAmounts(total, count);
    }

    private void prepareDebit(Player sender, AuthSnapshot admission, RedPacketType type, BigDecimal total, int count,
                              String wish, ChatService.RedPacketPermission permission, Consumer<ChatOperationResult> result) {
        try {
            String currency = config().currency();
            CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
            if (bridge == null || !bridge.available()) {
                result.accept(failed("redpacket.currency-unavailable", currency));
                return;
            }
            List<BigDecimal> amounts = splitAmounts(type, total, count, currencyScale(bridge));
            if (amounts.isEmpty()) {
                result.accept(failed("redpacket.invalid-precision"));
                return;
            }
            var backgroundBalance = bridge.prepareBackgroundBalance(sender);
            BigDecimal balance = backgroundBalance == null ? bridge.balance(sender) : null;
            var packet = buildPacket(newId(), type, admission.playerId().toString(), sender.getName(), sender.getDisplayName(),
                permission.channel(), currency, total, amounts, count, wish, "", false, Duration.ofMinutes(config().expireMinutes()));
            var debit = new PlayerDebit(sender, admission, permission, packet, bridge, result);
            ioExecutor.execute(() -> armDebit(debit, backgroundBalance != null ? backgroundBalance : () -> balance));
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("准备红包扣款失败: " + failure.getMessage());
            result.accept(failed("result.storage-unavailable"));
        }
    }

    private void armDebit(PlayerDebit debit, Supplier<BigDecimal> readBalance) {
        RedPacket packet = debit.packet();
        if (closed) return;
        try {
            BigDecimal balance = readBalance.get();
            if (balance.compareTo(packet.totalAmount()) < 0) {
                onMain(() -> debit.result().accept(failed("redpacket.insufficient", currencyManager.format(packet.currencyId(), balance))));
                return;
            }
            repository.insertRedPacket(packet.withStatus(RedPacket.STATUS_DEBITING), List.of());
        } catch (SQLException | RuntimeException | LinkageError failure) {
            logger.warning("红包扣款意图写入失败 " + packet.id() + ": " + failure.getMessage());
            onMain(() -> debit.result().accept(failed("result.storage-unavailable")));
            return;
        }
        afterReservation(() -> debitOnMain(debit), () -> finishDebit(debit, CurrencyTransactionResult.failure("cancelled"), null));
    }

    private void debitOnMain(PlayerDebit debit) {
        RedPacket packet = debit.packet();
        Player sender = debit.sender();
        ChatOperationResult refusal = debitCurrent(debit) && sameSession(sender, debit.admission())
            ? validatePlayerSend(sender, packet.type(), packet.totalAmount(), packet.totalCount())
            : failed("result.channel-changed-while-sending");
        if (refusal == null) {
            var speaking = chatService.validateRedPacketSend(sender, packet.wish(), debit.permission());
            if (!speaking.success()) refusal = speaking;
        }
        if (refusal != null) {
            ChatOperationResult rejected = refusal;
            ioExecutor.execute(() -> finishDebit(debit, CurrencyTransactionResult.failure("cancelled"), rejected));
            return;
        }
        Supplier<CurrencyTransactionResult> background;
        try {
            background = debit.bridge().prepareBackgroundDebit(sender, packet.totalAmount());
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("准备后台红包扣款失败 " + packet.id() + ": " + failure.getMessage());
            ioExecutor.execute(() -> finishDebit(debit, CurrencyTransactionResult.failure("cancelled"), failed("result.storage-unavailable")));
            return;
        }
        sendCooldowns.put(debit.admission().playerId(), System.currentTimeMillis());
        if (background == null) {
            var payment = invokeDebit(packet, () -> debit.bridge().withdraw(sender, packet.totalAmount()));
            ioExecutor.execute(() -> finishDebit(debit, payment, null));
        } else {
            ioExecutor.execute(() -> {
                var payment = debitCurrent(debit) ? invokeDebit(packet, background) : CurrencyTransactionResult.failure("cancelled");
                finishDebit(debit, payment, null);
            });
        }
    }

    private boolean debitCurrent(PlayerDebit debit) {
        AuthSnapshot admission = debit.admission();
        Instant now = Instant.now();
        return !closed && !debit.packet().expired(now) && debit.packet().createdAt().plus(REVIEW_DELAY).isAfter(now)
            && sameAdmission(admission.playerId(), admission) && chatService.redPacketPolicyCurrent(admission.playerId(), debit.permission());
    }

    private CurrencyTransactionResult invokeDebit(RedPacket packet, Supplier<CurrencyTransactionResult> operation) {
        try {
            return java.util.Objects.requireNonNull(operation.get());
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("红包扣款待核对 " + packet.id() + ": " + failure.getMessage());
            return CurrencyTransactionResult.unknown("");
        }
    }

    private void finishDebit(PlayerDebit debit, CurrencyTransactionResult payment, ChatOperationResult refusal) {
        RedPacket packet = debit.packet();
        try {
            if (!payment.uncertain() && !(payment.success() && debit.bridge().debitRequiresReconciliation())
                && repository.completeDebit(packet.id(), payment.success())) {
                if (payment.success()) {
                    onMain(() -> {
                        debit.result().accept(ChatOperationResult.success(message("redpacket.sent",
                            currencyManager.format(packet.currencyId(), packet.totalAmount()), packet.totalCount())));
                        broadcastLocal(packet);
                        publishCreate(packet);
                    });
                } else onMain(() -> debit.result().accept(refusal != null ? refusal : failed("result.storage-unavailable")));
                return;
            }
        } catch (SQLException | RuntimeException failure) {
            logger.warning("红包扣款回执保存失败 " + packet.id() + ": " + failure.getMessage());
        }
        onMain(() -> debit.result().accept(failed("redpacket.debit-review", packet.id())));
    }

    private record PlayerDebit(Player sender, AuthSnapshot admission, ChatService.RedPacketPermission permission,
                               RedPacket packet, CurrencyBridgeAPI.CurrencyBridge bridge, Consumer<ChatOperationResult> result) {}

    private ChatOperationResult failed(String key, Object... arguments) {
        return ChatOperationResult.failure(message(key, arguments));
    }

    /**
     * 管理员/控制台发送免扣款货币红包。
     */
    public void sendAdminPacket(CommandSender sender, RedPacketType type, BigDecimal total, int count, String wish,
                                Consumer<ChatOperationResult> reply) {
        if (!authorizeAdminSend(sender, reply)) return;
        if (type != RedPacketType.NORMAL && type != RedPacketType.LUCKY) {
            reply.accept(failed("redpacket.invalid-type"));
            return;
        }
        ChatOperationResult validation = validateAmounts(total, count);
        if (validation != null) {
            reply.accept(validation);
            return;
        }
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(config().currency());
        if (bridge == null || !bridge.available()) {
            reply.accept(failed("redpacket.currency-unavailable", config().currency()));
            return;
        }
        String moderatedWish = chatService.moderateRedPacketWish(wish);
        if (moderatedWish == null) {
            reply.accept(failed("redpacket.invalid-wish"));
            return;
        }
        int scale = currencyScale(bridge);
        List<BigDecimal> amounts = splitAmounts(type, total, count, scale);
        if (amounts.isEmpty()) {
            reply.accept(failed("redpacket.invalid-precision"));
            return;
        }
        Player player = sender instanceof Player actor ? actor : null;
        withAdminChannel(sender, channel -> {
            String currency = config().currency();
            RedPacket packet = buildPacket(newId(), type, player == null ? null : player.getUniqueId().toString(),
                sender.getName(), player == null ? sender.getName() : player.getDisplayName(),
                channel, currency, total, amounts, count, moderatedWish, "", true, Duration.ofMinutes(config().expireMinutes()));
            persistThenBroadcast(sender, packet, List.of(),
                () -> message("redpacket.sent", currencyManager.format(currency, total), count), reply);
        }, reply);
    }

    /**
     * 管理员/控制台发送预设奖励池红包。
     *
     * @param sender        发送者（可为控制台）
     * @param presetId      预设 ID
     * @param countOverride 份数覆盖，{@code null} 用预设值
     * @param reply 在服务器线程返回操作结果
     */
    public void sendPresetPacket(CommandSender sender, String presetId, @Nullable Integer countOverride,
                                 Consumer<ChatOperationResult> reply) {
        if (!authorizeAdminSend(sender, reply)) return;
        RedPacketPreset preset = presets.get(presetId == null ? "" : presetId.toLowerCase(java.util.Locale.ROOT));
        if (preset == null || !preset.enabled()) {
            reply.accept(failed("redpacket.preset-not-found", presetId));
            return;
        }
        int count = countOverride != null ? Math.max(1, countOverride) : preset.count();
        if (!preset.replacement()) {
            int totalSlots = 0;
            for (RedPacketPoolEntry entry : preset.rewards()) {
                totalSlots += Math.max(1, entry.quantity());
            }
            count = Math.min(count, totalSlots);
        }
        int packetCount = Math.min(count, config().maxCount());

        List<RedPacketEntryRow> rows = new ArrayList<>();
        for (int i = 0; i < preset.rewards().size(); i++) {
            RedPacketPoolEntry entry = preset.rewards().get(i);
            int remaining = preset.replacement()
                ? (entry.quantity() <= 0 ? -1 : entry.quantity())
                : Math.max(1, entry.quantity());
            rows.add(RedPacketEntryRow.of(i, entry.reward(), entry.weight(), remaining));
        }
        long expireMinutes = preset.expireMinutes() > 0 ? preset.expireMinutes() : config().expireMinutes();
        Player player = sender instanceof Player actor ? actor : null;
        withAdminChannel(sender, channel -> {
            String displayName = preset.displayName().isBlank() ? sender.getName() : preset.displayName();
            RedPacket packet = buildPacket(newId(), RedPacketType.POOL, player == null ? null : player.getUniqueId().toString(),
                displayName, displayName, channel, "", BigDecimal.ZERO, List.of(), packetCount,
                preset.wish(), preset.id(), true, Duration.ofMinutes(expireMinutes));
            persistThenBroadcast(sender, packet, rows, () -> message("redpacket.sent-preset", preset.displayName(), packetCount), reply);
        }, reply);
    }

    private boolean authorizeAdminSend(CommandSender sender, Consumer<ChatOperationResult> reply) {
        if (sender instanceof Player player && !AuthAccess.admitted(player.getUniqueId())) {
            reply.accept(ChatOperationResult.failure("请先完成登录验证。"));
        } else if (closed || !config().enabled()) {
            reply.accept(failed("redpacket.disabled"));
        } else if (!hasAdminAccess(sender)
                || (!config().adminPermission().isBlank() && !sender.hasPermission(config().adminPermission()))) {
            reply.accept(failed("redpacket.no-admin-permission"));
        } else return true;
        return false;
    }

    // ── 抢红包 ────────────────────────────────────────────

    /**
     * 玩家领取红包。DB 事务异步执行，结果回主线程发放奖励并播报。
     */
    public void claimPacket(Player player, String packetId) {
        UUID playerId = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(playerId);
        if (!admission.admitted() || closed) return;
        if (!config().enabled()) {
            notify(player, "redpacket.disabled");
            return;
        }
        String id = packetId == null ? "" : packetId.strip().toLowerCase(java.util.Locale.ROOT);
        if (!id.matches("[0-9a-f]{8,16}")) {
            notify(player, "redpacket.not-found");
            return;
        }
        if (!pendingClaims.add(playerId)) return;
        ioExecutor.execute(() -> {
            try {
                RedPacket packet = repository.findRedPacket(id).orElse(null);
                Bukkit.getScheduler().runTask(plugin, () -> authorizeClaim(player, admission, packet));
            } catch (SQLException exception) {
                pendingClaims.remove(playerId);
                claimFailure(player, admission, id, exception);
            }
        });
    }

    private void authorizeClaim(Player player, AuthSnapshot admission, @Nullable RedPacket packet) {
        UUID playerId = player.getUniqueId();
        if (!sameSession(player, admission) || closed) {
            pendingClaims.remove(playerId);
            return;
        }
        if (!config().claimPermission().isBlank() && !player.hasPermission(config().claimPermission())) {
            pendingClaims.remove(playerId);
            notify(player, "redpacket.no-claim-permission");
            return;
        }
        if (packet == null) {
            pendingClaims.remove(playerId);
            notify(player, "redpacket.not-eligible");
            return;
        }
        if (!routePacket(packet, player, audience -> {
            if (closed || !sameSession(player, admission)) {
                pendingClaims.remove(playerId);
            } else if (audience.isEmpty()) {
                pendingClaims.remove(playerId);
                notify(player, "redpacket.not-eligible");
            } else if (!config().claimPermission().isBlank() && !player.hasPermission(config().claimPermission())) {
                pendingClaims.remove(playerId);
                notify(player, "redpacket.no-claim-permission");
            } else {
                commitClaim(player, admission, packet);
            }
        })) {
            pendingClaims.remove(playerId);
            notify(player, "result.storage-unavailable");
        }
    }

    private void commitClaim(Player player, AuthSnapshot admission, RedPacket packet) {
        UUID playerId = admission.playerId();
        String playerName = player.getName();
        ioExecutor.execute(() -> {
            try {
                if (closed || !sameAdmission(playerId, admission)) return;
                RedPacketClaimOutcome outcome = repository.claimRedPacket(packet, playerId, playerName,
                    config().senderCanClaim());
                Bukkit.getScheduler().runTask(plugin, () -> handleClaimResult(player, outcome, admission));
            } catch (SQLException exception) {
                claimFailure(player, admission, packet.id(), exception);
            } finally {
                pendingClaims.remove(playerId);
            }
        });
    }

    private void claimFailure(Player player, AuthSnapshot admission, String id, SQLException exception) {
        logger.warning("红包领取失败 " + id + ": " + exception.getMessage());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && sameSession(player, admission)) notify(player, "result.storage-unavailable");
        });
    }

    private void handleClaimResult(Player player, RedPacketClaimOutcome outcome, AuthSnapshot admission) {
        if (closed || !sameSession(player, admission)) return;
        RedPacket packet = outcome.packet();
        String key = switch (outcome.status()) {
            case SUCCESS -> "redpacket.claim-queued";
            case NOT_FOUND -> "redpacket.not-found";
            case EXPIRED -> "redpacket.expired";
            case EXHAUSTED -> "redpacket.exhausted";
            case ALREADY_CLAIMED -> "redpacket.already-claimed";
            case SENDER_DENIED -> "redpacket.sender-denied";
            case NOT_ELIGIBLE -> "redpacket.not-eligible";
        };
        notify(player, key);
        if (outcome.status() != RedPacketClaimOutcome.Status.SUCCESS || packet == null) return;
        if (config().announceClaims()) {
            announce(packet, message("redpacket.broadcast-claim", player.getName(), packet.senderDisplay(),
                describeReward(outcome.reward())));
            publishClaimAnnounce(packet, player);
        }
        if (RedPacket.STATUS_EXHAUSTED.equals(packet.status())) announceFinish(packet);
    }

    private static boolean sameSession(Player player, AuthSnapshot previous) {
        return player.isOnline() && sameAdmission(player.getUniqueId(), previous);
    }

    private static boolean sameAdmission(UUID playerId, AuthSnapshot previous) {
        AuthSnapshot current = AuthAccess.snapshot(playerId);
        return current.admitted() && current.generation() == previous.generation()
            && java.util.Objects.equals(current.connectionId(), previous.connectionId());
    }

    private void notify(CommandSender recipient, String key, Object... arguments) {
        recipient.sendMessage(prefix() + message(key, arguments));
    }

    // ── 广播与播报 ─────────────────────────────────────────

    private void persistThenBroadcast(CommandSender sender, RedPacket packet, List<RedPacketEntryRow> entries,
                                      Supplier<String> success, Consumer<ChatOperationResult> reply) {
        adminSql(sender, () -> {
            repository.insertRedPacket(packet, entries);
            return packet;
        }, (saved, canReply) -> {
            broadcastLocal(saved);
            publishCreate(saved);
            if (canReply) reply.accept(ChatOperationResult.success(success.get()));
        }, () -> reply.accept(failed("result.storage-unavailable")));
    }

    static boolean usableChannel(@Nullable ChatChannelDefinition channel, @Nullable Player sender) {
        return channel != null && channel.mode() != ChatChannelMode.PRIVATE && channel.mode() != ChatChannelMode.PARTY
            && (channel.range() == 0.0D || (channel.mode() == ChatChannelMode.NORMAL && sender != null));
    }

    private ChatChannelDefinition packetChannel(RedPacket packet) {
        ChatChannelDefinition channel = configuration.channel(packet.channelId());
        if (channel == null || channel.mode() == ChatChannelMode.PRIVATE || channel.mode() == ChatChannelMode.PARTY) return null;
        if (!channel.audienceKey().equals(packet.audienceKey())) return null;
        return chatService.nodeId().equals(packet.originNode())
            || (crossServerEnabled() && channel.permitsCrossServer()) ? channel : null;
    }

    private boolean routePacket(RedPacket packet, Player viewer, Consumer<List<Player>> deliver) {
        ChatChannelDefinition channel = packetChannel(packet);
        UUID senderId = packet.senderUuid() == null ? null : UUID.fromString(packet.senderUuid());
        Player sender = senderId != null && chatService.nodeId().equals(packet.originNode()) ? Bukkit.getPlayer(senderId) : null;
        if (channel == null || (channel.range() > 0.0D && (sender == null || !sender.isOnline()))) {
            deliver.accept(List.of());
            return true;
        }
        return chatService.routeRedPacket(channel, sender, senderId, viewer, deliver);
    }

    void broadcastLocal(RedPacket packet) {
        if (closed || !config().enabled() || !packet.claimable(Instant.now())) return;
        if (deferredCards.containsKey(packet.id()) || (deferredCards.isEmpty() && tryBroadcastCard(packet))) return;
        if (deferredCards.size() >= MAX_DEFERRED_CARDS) {
            Instant now = Instant.now();
            deferredCards.values().removeIf(card -> !card.claimable(now));
        }
        if (deferredCards.size() >= MAX_DEFERRED_CARDS) return;
        deferredCards.put(packet.id(), packet);
        scheduleCardRetry();
    }

    private boolean tryBroadcastCard(RedPacket packet) {
        return routePacket(packet, null, recipients -> {
            if (closed || !packet.claimable(Instant.now())) return;
            for (Player recipient : recipients) deliverPacket(recipient, packet);
        });
    }

    private void scheduleCardRetry() {
        if (!closed && cardRetry == null && !deferredCards.isEmpty()) {
            cardRetry = Bukkit.getScheduler().runTaskLater(plugin, this::retryCards, 10L);
        }
    }

    private void retryCards() {
        cardRetry = null;
        if (closed) return;
        var cards = deferredCards.values().iterator();
        Instant now = Instant.now();
        for (int i = 0; i < CARD_RETRY_BATCH && cards.hasNext(); i++) {
            RedPacket packet = cards.next();
            if (packet.claimable(now) && !tryBroadcastCard(packet)) break;
            cards.remove();
        }
        scheduleCardRetry();
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
        if (closed || !config().enabled()) return;
        String rendered = prefix() + text;
        routePacket(packet, null, recipients -> {
            if (closed) return;
            for (Player recipient : recipients) recipient.sendMessage(rendered);
        });
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

    private void sweepExpired() {
        if (closed) return;
        try {
            Instant now = Instant.now();
            for (RedPacket packet : repository.loadActiveRedPackets()) {
                if (closed) break;
                if (crossServerStorage && !packet.originNode().isBlank()
                    && !chatService.nodeId().equals(packet.originNode()) && !crossServerEnabled()) continue;
                if (packet.expired(now)) repository.expireRedPacket(packet.id());
            }
        } catch (SQLException exception) {
            logger.warning("红包过期结算失败: " + exception.getMessage());
        }
    }

    private void queueDeliveries() {
        if (closed || !deliveryScan.compareAndSet(false, true)) return;
        Map<UUID, AuthSnapshot> sessions = new java.util.LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AuthSnapshot snapshot = AuthAccess.snapshot(player.getUniqueId());
            if (snapshot.admitted()) sessions.put(player.getUniqueId(), snapshot);
        }
        ioExecutor.execute(() -> {
            try {
                List<UUID> players = new ArrayList<>(sessions.keySet());
                for (int start = 0; start < players.size(); start += 200) {
                    var batch = java.util.Set.copyOf(players.subList(start, Math.min(start + 200, players.size())));
                    for (RedPacketDelivery delivery : repository.loadReadyDeliveries(batch, 20)) {
                        if (closed) return;
                        if (!reserveDelivery(delivery, sessions.get(delivery.playerId()))) return;
                    }
                }
            } catch (SQLException exception) {
                logger.warning("读取红包待发奖记录失败: " + exception.getMessage());
            } finally {
                deliveryScan.set(false);
            }
        });
    }

    private boolean reserveDelivery(RedPacketDelivery delivery, AuthSnapshot admission) {
        if (!sameAdmission(delivery.playerId(), admission)) return true;
        if (!deliverySlots.tryAcquire()) return false;
        var reservedAt = nextDeliveryStamp(delivery);
        boolean reserved = false;
        try {
            reserved = repository.transitionDelivery(delivery, Status.EXECUTING, reservedAt);
        } catch (SQLException exception) {
            logger.warning("无法预留红包发放 " + delivery.id() + ": " + exception.getMessage());
        } finally {
            if (!reserved) deliverySlots.release();
        }
        if (reserved) {
            var active = new RedPacketDelivery(delivery.id(), delivery.packetId(), delivery.playerId(), delivery.reward(),
                delivery.refund(), Status.EXECUTING, reservedAt);
            if (active.reward().kind() == RedPacketRewardKind.ITEM) {
                finishDelivery(active, admission, handoffItem(active, admission));
            } else {
                afterReservation(() -> deliverReward(active, admission), () -> finishDelivery(active, admission, Status.READY));
            }
        }
        return true;
    }

    private Status handoffItem(RedPacketDelivery delivery, AuthSnapshot admission) {
        if (closed || pendingRewards == null || !sameAdmission(delivery.playerId(), admission)) return Status.READY;
        String data = delivery.reward().itemData();
        if (data == null || data.isBlank()) return Status.REVIEW;
        try {
            var reward = PendingRewardService.PendingReward.itemSerialized(delivery.playerId(), MODULE_ID, data,
                "红包物品 " + delivery.id());
            return pendingRewards.savePendingBatchConfirmed("redpacket:item:" + delivery.id(), List.of(reward))
                ? Status.HANDED_OFF : Status.REVIEW;
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("红包物品持久交接待核对 " + delivery.id() + ": " + failure.getMessage());
            return Status.REVIEW;
        }
    }

    private void deliverReward(RedPacketDelivery delivery, AuthSnapshot admission) {
        Player player = Bukkit.getPlayer(delivery.playerId());
        if (closed || player == null || !sameSession(player, admission) || deliveryReviewDue(delivery)) {
            ioExecutor.execute(() -> finishDelivery(delivery, admission, Status.READY));
            return;
        }
        RedPacketReward reward = delivery.reward();
        Status status;
        try {
            if (reward.kind() == RedPacketRewardKind.CURRENCY) {
                CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(reward.currencyId());
                if (bridge == null || !bridge.available()) status = Status.READY;
                else if (reward.amount() == null || reward.amount().signum() <= 0) status = Status.REVIEW;
                else {
                    boolean requiresReview = bridge.creditRequiresReconciliation();
                    var credit = bridge.prepareBackgroundCredit(player, reward.amount());
                    if (credit != null) {
                        ioExecutor.execute(() -> {
                            Status result = closed || !sameAdmission(delivery.playerId(), admission) || deliveryReviewDue(delivery)
                                ? Status.READY
                                : creditStatus(delivery, credit, requiresReview);
                            finishDelivery(delivery, admission, result);
                        });
                        return;
                    }
                    status = creditStatus(delivery, () -> bridge.deposit(player, reward.amount()), requiresReview);
                }
            } else status = grantReward(player, delivery);
        } catch (RuntimeException | LinkageError exception) {
            logger.warning("红包发放结果待核对 " + delivery.id() + ": " + exception.getMessage());
            status = Status.REVIEW;
        }
        Status result = status;
        ioExecutor.execute(() -> finishDelivery(delivery, admission, result));
    }

    private Status creditStatus(RedPacketDelivery delivery, Supplier<CurrencyTransactionResult> credit, boolean requiresReview) {
        try {
            var result = credit.get();
            return result.success() && !result.uncertain() && !requiresReview ? Status.DELIVERED : Status.REVIEW;
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("红包入账结果待核对 " + delivery.id() + ": " + failure.getMessage());
            return Status.REVIEW;
        }
    }

    private void finishDelivery(RedPacketDelivery delivery, AuthSnapshot admission, Status result) {
        try {
            if (!repository.transitionDelivery(delivery, result, nextDeliveryStamp(delivery))) return;
            if (result == Status.DELIVERED || result == Status.HANDED_OFF) {
                onMain(() -> {
                    Player player = Bukkit.getPlayer(delivery.playerId());
                    if (player != null && sameSession(player, admission)) {
                        if (result == Status.HANDED_OFF) {
                            pendingRewards.flushPlayer(player);
                            notify(player, "redpacket.item-queued", describeReward(delivery.reward()));
                        } else {
                            notify(player, delivery.refund() ? "redpacket.refunded" : "redpacket.claimed",
                                describeReward(delivery.reward()));
                        }
                    }
                });
            } else if (result == Status.REVIEW) {
                logger.warning("红包发放待核对 " + delivery.id());
            }
        } catch (SQLException exception) {
            logger.warning("无法保存红包发放结果 " + delivery.id() + ": " + exception.getMessage());
        } finally {
            deliverySlots.release();
        }
    }

    private static Instant nextDeliveryStamp(RedPacketDelivery delivery) {
        return Instant.ofEpochMilli(Math.max(System.currentTimeMillis(), delivery.updatedAt().toEpochMilli() + 1));
    }

    private static boolean deliveryReviewDue(RedPacketDelivery delivery) {
        return !delivery.updatedAt().plus(REVIEW_DELAY).isAfter(Instant.now());
    }

    private Status grantReward(Player player, RedPacketDelivery delivery) {
        RedPacketReward reward = delivery.reward();
        return switch (reward.kind()) {
            case CURRENCY -> throw new IllegalArgumentException("Currency credit must use the currency bridge");
            case ITEM -> throw new IllegalArgumentException("物品红包必须交接到持久待发队列");
            case COMMAND -> {
                String name = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
                if (name.isBlank()) yield Status.REVIEW;
                String command = reward.command().replace("{player}", name);
                if (command.startsWith("/")) command = command.substring(1);
                yield Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command) ? Status.DELIVERED : Status.REVIEW;
            }
        };
    }

    /** 重启/重载后向全部在线玩家重播未过期红包。 */
    private void rebroadcastActive() {
        if (closed) return;
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
                    broadcastLocal(packet);
                }
            });
        });
    }

    /** 玩家进服后重播其可见的未过期红包。 */
    public void onPlayerJoin(Player player) {
        if (closed || !config().enabled()) {
            return;
        }
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (closed || !sameSession(player, admission)) {
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
                            && packet.senderUuid().equals(playerId.toString())) {
                        continue;
                    }
                    alive.add(packet);
                }
                if (alive.isEmpty()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (closed || !sameSession(player, admission)) return;
                    for (RedPacket packet : alive) {
                        routePacket(packet, player, audience -> {
                            if (!closed && !audience.isEmpty() && sameSession(player, admission) && packet.claimable(Instant.now())) {
                                deliverPacket(player, packet);
                            }
                        });
                    }
                });
            });
        }, REBROADCAST_DELAY_TICKS);
    }

    // ── 跨服 ─────────────────────────────────────────────

    /** 红包跨服广播仅在共享 MySQL 时启用（SQLite 各服独立，无法同步领取状态）。 */
    public boolean crossServerEnabled() {
        return BuildEdition.CROSS_SERVER_INCLUDED && crossServerStorage && chatService.crossServerActive();
    }

    private void publishCreate(RedPacket packet) {
        publishPacketEvent(packet, "create-v2", null);
    }

    private void publishClaimAnnounce(RedPacket packet, Player claimer) {
        publishPacketEvent(packet, "claim-v2", claimer.getUniqueId());
    }

    private void publishPacketEvent(RedPacket packet, String action, @Nullable UUID claimer) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        ChatChannelDefinition channel = packetChannel(packet);
        if (!crossServerEnabled() || channel == null || !channel.permitsCrossServer()) return;
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("action", action);
        yaml.set("packet-id", packet.id());
        if (claimer != null) yaml.set("claimer", claimer.toString());
        chatService.publishCrossServer(CROSS_PREFIX + yaml.saveToString());
    }

    public void handleRemotePayload(String payload, String sourceNode) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (closed || !config().enabled() || !crossServerEnabled()) return;
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(payload.substring(CROSS_PREFIX.length()));
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            logger.warning("跨服红包消息格式错误: " + exception.getMessage());
            return;
        }
        String action = yaml.getString("action", "");
        String packetId = yaml.getString("packet-id", "");
        if (!packetId.matches("[0-9a-f]{8,16}") || !List.of("create-v2", "claim-v2").contains(action)) return;
        ioExecutor.execute(() -> {
            if (closed || !crossServerEnabled()) return;
            try {
                RedPacket packet = repository.findRedPacket(packetId).orElse(null);
                if (packet == null) return;
                if ("create-v2".equals(action) && packet.originNode().equals(sourceNode)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!closed && crossServerEnabled()) broadcastLocal(packet);
                    });
                } else if ("claim-v2".equals(action)) {
                    String claimer = yaml.getString("claimer", "");
                    for (RedPacketClaim claim : repository.listRedPacketClaims(packetId)) {
                        if (!claim.playerUuid().equals(claimer)) continue;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!closed && crossServerEnabled() && config().announceClaims()) announce(packet, message("redpacket.broadcast-claim-remote",
                                claim.playerName(), packet.senderDisplay(), claim.rewardDesc()));
                        });
                        break;
                    }
                }
            } catch (SQLException exception) {
                logger.warning("读取跨服红包 " + packetId + " 失败: " + exception.getMessage());
            }
        });
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
        return splitAmounts(type, total, count, scale, BigDecimal.valueOf(config().minPerClaim()));
    }

    static List<BigDecimal> splitAmounts(RedPacketType type, BigDecimal total, int count, int scale, BigDecimal minimum) {
        if (total.stripTrailingZeros().scale() > scale) return List.of();
        total = total.setScale(scale);
        BigDecimal min = minimum.setScale(scale, RoundingMode.CEILING).max(BigDecimal.ONE.movePointLeft(scale));
        if (total.compareTo(min.multiply(BigDecimal.valueOf(count))) < 0) return List.of();
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
            String senderName, String senderDisplay, ChatChannelDefinition channel, String currencyId,
            BigDecimal total, List<BigDecimal> amounts, int count, String wish, String presetId,
            boolean admin, Duration ttl) {
        Instant now = Instant.now();
        return new RedPacket(
            id, type, senderUuid, senderName, senderDisplay, channel.id(), currencyId,
            total, total, amounts, count, 0, wish == null ? "" : wish, presetId, admin,
            RedPacket.STATUS_ACTIVE, now, now.plus(ttl), chatService.nodeId(), channel.audienceKey(),
            !admin && config().refundOnExpire()
        );
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void withAdminChannel(CommandSender sender, Consumer<ChatChannelDefinition> send,
                                  Consumer<ChatOperationResult> reply) {
        if (sender instanceof Player player) {
            UUID playerId = player.getUniqueId();
            long revision = chatService.channelRevision(playerId);
            adminSql(sender, () -> repository.loadState(playerId, configuration.defaultChannelId()).currentChannelId(),
                (channelId, canReply) -> {
                    if (!canReply) return;
                    if (chatService.channelRevision(playerId) != revision) {
                        reply.accept(failed("result.channel-changed-while-sending"));
                        return;
                    }
                    ChatChannelDefinition channel = configuration.channel(channelId);
                    if (usableChannel(channel, player)) send.accept(channel);
                    else reply.accept(failed("result.no-channel"));
                }, () -> reply.accept(failed("result.storage-unavailable")));
            return;
        }
        String configured = config().adminChannel();
        ChatChannelDefinition channel = chatService.channel(configured.isBlank() ? null : configured);
        if (usableChannel(channel, null)) send.accept(channel);
        else reply.accept(failed("result.no-channel"));
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
