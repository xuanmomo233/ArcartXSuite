package xuanmo.arcartxsuite.market.auction;

import xuanmo.arcartxsuite.market.MarketWork;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.AuctionConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.storage.AuctionHistory;
import xuanmo.arcartxsuite.market.storage.AuctionBidOrder;
import xuanmo.arcartxsuite.market.storage.AuctionPublication;
import xuanmo.arcartxsuite.market.storage.AuctionSettlement;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 拍卖行核心业务服务。
 */
public class AuctionService {
    private xuanmo.arcartxsuite.market.MarketLedger ledger;

    public void setLedger(xuanmo.arcartxsuite.market.MarketLedger ledger) { this.ledger = ledger; }


    /** EventBus 发布回调（由 MarketService 注入）。 */
    public interface AuctionEventPublisher {
        CompletionStage<Boolean> publish(String topic, @Nullable Player player, Map<String, String> payload);
    }

    private record AuctionEvent(@Nullable Player buyer, Map<String, String> payload) { }

    private static final int EXPIRED_SCAN_LIMIT = 128;
    private static final int MAX_SETTLEMENTS_PER_TICK = 4;
    private static final int MAX_PENDING_BIDS = 32;
    private static final int BID_PAYMENTS_PER_TICK = 4;
    private static final long SETTLEMENT_BUDGET_NANOS = 2_000_000L;
    private final JavaPlugin plugin;
    private final MarketWork work;
    private final AuctionConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final RedisMarketCache redisCache;
    private final @Nullable Consumer<String> crossServerPublisher;
    private final CurrencyBridgeAPI currencyManager;
    private final AuctionItemSerializer itemSerializer;
    private final Logger logger;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher;
    private final ItemMatcherAPI itemMatcher;
    private volatile AuctionEventPublisher eventPublisher;
    private volatile java.util.function.BiConsumer<Player, Map<String, String>> eventObserver;
    private final ConcurrentLinkedQueue<AuctionListing> expired = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PreparedExpiry> preparedExpired = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ArmedBid> armedBids = new ConcurrentLinkedQueue<>();
    private final Set<UUID> pendingBidPlayers = ConcurrentHashMap.newKeySet();
    private final Semaphore backgroundBidSlots = new Semaphore(4);
    private final Semaphore cancellationSlots = new Semaphore(32);
    private final Set<CompletableFuture<Boolean>> cancellationsAwaitingMain = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scanningExpired = new AtomicBoolean();
    private final AtomicBoolean settlingExpired = new AtomicBoolean();
    private final AtomicBoolean publishingExpired = new AtomicBoolean();
    private volatile @Nullable SettlementRefresh settlementRefresh;
    private volatile boolean stopped;
    private BukkitTask schedulerTask;
    private BukkitTask settlementTask;
    private BukkitTask publicationTask;
    private volatile @Nullable OfflineAuctionTax offlineAuctionTax;

    public void setEventPublisher(AuctionEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    public void setEventObserver(java.util.function.BiConsumer<Player, Map<String, String>> observer) {
        this.eventObserver = observer;
    }

    public void setSettlementRefresh(java.util.function.BooleanSupplier authorized,
            java.util.function.Function<String, java.util.concurrent.CompletionStage<Boolean>> publisher) {
        this.settlementRefresh = new SettlementRefresh(authorized, publisher);
    }

    private record SettlementRefresh(java.util.function.BooleanSupplier authorized,
            java.util.function.Function<String, java.util.concurrent.CompletionStage<Boolean>> publish) {}

    private boolean settlementRefreshAllowed() {
        var refresh = settlementRefresh;
        return refresh != null && refresh.authorized().getAsBoolean();
    }

    public AuctionService(MarketWork work, JavaPlugin plugin, AuctionConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, RedisMarketCache redisCache,
                          @Nullable Consumer<String> crossServerPublisher,
                          CurrencyBridgeAPI currencyManager,
                          AuctionItemSerializer itemSerializer, Logger logger,
                          xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
                          xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher,
                          ItemMatcherAPI itemMatcher) {
        this.plugin = plugin;this.work = work;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.redisCache = redisCache;
        this.crossServerPublisher = crossServerPublisher;
        this.currencyManager = currencyManager;
        this.itemSerializer = itemSerializer;
        this.logger = logger;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.itemMatcher = itemMatcher;
    }

    public void start(long intervalTicks) {
        stopped = false;
        var luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms != null && luckPerms.isEnabled() && !config.taxDiscount().isEmpty()) {
            try { offlineAuctionTax = new OfflineAuctionTax(luckPerms); }
            catch (ReflectiveOperationException | LinkageError exception) {
                logger.log(Level.WARNING, "[Auction] 离线税率权限桥接不可用", exception);
            }
        }
        settlementTask = work.repeat(() -> {
            settleExpired();
            drainBidPayments();
        }, 1L, 1L);
        publicationTask = work.repeatAsync(this::publishSettlements, 20L, 20L);
        schedulerTask = work.repeatAsync(this::processExpired, intervalTicks, intervalTicks);
        logger.info("[Auction] 拍卖行服务已启动，到期检查间隔: " + intervalTicks + " ticks");
    }

