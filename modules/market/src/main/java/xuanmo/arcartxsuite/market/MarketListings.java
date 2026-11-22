package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;
import xuanmo.arcartxsuite.market.storage.AuctionListingDraft;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Kind;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Save;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.PlayerShopListingDraft;

/** Stages a listing; the source node publishes it only after its inventory receipt is saved. */
final class MarketListings implements AutoCloseable {
    private static final int MAX_STAGING = 32;
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();
    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private final MarketWork work;
    private final MarketRepository repository;
    private final MarketLedger ledger;
    private final CurrencyBridgeAPI payments;
    private final AuctionItemSerializer serializer;
    private final MarketDebits debits;
    private final Logger logger;
    private final MarketListingSaves saves;
    private final BiConsumer<Save, Boolean> completed;
    private final Map<String, String[]> pinyinCache = new java.util.LinkedHashMap<>();
    private final Map<UUID, CompletableFuture<Result>> staging = new ConcurrentHashMap<>();
    private final Set<Removal> awaitingRemoval = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<Boolean>> waiters = new ConcurrentHashMap<>();
    private final Semaphore publicationSlots = new Semaphore(128);
    private volatile boolean closed;

    MarketListings(MarketWork work, MarketRepository repository, MarketLedger ledger, CurrencyBridgeAPI payments,
                   AuctionItemSerializer serializer, MarketDebits debits, Logger logger, Path playerData,
                   String sourceNode, BiConsumer<Save, Boolean> completed) {
        this.work = work;
        this.repository = repository;
        this.ledger = ledger;
        this.payments = payments;
        this.serializer = serializer;
        this.debits = debits;
        this.logger = logger;
        this.completed = completed;
        saves = new MarketListingSaves(work, repository, logger, playerData, sourceNode, this::listingCommitted);
        saves.start();
    }

    record Terms(Kind kind, double price, String currency, String note, long durationSeconds, String name,
                 String category, double fee, String feeCurrency, int maximum) {}
    record Result(boolean staged, String error) {
        static Result accepted() { return new Result(true, ""); }
        static Result refused(String error) { return new Result(false, error); }
    }
    record Submission(CompletableFuture<Result> staged, @Nullable CompletableFuture<Boolean> published) {
        static Submission refused(boolean awaitPublication, String error) {
            return new Submission(CompletableFuture.completedFuture(Result.refused(error)),
                awaitPublication ? CompletableFuture.completedFuture(false) : null);
        }
    }