    public void shutdown() {
        stopped = true;
        for (var request : List.copyOf(cancellationsAwaitingMain)) request.complete(false);
        for (ArmedBid bid; (bid = armedBids.poll()) != null; ) {
            ArmedBid unstarted = bid;
            work.async(() -> cancelArmedBid(unstarted));
        }
        pendingBidPlayers.clear();
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        if (settlementTask != null) {
            settlementTask.cancel();
            settlementTask = null;
        }
        if (publicationTask != null) {
            publicationTask.cancel();
            publicationTask = null;
        }
        expired.clear();
        preparedExpired.clear();
    }


    private String listingMaterial(AuctionListing listing) {
        String material="";
        try{ItemStack item=itemSerializer.deserialize(listing.getItemData());if(item!=null)material=item.getType().name();}
        catch(RuntimeException ignored){}
        return material;
    }


    /**
     * 竞价。
     */
    public void placeBid(Player bidder, BidConfirmation confirmation, CurrencyBridgeAPI payments,
                         BooleanSupplier authorized, Consumer<BidResult> reply) {
        UUID bidderId = bidder.getUniqueId();
        var session = AuthAccess.snapshot(bidderId);
        if (stopped || work.isStopping() || !session.admitted() || pendingBidPlayers.size() >= MAX_PENDING_BIDS
                || !pendingBidPlayers.add(bidderId)) {
            reply.accept(BidResult.fail("竞价暂不可用或已有竞价正在处理，请稍后重试"));
            return;
        }
        var request = new RequestedBid(bidder, session, bidder.getName(), confirmation, payments, authorized, reply);
        try {
            work.async(() -> loadBid(request));
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            pendingBidPlayers.remove(bidderId);
            reply.accept(BidResult.fail("拍卖正在停用，请重新报价"));
        }
    }

    private void loadBid(RequestedBid request) {
        var confirmation = request.confirmation();
        try {
            AuctionListing listing = repository.getListing(confirmation.listing());
            if (listing == null || !listing.isActive() || listing.getType() == AuctionListing.ListingType.BUY_NOW
                    || request.session().playerId().equals(listing.getSeller())) {
                replyBid(request, BidResult.fail("该物品已不可竞价"));
                return;
            }
            if (!AuctionBidTerms.revision(listing).equals(confirmation.revision())) {
                replyBid(request, BidResult.fail("拍卖状态已变化，请重新报价后提交"));
                return;
            }
            work.main(() -> prepareBidPayment(request, listing));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 竞价读取失败 #" + confirmation.listing(), failure);
            replyBid(request, BidResult.fail("拍卖状态读取失败，请重新报价"));
        }
    }

    private void prepareBidPayment(RequestedBid request, AuctionListing listing) {
        try {
            if (!mayStartBid(request, listing) || !request.authorized().getAsBoolean()) {
                replyBid(request, BidResult.fail("报价或会话已失效，请重新报价"));
                return;
            }
            double amount = request.confirmation().amount();
            BigDecimal minimum = minimumBid(listing);
            if (!Double.isFinite(amount) || amount <= 0 || amount > config.maxPrice()
                    || BigDecimal.valueOf(amount).stripTrailingZeros().scale() > currencyScale(listing.getCurrency())
                    || BigDecimal.valueOf(amount).compareTo(minimum) < 0) {
                replyBid(request, BidResult.fail("出价低于最低价、超过上限或精度无效，请重新报价"));
                return;
            }
            var bridge = request.payments().bridge(listing.getCurrency());
            if (bridge == null || !bridge.available()) {
                replyBid(request, BidResult.fail("货币系统不可用"));
                return;
            }
            UUID bidder = request.session().playerId();
            var debitAmount = BigDecimal.valueOf(amount).subtract(bidder.equals(listing.getHighestBidder())
                ? BigDecimal.valueOf(listing.getCurrentBid()) : BigDecimal.ZERO);
            if (debitAmount.signum() > 0 && bridge.debitRequiresReconciliation()) {
                replyBid(request, BidResult.fail("该货币无法确认扣款"));
                return;
            }
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', listing.getItemDisplayName()));
            String label = name.substring(0, Math.min(255, name.length()));
            String material = listingMaterial(listing);
            var order = new MarketOperation(id, id, bidder, "AUCTION_BID", "listing=" + listing.getId() + ",bid=" + amount,
                "RUNNING", listing.getCurrency(), BigDecimal.valueOf(amount).toPlainString(), listing.getItemData(),
                "等待确认扣款", ledger.owner(), now, now, label, material);
            var debit = new MarketOperation(id + ":debit", id, bidder, "DEBIT", "currency", "RUNNING", listing.getCurrency(),
                debitAmount.toPlainString(), listing.getItemData(), "调用货币接口前记录", ledger.owner(), now, now, label, material);
            var background = bridge.prepareBackgroundDebit(request.player(), debitAmount);
            var prepared = new ArmedBid(request, new AuctionBidOrder(listing, order, debit, amount, request.bidderName()),
                background, () -> bridge.withdraw(request.player(), debitAmount));
            work.async(() -> armBid(prepared));
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "[Auction] 竞价支付准备失败 #" + listing.getId(), failure);
            replyBid(request, BidResult.fail("竞价支付准备失败，请重新报价"));
        }
    }

    private void armBid(ArmedBid bid) {
        try {
            if (!mayStartBid(bid.request(), bid.order().expected()) || !repository.reserveAuctionBid(bid.order())) {
                replyBid(bid.request(), BidResult.fail("拍卖状态已变化，请重新报价后提交"));
                return;
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 竞价扣款意图未确认 " + bid.order().order().id(), failure);
            replyBid(bid.request(), BidResult.fail("扣款意图未确认，货币接口未调用"));
            return;
        }
        armedBids.add(bid);
        if ((stopped || work.isStopping()) && armedBids.remove(bid)) cancelArmedBid(bid);
    }

    void drainBidPayments() {
        for (int i = 0; i < BID_PAYMENTS_PER_TICK && !stopped && !work.isStopping(); i++) {
            if (backgroundBidSlots.availablePermits() == 0) return;
            ArmedBid bid = armedBids.poll();
            if (bid == null) return;
            if (!eligibleArmedBid(bid)) {
                work.async(() -> cancelArmedBid(bid));
                continue;
            }
            if (bid.background() == null) {
                var payment = invokeBidDebit(bid, bid.mainThreadDebit());
                work.async(() -> finishBid(bid, payment));
                return;
            }
            if (!backgroundBidSlots.tryAcquire()) {
                armedBids.add(bid);
                return;
            }
            work.async(() -> {
                try {
                    if (!mayStartBid(bid.request(), bid.order().expected())) cancelArmedBid(bid);
                    else finishBid(bid, invokeBidDebit(bid, bid.background()));
                } finally {
                    backgroundBidSlots.release();
                }
            });
        }
    }

    private boolean eligibleArmedBid(ArmedBid bid) {
        try {
            return mayStartBid(bid.request(), bid.order().expected()) && bid.request().authorized().getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "[Auction] 竞价会话检查失败 " + bid.order().order().id(), failure);
            return false;
        }
    }

    private boolean mayStartBid(RequestedBid request, AuctionListing listing) {
        var session = request.session();
        var current = AuthAccess.snapshot(session.playerId());
        return !stopped && !work.isStopping() && listing.isActive()
            && request.confirmation().validUntil() - System.nanoTime() > 0
            && current.admitted() && current.generation() == session.generation()
            && java.util.Objects.equals(current.connectionId(), session.connectionId());
    }

    private CurrencyTransactionResult invokeBidDebit(ArmedBid bid, Supplier<CurrencyTransactionResult> operation) {
        try {
            return java.util.Objects.requireNonNull(operation.get());
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "[Auction] 竞价扣款结果不明 " + bid.order().order().id(), failure);
            return CurrencyTransactionResult.unknown("");
        }
    }

    private void cancelArmedBid(ArmedBid bid) {
        finishBid(bid, CurrencyTransactionResult.failure("报价、会话或模块已失效"));
    }

    private void finishBid(ArmedBid bid, CurrencyTransactionResult payment) {
        var order = bid.order();
        long expiry = extendedExpiry(order.expected(), System.currentTimeMillis());
        AuctionBidOrder.Outcome outcome;
        try {
            outcome = repository.finishAuctionBid(order, payment, expiry);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 竞价回执未确认 " + order.order().id(), failure);
            replyBid(bid.request(), BidResult.fail("竞价结果未确认，请核对流水 " + order.order().id()));
            return;
        }
        BidResult reply = switch (outcome) {
            case PLACED -> BidResult.success(order.amount(), expiry > order.expected().getExpiresAt());
            case REFUND_QUEUED -> BidResult.fail("拍卖状态已变化，本次扣款已进入退款队列，请重新报价");
            case DECLINED -> BidResult.fail(payment.message() == null || payment.message().isBlank()
                ? messages.insufficientFunds() : payment.message());
            case REVIEW -> BidResult.fail("竞价扣款结果不明，请核对流水 " + order.order().id());
        };
        if (outcome == AuctionBidOrder.Outcome.PLACED) refreshBidSurfaces(order);
        replyBid(bid.request(), reply);
        if (outcome == AuctionBidOrder.Outcome.PLACED) notifyBid(order, expiry);
    }

    private void replyBid(RequestedBid bid, BidResult reply) {
        UUID player = bid.session().playerId();
        if (stopped || work.isStopping()) {
            pendingBidPlayers.remove(player);
            return;
        }
        work.main(() -> {
            pendingBidPlayers.remove(player);
            bid.reply().accept(reply);
        });
    }

    private void refreshBidSurfaces(AuctionBidOrder bid) {
        try {
            if (redisCache.isAvailable()) redisCache.invalidateByPrefix("market:listings:");
            publishCrossServer("BID_PLACED:" + bid.expected().getId() + ":" + bid.amount() + ":" + bid.order().player());
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 竞价已提交，跨服刷新失败 #" + bid.expected().getId(), failure);
        }
    }

    private void notifyBid(AuctionBidOrder bid, long expiry) {
        var listing = bid.expected();
        var order = bid.order();
        long id = listing.getId();
        UUID previous = listing.getHighestBidder();
        try {
            String price = currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(bid.amount()));
            if (previous != null && !previous.equals(order.player()) && listing.getCurrentBid() > 0 && config.notifyOutbid()) {
                String message = messages.auctionOutbid().replace("{item}", listing.getItemDisplayName()).replace("{amount}", price)
                    + " &7原出价已进入退款队列。";
                repository.insertOperation(settlementDelivery(order, previous, "NOTICE", "", "0", message,
                    "notice-" + config.outbidNotify() + ":outbid:" + id));
            }
            if (config.notifyWatchBids() || expiry > listing.getExpiresAt()) {
                String text = "&e关注的拍卖 #" + id + " &f" + listing.getItemDisplayName() + " &7最新出价 &e" + price
                    + (expiry > listing.getExpiresAt() ? " &6已延时，请查看最新截止时间" : "");
                for (UUID watcher : repository.auctionWatchers(id)) {
                    if (!watcher.equals(order.player()) && !watcher.equals(previous)) {
                        repository.insertOperation(settlementDelivery(order, watcher, "NOTICE", "", "0", text, "notice-chat:watch-bid:" + id));
                    }
                }
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 竞价已提交，通知发送失败 #" + id, failure);
        }
    }

    public record BidConfirmation(long listing, double amount, String revision, long validUntil) {}
    private record RequestedBid(Player player, AuthSnapshot session, String bidderName, BidConfirmation confirmation,
                                CurrencyBridgeAPI payments, BooleanSupplier authorized, Consumer<BidResult> reply) {}
    private record ArmedBid(RequestedBid request, AuctionBidOrder order, Supplier<CurrencyTransactionResult> background,
                            Supplier<CurrencyTransactionResult> mainThreadDebit) {}
    /**
     * 取消上架。
     */
    public CompletableFuture<Boolean> cancelListing(UUID actor, long listingId, boolean forced, BooleanSupplier authorized) {
        if (listingId <= 0 || stopped || work.isStopping() || !cancellationSlots.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        cancellationsAwaitingMain.add(result);
        result.whenComplete((committed, failure) -> {
            cancellationsAwaitingMain.remove(result);
            cancellationSlots.release();
        });
        var request = new Cancellation(actor, listingId, forced, authorized, result);
        try {
            work.async(() -> readCancellation(request));
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result.copy();
    }

    private void readCancellation(Cancellation request) {
        try {
            var listing = repository.getListing(request.listing());
            if (!canCancel(request, listing) || stopped || work.isStopping() || !plugin.isEnabled()) {
                request.result().complete(false);
                return;
            }
            work.main(() -> prepareCancellation(request, listing));
        } catch (RuntimeException failure) {
            request.result().completeExceptionally(failure);
        }
    }

    private void prepareCancellation(Cancellation request, AuctionListing listing) {
        if (!cancellationsAwaitingMain.remove(request.result())) return;
        try {
            if (stopped || work.isStopping() || !request.authorized().getAsBoolean()) {
                request.result().complete(false);
                return;
            }
            String material = listingMaterial(listing);
            work.async(() -> commitCancellation(request, listing, material));
        } catch (RuntimeException | LinkageError failure) {
            request.result().completeExceptionally(failure);
        }
    }

    private boolean canCancel(Cancellation request, @Nullable AuctionListing listing) {
        return listing != null && listing.getStatus() == AuctionListing.ListingStatus.ACTIVE
            && (request.forced() || (request.actor().equals(listing.getSeller()) && listing.isActive()
                && (config.allowCancelWithBids() || listing.getHighestBidder() == null)));
    }

    private void commitCancellation(Cancellation request, AuctionListing listing, String material) {
        try {
            for (int attempt = 0; attempt < 2 && canCancel(request, listing); attempt++) {
                var settlement = cancellationSettlement(request, listing, material);
                if (repository.cancelAuction(settlement, request.forced() ? null : request.actor(),
                        request.forced() || config.allowCancelWithBids())) {
                    invalidateCancelledAuction(listing.getId());
                    request.result().complete(true);
                    return;
                }
                if (attempt == 0) listing = repository.getListing(request.listing());
            }
            request.result().complete(false);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 撤单提交未确认 #" + request.listing(), failure);
            request.result().completeExceptionally(failure);
        }
    }

    private AuctionSettlement cancellationSettlement(Cancellation request, AuctionListing listing, String material) {
        long id = listing.getId();
        long now = System.currentTimeMillis();
        String tradeId = AuctionPublication.cancellationId(id);
        String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', listing.getItemDisplayName()));
        String label = name.substring(0, Math.min(255, name.length()));
        String reason = request.forced() ? "auction_admin_cancel" : "auction_cancel";
        var receipt = new MarketOperation(tradeId, tradeId, request.actor(),
            request.forced() ? "AUCTION_ADMIN_CANCEL" : "AUCTION_CANCEL", "listing=" + id,
            "DONE", "", "0", listing.getItemData(), "撤单已提交，退款及物品等待交付", "", now, now, label, material);
        var history = new AuctionHistory(0, id, listing.getSeller(), null, listing.getItemData(),
            listing.getItemDisplayName(), 0, listing.getCurrency(), 0, request.forced() ? "ADMIN_CANCELLED" : "CANCELLED", now);
        var deliveries = new ArrayList<MarketOperation>();
        deliveries.add(settlementDelivery(receipt, listing.getSeller(), "ITEM", "", "0", listing.getItemData(), reason + "_return:" + id));
        if (hasWinningBid(listing)) {
            deliveries.add(settlementDelivery(receipt, listing.getHighestBidder(), "CURRENCY", listing.getCurrency(),
                BigDecimal.valueOf(listing.getCurrentBid()).toPlainString(), "", reason + "_refund"));
        }
        if (config.notifySettlement()) {
            var recipients = new java.util.LinkedHashSet<>(repository.auctionWatchers(id));
            if (listing.getHighestBidder() != null) recipients.add(listing.getHighestBidder());
            String notice = "&7拍卖 #" + id + " " + listing.getItemDisplayName() + " 已取消，有效押金按记录退还。";
            for (UUID recipient : recipients) {
                deliveries.add(settlementDelivery(receipt, recipient, "NOTICE", "", "0", notice, "notice-chat:cancel:" + id));
            }
        }
        var publication = !settlementRefreshAllowed() ? null : new AuctionPublication(tradeId, id, listing.getSeller(), null,
            listing.getItemDisplayName(), 0, listing.getCurrency(), 0, "", false, true, now);
        return new AuctionSettlement(listing, receipt, history, deliveries, publication);
    }

    private void invalidateCancelledAuction(long listingId) {
        try {
            if (redisCache.isAvailable()) redisCache.invalidateByPrefix("market:listings:");
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Auction] 撤单已提交，缓存失效失败 #" + listingId, failure);
        }
    }

    private record Cancellation(UUID actor, long listing, boolean forced, BooleanSupplier authorized,
                                CompletableFuture<Boolean> result) {}

    public BigDecimal minimumBid(AuctionListing listing) {
        return minimumBid(listing.getStartingBid(), listing.getCurrentBid(), listing.getHighestBidder() != null,
            currencyScale(listing.getCurrency()));
    }

    public BigDecimal minimumBid(double startingBid, double currentBid, boolean hasBid, int scale) {
        BigDecimal current=BigDecimal.valueOf(currentBid);
        BigDecimal minimum=!hasBid?BigDecimal.valueOf(startingBid)
            :current.add(current.multiply(BigDecimal.valueOf(config.minBidIncrementRatio()))
                .max(BigDecimal.valueOf(config.minBidIncrementAbsolute())));
        return minimum.setScale(scale,java.math.RoundingMode.CEILING);
    }

    public int currencyScale(String currency) {
        var definition=currencyManager.definition(currency);
        return definition==null?2:Math.max(0,Math.min(16,definition.scale()));
    }

    public long extendedExpiry(AuctionListing listing,long now) {
        if(!config.endProtectionEnabled() || listing.getExpiresAt()<=now
            || listing.getExpiresAt()-now>config.endWindowSeconds()*1000L)return listing.getExpiresAt();
        long cap=listing.getOriginalExpiresAt()+config.endMaxExtensionSeconds()*1000L;
        return Math.max(listing.getExpiresAt(),Math.min(cap,now+config.endExtendSeconds()*1000L));
    }

    public List<AuctionListing> getActiveListings(int page, int pageSize) {
        return repository.getActiveListings(page * pageSize, pageSize);
    }

    public List<AuctionListing> getListingsByCategory(String category, int page, int pageSize) {
        return repository.getActiveListingsByCategory(category, page * pageSize, pageSize);
    }

    public List<AuctionListing> searchListings(String keyword, int page, int pageSize) {
        return repository.searchListings(keyword, page * pageSize, pageSize);
    }

    public List<AuctionListing> getMyListings(UUID seller) {
        return repository.getListingsBySeller(seller);
    }

    public List<AuctionListing> getMyListings(UUID seller, int page, int pageSize) {
        return repository.getListingsBySeller(seller, page * pageSize, pageSize);
    }

    public int countActive() {
        return repository.countActiveListings();
    }

    public int countCategory(String category) {
        return repository.countActiveListingsByCategory(category);
    }

    public int countSearch(String keyword) {
        return repository.countSearchListings(keyword);
    }

    public int countMy(UUID seller) {
        return repository.countListingsBySeller(seller);
    }

    /**
     * 手动触发到期处理，返回处理的条目数。
     */
    public int triggerExpiredProcessing() {
        if (stopped || settlingExpired.get() || !expired.isEmpty() || !preparedExpired.isEmpty()
            || !scanningExpired.compareAndSet(false, true)) return 0;
        try {
            var batch = repository.getExpiredListings(EXPIRED_SCAN_LIMIT);
            if (stopped) return 0;
            expired.addAll(batch);
            if (stopped) expired.clear();
            return batch.size();
        } finally {
            scanningExpired.set(false);
        }
    }

    // ─── 定期处理 ───────────────────────────────────────────

    private void processExpired() {
        try {
            triggerExpiredProcessing();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Auction] 到期处理异常", e);
        }
    }

    private void settleExpired() {
        if (stopped) return;
        long start = System.nanoTime();
        for (int processed = 0; processed < MAX_SETTLEMENTS_PER_TICK; processed++) {
            var listing = expired.poll();
            if (listing == null) break;
            prepareExpiry(listing);
            if (System.nanoTime() - start >= SETTLEMENT_BUDGET_NANOS) break;
        }
        if (!preparedExpired.isEmpty() && settlingExpired.compareAndSet(false, true)) {
            work.async(this::settlePreparedExpiries);
        }
    }

    private void prepareExpiry(AuctionListing listing) {
        try {
            Player seller = config.taxDiscount().isEmpty() || !hasWinningBid(listing)
                ? null : Bukkit.getPlayer(listing.getSeller());
            double taxRate = seller == null ? config.transactionTaxRate() : getEffectiveTaxRateOnline(seller);
            preparedExpired.add(new PreparedExpiry(listing, listingMaterial(listing), taxRate,
                seller == null && hasWinningBid(listing) && !config.taxDiscount().isEmpty()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "[Auction] 到期准备失败 #" + listing.getId(), exception);
        }
    }

    private record PreparedExpiry(AuctionListing listing, String material, double taxRate, boolean lookupOfflineTax) { }

    private void settlePreparedExpiries() {
        boolean changed = false;
        Map<UUID, Double> offlineTax = new HashMap<>();
        try {
            for (PreparedExpiry prepared; !stopped && (prepared = preparedExpired.poll()) != null; ) {
                try {
                    changed |= processExpiredListingWork(prepared, offlineTax);
                } catch (RuntimeException exception) {
                    logger.log(Level.SEVERE, "[Auction] 到期处理失败 #" + prepared.listing().getId(), exception);
                }
            }
            if (changed && !stopped && redisCache.isAvailable()) redisCache.invalidateByPrefix("market:listings:");
        } finally {
            settlingExpired.set(false);
        }
    }

    private static boolean hasWinningBid(AuctionListing listing) {
        return listing.getHighestBidder() != null && listing.getCurrentBid() > 0;
    }

    private boolean processExpiredListingWork(PreparedExpiry prepared, Map<UUID, Double> offlineTax) {
        var listing = prepared.listing();
        long id = listing.getId();
        long now = System.currentTimeMillis();
        String tradeId = "auction-expiry:" + id;
        boolean sold = hasWinningBid(listing);
        double price = sold ? listing.getCurrentBid() : 0;
        double rate = prepared.lookupOfflineTax()
            ? offlineTax.computeIfAbsent(listing.getSeller(), this::getEffectiveTaxRateOffline) : prepared.taxRate();
        if (stopped) return false;
        double tax = price * rate;
        double income = price - tax;
        var history = new AuctionHistory(0, id, listing.getSeller(), sold ? listing.getHighestBidder() : null,
            listing.getItemData(), listing.getItemDisplayName(), price, listing.getCurrency(), tax,
            sold ? "BID_WIN" : "EXPIRED", now);
        String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', listing.getItemDisplayName()));
        String label = name.substring(0, Math.min(255, name.length()));
        var receipt = new MarketOperation(tradeId, tradeId, listing.getSeller(), "AUCTION_SETTLE", "listing=" + id,
            "DONE", listing.getCurrency(), BigDecimal.valueOf(price).toPlainString(), listing.getItemData(),
            sold ? "拍卖成交已结算" : "拍卖流拍已结算", "", now, now, label, prepared.material());
        var deliveries = new ArrayList<MarketOperation>();
        deliveries.add(settlementDelivery(receipt, sold ? listing.getHighestBidder() : listing.getSeller(),
            "ITEM", "", "0", listing.getItemData(), (sold ? "auction_bidwin_item:" : "auction_expired_return:") + id));
        if (income > 0) {
            deliveries.add(settlementDelivery(receipt, listing.getSeller(), "CURRENCY", listing.getCurrency(),
                BigDecimal.valueOf(income).toPlainString(), "", "auction_bidwin_income"));
        }
        if (config.notifySettlement()) addExpiryNotices(listing, receipt, deliveries, sold, income);
        boolean eventPending = sold && eventPublisher != null;
        String bidderName = "未知";
        if (eventPending) {
            try { bidderName = repository.latestAuctionBidderName(id, listing.getHighestBidder()); }
            catch (RuntimeException exception) { logger.log(Level.WARNING, "[Auction] 成交事件买家名称读取失败 #" + id, exception); }
        }
        boolean refreshPending = settlementRefreshAllowed();
        var publication = eventPending || refreshPending ? new AuctionPublication(tradeId, id, listing.getSeller(),
            sold ? listing.getHighestBidder() : null, listing.getItemDisplayName(), price, listing.getCurrency(),
            tax, bidderName, eventPending, refreshPending, now) : null;
        return repository.settleExpiredAuction(new AuctionSettlement(listing, receipt, history, deliveries, publication));
    }

    private void addExpiryNotices(AuctionListing listing, MarketOperation receipt, List<MarketOperation> deliveries,
                                  boolean sold, double income) {
        long id = listing.getId();
        String display = listing.getItemDisplayName();
        String formatted = sold ? currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(listing.getCurrentBid())) : "";
        if (sold) {
            String sellerNotice = messages.auctionSold().replace("{item}", display).replace("{amount}",
                currencyManager.format(listing.getCurrency(), BigDecimal.valueOf(income))) + " &7收入已进入交付队列。";
            deliveries.add(settlementDelivery(receipt, listing.getSeller(), "NOTICE", "", "0", sellerNotice, "notice-chat:sold:" + id));
            deliveries.add(settlementDelivery(receipt, listing.getHighestBidder(), "NOTICE", "", "0",
                "&a你已拍得 #" + id + " " + display + "，成交价 " + formatted + "，物品已进入交付队列。", "notice-chat:won:" + id));
        } else {
            deliveries.add(settlementDelivery(receipt, listing.getSeller(), "NOTICE", "", "0",
                messages.auctionExpired().replace("{item}", display), "notice-chat:expired:" + id));
        }
        String text = "&7关注的拍卖 #" + id + " " + display + (sold ? " 已成交，成交价 " + formatted : " 已流拍");
        for (UUID watcher : repository.auctionWatchers(id)) {
            if (watcher.equals(listing.getSeller()) || watcher.equals(listing.getHighestBidder())) continue;
            deliveries.add(settlementDelivery(receipt, watcher, "NOTICE", "", "0", text, "notice-chat:watch-ended:" + id));
        }
    }

    private static MarketOperation settlementDelivery(MarketOperation receipt, UUID player, String kind,
                                                      String currency, String amount, String payload, String reference) {
        return new MarketOperation(UUID.randomUUID().toString(), receipt.id(), player, kind, reference, "READY", currency, amount,
            payload, "等待交付", "", receipt.createdAt(), receipt.updatedAt(), receipt.itemName(), receipt.material());
    }

    private void publishSettlements() {
        if (stopped || !publishingExpired.compareAndSet(false, true)) return;
        String lease = UUID.randomUUID().toString();
        try {
            var claimed = repository.claimAuctionPublications(lease, System.currentTimeMillis(), 8, settlementRefreshAllowed());
            if (!claimed.isEmpty() && !stopped && redisCache.isAvailable()) redisCache.invalidateByPrefix("market:listings:");
            for (var notice : claimed) {
                if (stopped) break;
                boolean event = !notice.eventPending();
                boolean refresh = !notice.refreshPending();
                AuctionEvent observed = null;
                var delivery = settlementRefresh;
                if (!refresh && delivery != null && delivery.authorized().getAsBoolean()) {
                    java.util.concurrent.CompletableFuture<Boolean> submitted = null;
                    try {
                        submitted = delivery.publish().apply(notice.refreshPayload()).toCompletableFuture();
                        refresh = Boolean.TRUE.equals(submitted.get(1, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        if (submitted != null) submitted.cancel(false);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception exception) {
                        if (submitted != null) submitted.cancel(false);
                        logger.fine("[Auction] 跨服刷新稍后重试 #" + notice.listingId());
                    }
                }
                if (!event && eventPublisher != null && !stopped) {
                    var sent = new CompletableFuture<AuctionEvent>();
                    work.main(() -> {
                        if (sent.isCancelled() || stopped) { sent.complete(null); return; }
                        try {
                            publishAuctionPurchased(notice).whenComplete((accepted, failure) -> {
                                if (failure == null) sent.complete(accepted);
                                else sent.completeExceptionally(failure);
                            });
                        } catch (RuntimeException | LinkageError exception) { sent.completeExceptionally(exception); }
                    });
                    try {
                        observed = sent.get(1, TimeUnit.SECONDS);
                        event = observed != null;
                    }
                    catch (InterruptedException exception) { sent.cancel(false); Thread.currentThread().interrupt(); break; }
                    catch (Exception exception) { sent.cancel(false); logger.fine("[Auction] 成交事件稍后重试 #" + notice.listingId()); }
                }
                if (repository.finishAuctionPublication(notice.id(), lease, event, refresh, System.currentTimeMillis() + 5_000)
                        && observed != null && eventObserver != null) {
                    var notification = observed;
                    work.main(() -> notifyAuctionObservers(notification));
                }
            }
        } catch (RuntimeException exception) {
            if (!stopped) logger.log(Level.WARNING, "[Auction] 恢复拍卖通知失败", exception);
        } finally {
            publishingExpired.set(false);
        }
    }

    private void notifyAuctionObservers(AuctionEvent event) {
        var observer = eventObserver;
        if (stopped || observer == null) return;
        try { observer.accept(event.buyer(), event.payload()); }
        catch (RuntimeException | LinkageError failure) {
            logger.warning("拍卖实时通知失败: " + failure.getMessage());
        }
    }

    private CompletionStage<AuctionEvent> publishAuctionPurchased(AuctionPublication notice) {
        AuctionEventPublisher publisher = eventPublisher;
        if (publisher == null || notice.buyer() == null) return CompletableFuture.completedFuture(null);
        Player buyer = Bukkit.getPlayer(notice.buyer());
        String currency = notice.currency();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("event_id", notice.id());
        if (notice.settledAt() > 0L) {
            payload.put("business_date", java.time.Instant.ofEpochMilli(notice.settledAt())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString());
        }
        payload.put("listing_id", String.valueOf(notice.listingId()));
        payload.put("item", notice.item());
        payload.put("price", String.valueOf(notice.price()));
        payload.put("formatted_price", currencyManager.format(currency, BigDecimal.valueOf(notice.price())));
        payload.put("currency", currency);
        payload.put("tax", String.valueOf(notice.tax()));
        payload.put("seller_uuid", notice.seller().toString());
        payload.put("buyer_uuid", notice.buyer().toString());
        payload.put("buyer", buyer == null ? notice.buyerName() : buyer.getName());
        var event = new AuctionEvent(buyer, Map.copyOf(payload));
        return publisher.publish(EventBusCapability.MARKET_AUCTION_PURCHASED, buyer, event.payload())
            .thenApply(accepted -> Boolean.TRUE.equals(accepted) ? event : null);
    }

    private double getEffectiveTaxRateOnline(Player player) {
        return Math.min(config.transactionTaxRate(), config.taxDiscount().entrySet().stream()
            .filter(entry -> player.hasPermission(entry.getKey()))
            .mapToDouble(entry -> Math.max(0, entry.getValue())).min().orElse(config.transactionTaxRate()));
    }

    private double getEffectiveTaxRateOffline(UUID seller) {
        var resolver = offlineAuctionTax;
        if (resolver == null) return config.transactionTaxRate();
        try {
            return resolver.rate(seller, config.taxDiscount(), config.transactionTaxRate());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("离线税率查询已中断", exception);
        } catch (Exception | LinkageError exception) {
            logger.log(Level.WARNING, "[Auction] 离线税率查询失败，使用默认税率 seller=" + seller, exception);
            return config.transactionTaxRate();
        }
    }

    public boolean isBlacklisted(ItemStack item) {
        if (item == null) return true;
        if (itemMatcher == null) return false;
        return itemMatcher.matches(config.blacklist(), item);
    }

    public String classifyItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return defaultCategoryId();
        }
        List<java.util.Map.Entry<String, xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.CategoryDefinition>> categories =
            new ArrayList<>(config.categories().entrySet());
        categories.sort(Comparator.comparingInt(entry -> entry.getValue().priority()));
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (var entry : categories) {
            var category = entry.getValue();
            if (category.isDefault() || category.nbtPath().isBlank() || category.nbtValues().isEmpty()) continue;
            String value = readConfiguredValue(pdc, category.nbtPath());
            if (value != null && category.nbtValues().stream().anyMatch(value::equalsIgnoreCase)) {
                return entry.getKey();
            }
        }
        return defaultCategoryId();
    }

    private String defaultCategoryId() {
        return config.categories().entrySet().stream()
            .filter(entry -> entry.getValue().isDefault())
            .min(Comparator.comparingInt(entry -> entry.getValue().priority()))
            .map(java.util.Map.Entry::getKey)
            .orElse("other");
    }

    private String readConfiguredValue(PersistentDataContainer pdc, String path) {
        if (!path.regionMatches(true, 0, "pdc:", 0, 4)) return null;
        String keyText = path.substring(4);
        int separator = keyText.indexOf(58);
        if (separator <= 0 || separator == keyText.length() - 1) return null;
        try {
            NamespacedKey key = new NamespacedKey(keyText.substring(0, separator), keyText.substring(separator + 1));
            String stringValue = pdc.get(key, PersistentDataType.STRING);
            if (stringValue != null) return stringValue;
            Integer intValue = pdc.get(key, PersistentDataType.INTEGER);
            if (intValue != null) return String.valueOf(intValue);
            Long longValue = pdc.get(key, PersistentDataType.LONG);
            if (longValue != null) return String.valueOf(longValue);
            Byte byteValue = pdc.get(key, PersistentDataType.BYTE);
            return byteValue != null ? String.valueOf(byteValue) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String getItemDisplayName(ItemStack item) {
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(item);
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    /**
     * 生成 displayName 的拼音和首字母搜索 token。
     * 多音字遍历所有读音，同一字的多读音连续拼接；字之间用空格分隔。
     * 返回 [pinyin, initials]，如 "钻石剑" → ["zuan shi jian", "z s j"]。
     */

    private void publishCrossServer(String message) {
        if (crossServerPublisher != null && message != null && !message.isBlank()) {
            crossServerPublisher.accept(message);
        }
    }

    // ─── 结果类 ─────────────────────────────────────────────


    public record BidResult(boolean success, @Nullable String error, double amount, boolean extended) {
        public static BidResult success(double amount, boolean extended) { return new BidResult(true, null, amount, extended); }
        public static BidResult fail(String error) { return new BidResult(false, error, 0, false); }
    }
}