    Submission submit(Player seller, int slot, ItemStack requested, Terms terms,
                      BooleanSupplier authorized, boolean awaitPublication) {
        UUID sellerId = seller.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(sellerId);
        if (closed || work.isStopping() || !session.admitted() || staging.size() >= MAX_STAGING) {
            return Submission.refused(awaitPublication, "上架暂不可用，请稍后重试");
        }
        if (!Double.isFinite(terms.fee()) || terms.fee() < 0 || terms.durationSeconds() <= 0) {
            return Submission.refused(awaitPublication, "上架费或时长配置无效");
        }
        ItemStack held = seller.getInventory().getItem(slot);
        if (held == null || held.getType().isAir() || requested.getAmount() < 1
                || !held.isSimilar(requested) || held.getAmount() < requested.getAmount()) {
            return Submission.refused(awaitPublication, "上架物品与指定槽位不匹配");
        }
        var staged = new CompletableFuture<Result>();
        if (staging.putIfAbsent(sellerId, staged) != null) {
            return Submission.refused(awaitPublication, "已有上架请求正在处理");
        }
        if (awaitPublication && !publicationSlots.tryAcquire()) {
            staging.remove(sellerId, staged);
            return Submission.refused(true, "等待发布的上架请求较多，请稍后重试");
        }
        var published = awaitPublication ? new CompletableFuture<Boolean>() : null;
        if (published != null) published.whenComplete((result, failure) -> publicationSlots.release());
        staged.whenComplete((result, failure) -> {
            staging.remove(sellerId, staged);
            if (published == null) return;
            if (failure != null) published.completeExceptionally(failure);
            else if (!result.staged()) published.complete(false);
        });
        try {
            String data = serializer.serialize(requested.clone());
            if (data == null || data.isBlank()) {
                staged.complete(Result.refused("物品无法暂存"));
            } else {
                var fee = BigDecimal.valueOf(terms.fee());
                var bridge = fee.signum() == 0 ? null : payments.bridge(terms.feeCurrency());
                if (fee.signum() > 0 && (bridge == null || !bridge.available())) {
                    staged.complete(Result.refused("上架费货币不可用"));
                } else if (bridge != null && bridge.debitRequiresReconciliation()) {
                    staged.complete(Result.refused("上架费货币无法确认扣款"));
                } else {
                    Supplier<CurrencyTransactionResult> background = bridge == null ? CurrencyTransactionResult::ok
                        : bridge.prepareBackgroundDebit(seller, fee);
                    Supplier<CurrencyTransactionResult> mainDebit = bridge == null ? CurrencyTransactionResult::ok
                        : () -> bridge.withdraw(seller, fee);
                    var request = new Request(seller, session, slot, held.clone(), requested.getAmount(), seller.getName(),
                        authorized, System.nanoTime() + TimeUnit.SECONDS.toNanos(15), staged, published);
                    var item = new Snapshot(data, requested.getType().name());
                    work.async(() -> reserve(request, terms, item, fee, background, mainDebit));
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Market 上架准备失败 " + sellerId, failure);
            staged.complete(Result.refused("无法准备上架，本次未扣除物品"));
        }
        return new Submission(staged.copy(), published == null ? null : published.copy());
    }

    private void reserve(Request request, Terms terms, Snapshot item, BigDecimal fee,
                         Supplier<CurrencyTransactionResult> background, Supplier<CurrencyTransactionResult> mainDebit) {
        try {
            if (!validSession(request)) {
                request.staged().complete(Result.refused("上架请求已失效，物品未扣除"));
                return;
            }
            MarketListingDraft draft = draft(request, terms, item, fee);
            var reservation = repository.reserveListingDraft(draft, terms.maximum());
            if (reservation != MarketListingDraft.Reservation.RESERVED) {
                request.staged().complete(Result.refused(reservation == MarketListingDraft.Reservation.LIMIT_REACHED
                    ? "在售商品已达到上架上限" : "已有同类商品等待保存或核对"));
                return;
            }
            var removal = new Removal(request, draft);
            debits.enqueue(new MarketDebits.Job(draft.id(), () -> currentSlot(request), () -> validSession(request),
                background, mainDebit, payment -> finishFee(removal, payment)));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Market 上架暂存未确认 " + request.session().playerId(), failure);
            request.staged().complete(Result.refused("上架暂存未确认，物品未扣除"));
        }
    }

    private MarketListingDraft draft(Request request, Terms terms, Snapshot item, BigDecimal fee) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long duration = Math.multiplyExact(terms.durationSeconds(), 1000L);
        long expires = Math.addExact(now, duration);
        UUID seller = request.session().playerId();
        String name = ChatColor.stripColor(terms.name());
        String[] tokens = pinyinTokens(name);
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', terms.name()));
        String label = plain.substring(0, Math.min(255, plain.length()));
        String feeCurrency = fee.signum() == 0 ? "" : terms.feeCurrency();
        if (terms.kind() == Kind.AUCTION) {
            var auction = new AuctionListing(0, seller, request.sellerName(), item.data(), terms.name(), tokens[0], tokens[1],
                terms.category(), 0, terms.price(), 0, null, terms.currency(), AuctionListing.ListingType.AUCTION,
                AuctionListing.ListingStatus.PREPARING, now, expires);
            auction.setMessage(terms.note());
            return new AuctionListingDraft(auction, duration, id, ledger.owner(), saves.node(), MarketListingSaves.SERVER_START,
                feeCurrency, fee, label, item.material());
        }
        var playerShop = new PlayerShopListing(0, seller, request.sellerName(), item.data(), terms.name(), tokens[0], tokens[1],
            terms.note(), terms.price(), terms.currency(), PlayerShopListing.ListingStatus.PREPARING, now, expires);
        return new PlayerShopListingDraft(playerShop, duration, id, ledger.owner(), saves.node(), MarketListingSaves.SERVER_START,
            feeCurrency, fee, label, item.material());
    }

    private boolean validSession(Request request) {
        if (closed || work.isStopping() || request.validUntil() - System.nanoTime() <= 0) return false;
        var now = AuthAccess.snapshot(request.session().playerId());
        return now.admitted() && now.generation() == request.session().generation()
            && java.util.Objects.equals(now.connectionId(), request.session().connectionId());
    }

    private boolean currentSlot(Request request) {
        return validSession(request) && request.authorized().getAsBoolean()
            && request.expectedSlot().equals(request.seller().getInventory().getItem(request.slot()));
    }

    private void finishFee(Removal removal, CurrencyTransactionResult payment) {
        var draft = removal.draft();
        var result = removal.request().staged();
        try {
            repository.recordListingFee(draft, payment);
        } catch (RuntimeException failure) {
            try { repository.recordListingFee(draft, CurrencyTransactionResult.unknown("")); }
            catch (RuntimeException unresolved) { failure.addSuppressed(unresolved); }
            logger.log(Level.WARNING, "Market 上架费回执未确认 " + draft.id(), failure);
            result.complete(Result.refused("上架费回执未确认，物品未扣除。流水 " + draft.id()));
            return;
        }
        if (payment.uncertain()) {
            result.complete(Result.refused("上架费结果不明，物品未扣除。流水 " + draft.id()));
        } else if (!payment.success()) {
            result.complete(Result.refused(payment.message() == null || payment.message().isBlank()
                ? "上架费扣款失败" : payment.message()));
        } else {
            awaitingRemoval.add(removal);
            if (closed || work.isStopping()) {
                if (awaitingRemoval.remove(removal)) abort(removal);
            } else {
                work.main(() -> removeItem(removal));
            }
        }
    }

    private void removeItem(Removal removal) {
        if (!awaitingRemoval.remove(removal)) return;
        var request = removal.request();
        boolean valid;
        try { valid = currentSlot(request); }
        catch (RuntimeException | LinkageError failure) {
            valid = false;
            logger.log(Level.WARNING, "Market 无法复核上架槽位 " + removal.draft().id(), failure);
        }
        if (!valid) {
            work.async(() -> abort(removal));
            return;
        }

        var draft = removal.draft();
        var player = request.seller();
        var receipt = MarketListingSaves.receipt(draft.kind());
        var publication = request.published();
        try {
            ItemStack remainder = null;
            int left = request.expectedSlot().getAmount() - request.amount();
            if (left > 0) {
                remainder = request.expectedSlot().clone();
                remainder.setAmount(left);
            }
            if (publication != null) waiters.put(draft.id(), publication);
            player.getPersistentDataContainer().set(receipt, PersistentDataType.STRING, draft.id());
            player.getInventory().setItem(request.slot(), remainder);
            saves.wake();
            request.staged().complete(Result.accepted());
        } catch (RuntimeException | LinkageError failure) {
            try { player.getPersistentDataContainer().remove(receipt); }
            catch (RuntimeException | LinkageError cleanup) { failure.addSuppressed(cleanup); }
            waiters.remove(draft.id());
            if (publication != null) publication.completeExceptionally(failure);
            logger.log(Level.WARNING, "Market 物品扣除结果未确认 " + draft.id(), failure);
            request.staged().complete(Result.refused("物品扣除结果未确认，请核对背包。流水 " + draft.id()));
        }
    }

    private void abort(Removal removal) {
        var draft = removal.draft();
        try {
            boolean cancelled = repository.abortListingDraft(draft.save(), draft.sourceNode(),
                "页面、槽位或会话已变化，物品未扣除");
            removal.request().staged().complete(Result.refused(cancelled
                ? "上架已取消，物品未扣除。已付上架费进入退款队列"
                : "撤销状态未确认，请核对流水 " + draft.id()));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Market 上架撤销未确认 " + draft.id(), failure);
            removal.request().staged().complete(Result.refused("撤销状态未确认，请核对流水 " + draft.id()));
        }
    }

    private void listingCommitted(Save save, boolean published) {
        var waiter = waiters.remove(save.id());
        if (waiter != null) waiter.complete(published);
        completed.accept(save, published);
    }

    @Override
    public void close() {
        closed = true;
        saves.close();
        for (var removal : List.copyOf(awaitingRemoval)) {
            if (awaitingRemoval.remove(removal)) work.async(() -> abort(removal));
        }
        for (var waiter : waiters.values()) {
            waiter.completeExceptionally(new IllegalStateException("Market 已停用，暂存挂单由原子服恢复"));
        }
        waiters.clear();
    }

    private record Snapshot(String data, String material) {}
    private record Request(Player seller, AuthSnapshot session, int slot, ItemStack expectedSlot, int amount,
                           String sellerName, BooleanSupplier authorized, long validUntil,
                           CompletableFuture<Result> staged, @Nullable CompletableFuture<Boolean> published) {}
    private record Removal(Request request, MarketListingDraft draft) {}

    private String[] pinyinTokens(String name) {
        synchronized (pinyinCache) {
            var cached = pinyinCache.get(name);
            if (cached != null) return cached;
        }
        var computed = computePinyinTokens(name);
        synchronized (pinyinCache) {
            if (pinyinCache.size() >= 4096) pinyinCache.remove(pinyinCache.keySet().iterator().next());
            return pinyinCache.computeIfAbsent(name, ignored -> computed);
        }
    }

    private static String[] computePinyinTokens(String stripped) {
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) continue;
            String[] values;
            try {
                values = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                values = null;
            }
            if (values != null && values.length > 0) {
                StringBuilder charPinyin = new StringBuilder();
                StringBuilder charInitials = new StringBuilder();
                for (String value : values) {
                    String candidate = value.replaceAll("[^a-z0-9]", "");
                    if (!candidate.isBlank()) {
                        charPinyin.append(candidate);
                        charInitials.append(candidate.charAt(0));
                    }
                }
                if (charPinyin.length() > 0) {
                    if (pinyin.length() > 0) pinyin.append(' ');
                    if (initials.length() > 0) initials.append(' ');
                    pinyin.append(charPinyin);
                    initials.append(charInitials);
                    continue;
                }
            }
            if (Character.isLetterOrDigit(character)) {
                if (pinyin.length() > 0) pinyin.append(' ');
                if (initials.length() > 0) initials.append(' ');
                pinyin.append(Character.toLowerCase(character));
                initials.append(Character.toLowerCase(character));
            }
        }
        return new String[]{pinyin.toString(), initials.toString()};
    }
}
