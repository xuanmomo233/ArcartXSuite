package xuanmo.arcartxsuite.market.storage;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSettlementTest {
    @TempDir Path directory;
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();
    private JdbcMarketRepository repository;

    @BeforeEach
    void initialize() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("auction.db"));
        repository = new JdbcMarketRepository(source, StorageDescriptor.sqlite("auction.db"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
    }

    @Test
    void auctionDraftOnlyPublishesWithTheMatchingSourceAndStartsItsFullDuration() {
        long now = System.currentTimeMillis();
        var previous = listing(false, now - 1_000);
        assertFalse(previous.isActive());
        var auction = new AuctionListing(0, seller, "Seller", "snapshot", "Item", "", "", "other", 0,
            20, 0, null, "money", AuctionListing.ListingType.AUCTION, AuctionListing.ListingStatus.PREPARING, now, now + 60_000);
        var draft = new AuctionListingDraft(auction, 60_000, UUID.randomUUID().toString(), "worker", "source",
            100, "", BigDecimal.ZERO, "Item", "STONE");
        assertEquals(MarketListingDraft.Reservation.RESERVED, repository.reserveListingDraft(draft, 1));
        repository.recordListingFee(draft, CurrencyTransactionResult.ok());
        assertTrue(repository.getActiveListings(0, 20).isEmpty());
        var held = draft.save();
        var wrongKind = new MarketListingDraft.Save(MarketListingDraft.Kind.PLAYER_SHOP, held.id(), held.seller(),
            held.listingId(), held.durationMillis(), held.serverStart());
        assertFalse(repository.publishListingDraft(wrongKind, "source"));
        assertFalse(repository.publishListingDraft(held, "other"));
        assertFalse(repository.isListingDraftPublished(held, "source"));
        assertTrue(repository.publishListingDraft(held, "source"));
        assertFalse(repository.publishListingDraft(held, "source"));
        assertTrue(repository.isListingDraftPublished(held, "source"));
        assertFalse(repository.isListingDraftPublished(wrongKind, "source"));
        assertFalse(repository.isListingDraftPublished(held, "other"));
        var active = repository.getActiveListings(0, 20).get(0);
        assertEquals(active.getCreatedAt() + 60_000, active.getExpiresAt());
        assertEquals(active.getExpiresAt(), active.getOriginalExpiresAt());
    }

    @Test
    void playerShopDraftCountsOccupiedSlotsAndRequiresSourceConfirmationToPublish() {
        long now = System.currentTimeMillis();
        playerShopListing(now - 1_000);
        var occupied = playerShopListing(now + 60_000);
        var draft = listingDraft(now);
        assertEquals(MarketListingDraft.Reservation.LIMIT_REACHED, repository.reserveListingDraft(draft, 1));
        assertTrue(repository.terminatePlayerShopListing(playerShopTermination(occupied, false)));
        assertEquals(MarketListingDraft.Reservation.RESERVED, repository.reserveListingDraft(draft, 1));
        assertEquals(MarketListingDraft.Reservation.ALREADY_PENDING, repository.reserveListingDraft(listingDraft(now), 2));
        assertEquals(0, repository.countActivePlayerShopListings());
        repository.recordListingFee(draft, CurrencyTransactionResult.ok());
        assertEquals(PlayerShopListing.ListingStatus.PREPARING, repository.getPlayerShopListing(draft.listing().getId()).getStatus());
        assertEquals(List.of(draft.save()), repository.listingSaves("source", "", 32));
        assertFalse(repository.publishListingDraft(draft.save(), "other"));
        assertTrue(repository.publishListingDraft(draft.save(), "source"));
        assertFalse(repository.publishListingDraft(draft.save(), "source"));
        assertEquals(1, repository.countActivePlayerShopListings());
        assertTrue(repository.playerShopProfile(seller).published());
    }

    @Test
    void releasingAnUnappliedListingDraftQueuesOnlyTheConfirmedFeeRefund() {
        var draft = listingDraft(System.currentTimeMillis());
        assertEquals(MarketListingDraft.Reservation.RESERVED, repository.reserveListingDraft(draft, 2));
        repository.recordListingFee(draft, CurrencyTransactionResult.ok());
        assertTrue(repository.abortListingDraft(draft.save(), "source", "未应用库存扣除"));
        assertFalse(repository.abortListingDraft(draft.save(), "source", "重复撤销"));
        assertFalse(repository.publishListingDraft(draft.save(), "source"));
        assertEquals(PlayerShopListing.ListingStatus.CANCELLED, repository.getPlayerShopListing(draft.listing().getId()).getStatus());
        var pending = repository.pendingDeliveries(seller, "node", 10);
        assertEquals(1, pending.size());
        assertEquals("CURRENCY", pending.get(0).kind());
        assertEquals("2", pending.get(0).amount());
    }

    private PlayerShopListingDraft listingDraft(long now) {
        var listing = new PlayerShopListing(0, seller, "Seller", "snapshot", "Test item", "", "", "", 10, "coin",
            PlayerShopListing.ListingStatus.PREPARING, now, now + 60_000);
        return new PlayerShopListingDraft(listing, 60_000, UUID.randomUUID().toString(), "worker", "source", 100,
            "coin", BigDecimal.valueOf(2), "Test item", "STONE");
    }

    @Test
    void playerShopReservationPreventsAnotherChargeAndCommitsBothDeliveriesOnce() throws Exception {
        var listing = playerShopListing(System.currentTimeMillis() + 60_000);
        var first = playerShopOrder(listing, buyer);
        var second = playerShopOrder(listing, UUID.randomUUID());
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        PlayerShopOrder paid;
        try {
            var a = workers.submit(() -> { start.await(); return repository.reservePlayerShopPurchase(first); });
            var b = workers.submit(() -> { start.await(); return repository.reservePlayerShopPurchase(second); });
            start.countDown();
            boolean won = a.get(8, TimeUnit.SECONDS);
            assertNotEquals(won, b.get(8, TimeUnit.SECONDS));
            paid = won ? first : second;
        } finally {
            workers.shutdownNow();
        }
        assertEquals(PlayerShopListing.ListingStatus.RESERVED, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertTrue(repository.pendingDeliveries(paid.order().player(), "node", 10).isEmpty());
        assertTrue(repository.pendingDeliveries(seller, "node", 10).isEmpty());
        assertFalse(repository.terminatePlayerShopListing(playerShopTermination(listing, false)));
        repository.finishPlayerShopPurchase(paid, CurrencyTransactionResult.ok());
        assertThrows(IllegalStateException.class, () -> repository.finishPlayerShopPurchase(paid, CurrencyTransactionResult.ok()));
        assertEquals(PlayerShopListing.ListingStatus.SOLD, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertEquals(1, repository.getPlayerShopHistoryByPlayer(seller, 0, 10).size());
        var item = repository.pendingDeliveries(paid.order().player(), "node", 10);
        assertEquals(1, item.size());
        assertEquals(listing.getItemData(), item.get(0).itemData());
        var income = repository.pendingDeliveries(seller, "node", 10);
        assertEquals(1, income.size());
        assertEquals("9", income.get(0).amount());
    }

    @Test
    void failedPlayerShopHistoryRollsBackCheckoutAndReviewCanQueueOnlyOneRefund() throws Exception {
        var listing = playerShopListing(System.currentTimeMillis() + 60_000);
        var order = playerShopOrder(listing, buyer);
        assertTrue(repository.reservePlayerShopPurchase(order));
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("auction.db"));
             var statement = conn.createStatement()) {
            statement.execute("""
                CREATE TRIGGER reject_shop_history BEFORE INSERT ON %splayer_shop_history
                BEGIN SELECT RAISE(ABORT, 'test'); END
                """.formatted(repository.tablePrefix()));
        }
        assertThrows(IllegalStateException.class, () -> repository.finishPlayerShopPurchase(order, CurrencyTransactionResult.ok()));
        assertEquals(PlayerShopListing.ListingStatus.RESERVED, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertTrue(repository.pendingDeliveries(buyer, "node", 10).isEmpty());
        assertTrue(repository.pendingDeliveries(seller, "node", 10).isEmpty());
        repository.finishPlayerShopPurchase(order, CurrencyTransactionResult.unknown(""));
        assertFalse(repository.resolveOperation(order.order().id(), "cancel", "generic review"));
        assertEquals("已取消订单，退款已入队", repository.reviewPlayerShopPurchase(order.order().id(), "refund-paid", "核对已扣款"));
        assertTrue(repository.reviewPlayerShopPurchase(order.order().id(), "refund-paid", "重复核对").contains("无需重复核对"));
        assertEquals(PlayerShopListing.ListingStatus.ACTIVE, repository.getPlayerShopListing(listing.getId()).getStatus());
        var refunds = repository.pendingDeliveries(buyer, "node", 10);
        assertEquals(1, refunds.size());
        assertEquals("pshop-refund", refunds.get(0).reference());
        assertEquals("10", refunds.get(0).amount());
        assertTrue(repository.pendingDeliveries(seller, "node", 10).isEmpty());
    }

    private PlayerShopOrder playerShopOrder(PlayerShopListing listing, UUID customer) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        var tax = BigDecimal.ONE;
        var order = new MarketOperation(id, id, customer, "PSHOP_BUY", PlayerShopOrder.reference(listing.getId(), tax, 2),
            "RUNNING", listing.getCurrency(), "10", listing.getItemData(), "", "worker", now, now);
        var debit = new MarketOperation(id + ":debit", id, customer, "DEBIT", "currency",
            "RUNNING", listing.getCurrency(), "10", "", "", "worker", now, now);
        var item = new MarketOperation(id + ":item", id, customer, "ITEM", "pshop_purchase_item", "PREPARED", "", "0",
            listing.getItemData(), "", "worker", now, now);
        var income = new MarketOperation(id + ":income", id, listing.getSeller(), "CURRENCY", "pshop_sold_income",
            "PREPARED", listing.getCurrency(), "9", "", "", "worker", now, now);
        return new PlayerShopOrder(listing, order, debit, tax, List.of(item, income), false);
    }

    @Test
    void concurrentPlayerShopTerminationCreatesOneHistoryAndOneReturn() throws Exception {
        var listing = playerShopListing(System.currentTimeMillis() + 60_000);
        assertFalse(repository.terminatePlayerShopListing(playerShopTermination(listing, true)));
        var removal = playerShopTermination(listing, false);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> { start.await(); return repository.terminatePlayerShopListing(removal); });
            var b = workers.submit(() -> { start.await(); return repository.terminatePlayerShopListing(removal); });
            start.countDown();
            assertNotEquals(a.get(8, TimeUnit.SECONDS), b.get(8, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
        assertEquals(PlayerShopListing.ListingStatus.CANCELLED, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertEquals(1, repository.getPlayerShopHistoryByPlayer(seller, 0, 10).size());
        var deliveries = repository.pendingDeliveries(seller, "node", 10);
        assertEquals(1, deliveries.size());
        assertEquals(listing.getItemData(), deliveries.get(0).itemData());
        assertEquals(removal.receipt().id(), deliveries.get(0).tradeId());
    }

    @Test
    void failedPlayerShopReturnRollsBackExpiryAndCanBeRecoveredLater() {
        var listing = playerShopListing(System.currentTimeMillis() - 10);
        playerShopListing(System.currentTimeMillis() - 5);
        assertEquals(List.of(listing.getId()), repository.getExpiredPlayerShopListings(1)
            .stream().map(PlayerShopListing::getId).toList());
        var expiry = playerShopTermination(listing, true);
        var item = expiry.deliveries().get(0);
        var broken = new PlayerShopTermination(listing, true, expiry.receipt(), expiry.history(), List.of(item, item));
        assertThrows(IllegalStateException.class, () -> repository.terminatePlayerShopListing(broken));
        assertEquals(PlayerShopListing.ListingStatus.ACTIVE, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertTrue(repository.getPlayerShopHistoryByPlayer(seller, 0, 10).isEmpty());
        assertTrue(repository.pendingDeliveries(seller, "node", 10).isEmpty());
        assertTrue(repository.terminatePlayerShopListing(expiry));
        assertFalse(repository.terminatePlayerShopListing(expiry));
        assertEquals(PlayerShopListing.ListingStatus.EXPIRED, repository.getPlayerShopListing(listing.getId()).getStatus());
        assertEquals(1, repository.getPlayerShopHistoryByPlayer(seller, 0, 10).size());
        assertEquals(1, repository.pendingDeliveries(seller, "node", 10).size());
    }

    private PlayerShopListing playerShopListing(long expires) {
        var listing = new PlayerShopListing(0, seller, "Seller", "item", "Test item", "", "", "", 10, "coin",
            PlayerShopListing.ListingStatus.ACTIVE, expires - 60_000, expires);
        assertTrue(repository.insertPlayerShopListing(listing));
        return listing;
    }

    private PlayerShopTermination playerShopTermination(PlayerShopListing listing, boolean expired) {
        long now = System.currentTimeMillis();
        String id = "pshop-end:" + listing.getId();
        var receipt = new MarketOperation(id, id, seller, expired ? "PSHOP_EXPIRE" : "PSHOP_CANCEL", "listing=" + listing.getId(),
            "DONE", "", "0", listing.getItemData(), "", "", now, now);
        var history = new MarketRepository.PlayerShopHistory(0, listing.getId(), seller, null, listing.getItemData(),
            listing.getItemDisplayName(), 0, listing.getCurrency(), 0, expired ? "EXPIRED" : "CANCELLED", now);
        var returnItem = new MarketOperation(id + ":item", id, seller, "ITEM", "pshop_return",
            "READY", "", "0", listing.getItemData(), "", "", now, now);
        return new PlayerShopTermination(listing, expired, receipt, history, List.of(returnItem));
    }

    @Test
    void concurrentFavoritesRespectTheLimitAndRemovingStillWorksAtCapacity() throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        var first = listing(false, deadline);
        var second = listing(false, deadline);
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("auction.db"));
             var insert = conn.prepareStatement("INSERT INTO " + repository.tablePrefix() + "favorites (player,listing_id) VALUES (?,?)")) {
            conn.setAutoCommit(false);
            insert.setString(1, buyer.toString());
            for (int i = 0; i < 99; i++) {
                insert.setLong(2, 1000 + i);
                insert.addBatch();
            }
            insert.executeBatch();
            conn.commit();
        }
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> { start.await(); return repository.toggleAuctionFavorite(buyer, first.getId()); });
            var b = workers.submit(() -> { start.await(); return repository.toggleAuctionFavorite(buyer, second.getId()); });
            start.countDown();
            assertEquals(java.util.Set.of(MarketRepository.FavoriteChange.ADDED, MarketRepository.FavoriteChange.LIMIT_REACHED),
                java.util.Set.of(a.get(6, TimeUnit.SECONDS), b.get(6, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        var favorites = repository.getFavorites(buyer);
        assertEquals(100, favorites.size());
        boolean firstAdded = favorites.contains(first.getId());
        long added = firstAdded ? first.getId() : second.getId();
        long rejected = firstAdded ? second.getId() : first.getId();
        assertEquals(MarketRepository.FavoriteChange.REMOVED, repository.toggleAuctionFavorite(buyer, added));
        assertEquals(MarketRepository.FavoriteChange.MISSING, repository.toggleAuctionFavorite(buyer, Long.MAX_VALUE));
        assertEquals(MarketRepository.FavoriteChange.ADDED, repository.toggleAuctionFavorite(buyer, rejected));
        assertEquals(100, repository.getFavorites(buyer).size());
    }

    @Test
    void cancellingCommitsOneRefundReturnAndReplayableRefresh() {
        var listing = listing(true, System.currentTimeMillis() + 60_000);
        var settlement = cancellation(listing);
        assertFalse(repository.cancelAuction(settlement, buyer, true));
        assertFalse(repository.cancelAuction(settlement, seller, false));
        assertTrue(repository.cancelAuction(settlement, seller, true));
        assertFalse(repository.cancelAuction(settlement, null, true));
        assertEquals(AuctionListing.ListingStatus.CANCELLED, repository.getListing(listing.getId()).getStatus());
        assertEquals(1, repository.getHistoryByPlayer(seller, 0, 10).size());
        assertEquals(1, repository.pendingDeliveries(seller, "node", 10).size());
        var refunds = repository.pendingDeliveries(buyer, "node", 10);
        assertEquals(1, refunds.size());
        assertEquals("20", refunds.get(0).amount());
        assertEquals(1, repository.auctionSettlementRevision());
        var publication = repository.claimAuctionPublications("worker", 1, 8).get(0);
        assertEquals("LISTING_CANCELLED:" + listing.getId(), publication.refreshPayload());
        assertFalse(publication.eventPending());
        assertTrue(publication.refreshPending());
    }

    @Test
    void forcedCancellationRollsBackWithItsRefundAndCanThenBeRetried() {
        var listing = listing(true, System.currentTimeMillis() - 10);
        var settlement = cancellation(listing);
        var refund = settlement.deliveries().get(1);
        var broken = new AuctionSettlement(listing, settlement.receipt(), settlement.history(),
            List.of(settlement.deliveries().get(0), refund, refund), settlement.publication());
        assertFalse(repository.cancelAuction(settlement, seller, true));
        assertThrows(IllegalStateException.class, () -> repository.cancelAuction(broken, null, true));
        assertEquals(AuctionListing.ListingStatus.ACTIVE, repository.getListing(listing.getId()).getStatus());
        assertTrue(repository.getHistoryByPlayer(seller, 0, 10).isEmpty());
        assertTrue(repository.pendingDeliveries(seller, "node", 10).isEmpty());
        assertTrue(repository.pendingDeliveries(buyer, "node", 10).isEmpty());
        assertTrue(repository.claimAuctionPublications("worker", 1, 8).isEmpty());
        assertTrue(repository.cancelAuction(settlement, null, true));
        assertEquals(AuctionListing.ListingStatus.CANCELLED, repository.getListing(listing.getId()).getStatus());
    }

    private AuctionSettlement cancellation(AuctionListing listing) {
        long now = System.currentTimeMillis();
        long listingId = listing.getId();
        String id = AuctionPublication.cancellationId(listingId);
        var receipt = new MarketOperation(id, id, seller, "AUCTION_CANCEL", "listing=" + listingId,
            "DONE", "", "0", listing.getItemData(), "撤单已提交", "", now, now);
        var history = new AuctionHistory(0, listingId, seller, null, listing.getItemData(),
            listing.getItemDisplayName(), 0, listing.getCurrency(), 0, "CANCELLED", now);
        var item = new MarketOperation(id + ":item", id, seller, "ITEM", "auction_cancel_return",
            "READY", "", "0", listing.getItemData(), "", "", now, now);
        var refund = new MarketOperation(id + ":refund", id, buyer, "CURRENCY", "auction_cancel_refund",
            "READY", listing.getCurrency(), "20", "", "", "", now, now);
        var publication = new AuctionPublication(id, listingId, seller, null, listing.getItemDisplayName(),
            0, listing.getCurrency(), 0, "", false, true, now);
        return new AuctionSettlement(listing, receipt, history, List.of(item, refund), publication);
    }

    @Test
    void changedQuoteCannotReserveAndRacingPaymentsQueueExactlyOneRefundEach() {
        long deadline = System.currentTimeMillis() + 60_000;
        AuctionListing listing = listing(false, deadline);
        var quote = repository.readAuctionBidTerms(listing.getId());
        assertTrue(quote.canBid(buyer));
        var first = bid(listing, buyer, "Buyer", 25, 25);
        assertTrue(repository.reserveAuctionBid(first));
        assertEquals(AuctionBidOrder.Outcome.PLACED, repository.finishAuctionBid(first, CurrencyTransactionResult.ok(), deadline));
        var current = repository.readAuctionBidTerms(listing.getId());
        assertEquals("Buyer", current.highestBidderName());
        assertNotEquals(quote.revision(), current.revision());
        assertFalse(repository.reserveAuctionBid(bid(listing, buyer, "Buyer", 30, 5)));

        var latest = repository.getListing(listing.getId());
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        var winner = bid(latest, second, "Second", 30, 30);
        var race = bid(latest, third, "Third", 35, 35);
        assertTrue(repository.reserveAuctionBid(winner));
        assertTrue(repository.reserveAuctionBid(race));
        assertEquals(AuctionBidOrder.Outcome.PLACED, repository.finishAuctionBid(winner, CurrencyTransactionResult.ok(), deadline));
        assertEquals(AuctionBidOrder.Outcome.REFUND_QUEUED, repository.finishAuctionBid(race, CurrencyTransactionResult.ok(), deadline));
        assertThrows(IllegalStateException.class, () -> repository.finishAuctionBid(race, CurrencyTransactionResult.ok(), deadline));
        assertEquals(second, repository.getListing(listing.getId()).getHighestBidder());
        assertEquals(2, repository.getBidsForListing(listing.getId()).size());
        var outbid = repository.pendingDeliveries(buyer, "node", 10);
        var refunded = repository.pendingDeliveries(third, "node", 10);
        assertEquals(1, outbid.size());
        assertEquals("25.0", outbid.get(0).amount());
        assertEquals(1, refunded.size());
        assertEquals("35.0", refunded.get(0).amount());
    }

    @Test
    void refundWriteFailureRollsBackTheBidAndPaymentReceipt() throws Exception {
        var listing = listing(true, System.currentTimeMillis() + 60_000);
        UUID newBidder = UUID.randomUUID();
        var order = bid(listing, newBidder, "Second", 30, 30);
        assertTrue(repository.reserveAuctionBid(order));
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("auction.db"));
             var statement = conn.createStatement()) {
            statement.execute("""
                CREATE TRIGGER reject_bid_refund BEFORE INSERT ON %soperations
                WHEN NEW.reference = 'auction_outbid_refund'
                BEGIN SELECT RAISE(ABORT, 'test'); END
                """.formatted(repository.tablePrefix()));
        }
        assertThrows(IllegalStateException.class, () -> repository.finishAuctionBid(order, CurrencyTransactionResult.ok(), listing.getExpiresAt()));
        assertEquals(buyer, repository.getListing(listing.getId()).getHighestBidder());
        assertTrue(repository.getBidsForListing(listing.getId()).isEmpty());
        assertTrue(repository.pendingDeliveries(buyer, "node", 10).isEmpty());
        assertEquals(2, repository.operations("RUNNING", newBidder, 0, 10).size());
    }

    @Test
    void uncertainDebitLeavesTheListingUntouchedAndBothReceiptsReviewable() {
        var listing = listing(false, System.currentTimeMillis() + 60_000);
        var order = bid(listing, buyer, "Buyer", 30, 30);
        assertTrue(repository.reserveAuctionBid(order));
        assertEquals(AuctionBidOrder.Outcome.REVIEW,
            repository.finishAuctionBid(order, CurrencyTransactionResult.unknown(""), listing.getExpiresAt()));
        assertNull(repository.getListing(listing.getId()).getHighestBidder());
        assertEquals(2, repository.operations("REVIEW", buyer, 0, 10).size());
        assertTrue(repository.pendingDeliveries(buyer, "node", 10).isEmpty());
    }

    private AuctionBidOrder bid(AuctionListing listing, UUID bidder, String name, double price, double debit) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        var order = new MarketOperation(id, id, bidder, "AUCTION_BID", "listing=" + listing.getId(),
            "RUNNING", listing.getCurrency(), Double.toString(price), "", "", "node", now, now);
        var debitRow = new MarketOperation(id + ":debit", id, bidder, "DEBIT", "currency", "RUNNING",
            listing.getCurrency(), Double.toString(debit), "", "", "node", now, now);
        return new AuctionBidOrder(listing, order, debitRow, price, name);
    }

    @Test
    void playerShopDirectoryGroupsRenamedSellersAndSearchTreatsWildcardsLiterally() {
        long now = System.currentTimeMillis();
        repository.ensurePlayerShopProfile(seller, "CurrentName", true);
        var first = new PlayerShopListing(0, seller, "OldName", "item", "Lamp_10%!", "", "", "", 10,
            "coin", PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        var second = new PlayerShopListing(0, seller, "CurrentName", "item", "LampX10off", "", "", "", 10,
            "coin", PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        var third = new PlayerShopListing(0, buyer, "OtherSeller", "item", "Other item", "", "", "", 10,
            "coin", PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        for (var item : List.of(first, second, third)) assertTrue(repository.insertPlayerShopListing(item));
        assertEquals(2, repository.countPlayerShopSellers());
        var sellers = repository.getPlayerShopSellers(0, 2);
        assertEquals(List.of(seller, buyer), sellers.stream().map(MarketRepository.PlayerShopSellerInfo::seller).toList());
        assertEquals(2, sellers.get(0).activeCount());
        assertEquals("CurrentName", sellers.get(0).sellerName());
        assertEquals(List.of(first.getId()), repository.searchPlayerShopListings("_10%!", 0, 20)
            .stream().map(PlayerShopListing::getId).toList());
        assertEquals(1, repository.countSearchPlayerShopListingsBySeller(seller, "_10%!"));
        assertEquals(second.getId(), repository.getActivePlayerShopListingsBySeller(seller, 0, 1).get(0).getId());
    }

    @Test
    void shareQueryRequiresTheListingStoreToBePublished() {
        long now = System.currentTimeMillis();
        repository.ensurePlayerShopProfile(seller, "Seller", false);
        var listing = new PlayerShopListing(0, seller, "Seller", "serialized", "Item", "", "", "", 10,
            "coin", PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        assertTrue(repository.insertPlayerShopListing(listing));
        assertNull(repository.getPublishedPlayerShopListing(listing.getId()));
        assertNotNull(repository.getPlayerShopListing(listing.getId()));
        var profile = repository.playerShopProfile(seller);
        assertTrue(repository.savePlayerShopProfile(profile, profile.version(), true));
        assertEquals(listing.getId(), repository.getPublishedPlayerShopListing(listing.getId()).getId());
    }

    @Test
    void concurrentExpiryCreatesOneHistoryAndOnePairOfDeliveries() throws Exception {
        var listing = listing(true, System.currentTimeMillis() - 10);
        var settlement = settlement(listing);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return repository.settleExpiredAuction(settlement); });
            var b = pool.submit(() -> { start.await(); return repository.settleExpiredAuction(settlement); });
            start.countDown();
            assertNotEquals(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(AuctionListing.ListingStatus.SOLD, repository.getListing(listing.getId()).getStatus());
        assertEquals(1, repository.getHistoryByPlayer(buyer, 0, 20).size());
        assertEquals(1, repository.pendingDeliveries(buyer, "node", 20).size());
        assertEquals(1, repository.pendingDeliveries(seller, "node", 20).size());
        assertFalse(repository.settleExpiredAuction(settlement));
    }

    @Test
    void aDeliveryInsertFailureRollsBackStatusAndHistory() {
        var listing = listing(false, System.currentTimeMillis() - 10);
        var settlement = settlement(listing);
        var conflicting = settlement.deliveries().get(0);
        var broken = new AuctionSettlement(listing, settlement.receipt(), settlement.history(),
            List.of(conflicting, conflicting), publication(listing, false, settlement.receipt().createdAt()));
        assertThrows(IllegalStateException.class, () -> repository.settleExpiredAuction(broken));
        assertEquals(AuctionListing.ListingStatus.ACTIVE, repository.getListing(listing.getId()).getStatus());
        assertTrue(repository.getHistoryByPlayer(seller, 0, 20).isEmpty());
        assertTrue(repository.operations(null, seller, 0, 20).isEmpty());
        assertTrue(repository.claimAuctionPublications("worker", 1, 4).isEmpty());
        assertEquals(0, repository.auctionSettlementRevision());
        assertTrue(repository.settleExpiredAuction(settlement));
        assertEquals(AuctionListing.ListingStatus.EXPIRED, repository.getListing(listing.getId()).getStatus());
        assertEquals("ITEM", repository.pendingDeliveries(seller, "node", 20).get(0).kind());
    }

    @Test
    void publicationLeaseKeepsIndependentAcknowledgementsAcrossTakeover() {
        var listing = listing(true, System.currentTimeMillis() - 10);
        var settlement = settlement(listing);
        var publication = publication(listing, true, settlement.receipt().createdAt());
        assertTrue(repository.settleExpiredAuction(new AuctionSettlement(listing, settlement.receipt(),
            settlement.history(), settlement.deliveries(), publication)));
        assertEquals(1, repository.auctionSettlementRevision());
        assertEquals(List.of(publication), repository.claimAuctionPublications("first", 100, 4));
        assertTrue(repository.claimAuctionPublications("second", 200, 4).isEmpty());
        repository.finishAuctionPublication(publication.id(), "first", true, false, 600);
        var replay = repository.claimAuctionPublications("second", 600, 4).get(0);
        assertFalse(replay.eventPending());
        assertTrue(replay.refreshPending());
        repository.finishAuctionPublication(publication.id(), "first", true, true, 700);
        repository.finishAuctionPublication(publication.id(), "second", false, true, 800);
        assertTrue(repository.claimAuctionPublications("third", 999, 4).isEmpty());
        assertEquals(1, repository.auctionSettlementRevision());
    }

    @Test
    void aConflictingBatchCannotPartiallyClaimOrAcknowledgeItems() {
        var first = settlement(listing(false, System.currentTimeMillis() - 10));
        var second = settlement(listing(false, System.currentTimeMillis() - 10));
        assertTrue(repository.settleExpiredAuction(first));
        assertTrue(repository.settleExpiredAuction(second));
        var ids = List.of(first.deliveries().get(0).id(), second.deliveries().get(0).id());
        assertFalse(repository.transitionItemBatch(List.of(ids.get(0), "missing"), "node", true));
        assertEquals(2, repository.pendingDeliveries(seller, "node", 20).size());
        assertTrue(repository.transitionItemBatch(ids, "node", true));
        assertFalse(repository.transitionItemBatch(ids, "other", false));
        assertEquals(2, repository.pendingDeliveries(seller, "node", 20).size());
        assertTrue(repository.transitionItemBatch(ids, "node", false));
        assertTrue(repository.pendingDeliveries(seller, "node", 20).isEmpty());
    }

    private AuctionPublication publication(AuctionListing listing, boolean sold, long settledAt) {
        return new AuctionPublication("auction-expiry:" + listing.getId(), listing.getId(), seller,
            sold ? buyer : null, "test", sold ? 20 : 0, "money", sold ? 1 : 0, "Buyer", sold, true, settledAt);
    }

    @Test
    void activeUnexpiredListingCannotBeSettled() {
        var listing = listing(true, System.currentTimeMillis() + 60_000);
        assertFalse(repository.settleExpiredAuction(settlement(listing)));
        assertEquals(AuctionListing.ListingStatus.ACTIVE, repository.getListing(listing.getId()).getStatus());
        assertTrue(repository.getHistoryByPlayer(buyer, 0, 20).isEmpty());
    }

    private AuctionListing listing(boolean sold, long expires) {
        var listing = new AuctionListing(0, seller, "Seller", "snapshot", "test", "", "", "other",
            0, 20, sold ? 20 : 0, sold ? buyer : null, "money", AuctionListing.ListingType.AUCTION,
            AuctionListing.ListingStatus.ACTIVE, expires - 120_000, expires);
        assertTrue(repository.insertListing(listing));
        return listing;
    }

    private AuctionSettlement settlement(AuctionListing listing) {
        boolean sold = listing.getHighestBidder() != null;
        String trade = "auction-expiry:" + listing.getId();
        var receipt = new MarketOperation(trade, trade, seller, "AUCTION_SETTLE", "listing=" + listing.getId(),
            "DONE", "money", sold ? "20" : "0", "snapshot", "", "", 1, 1);
        var history = new AuctionHistory(0, listing.getId(), seller, listing.getHighestBidder(), "snapshot", "test",
            listing.getCurrentBid(), "money", sold ? 1 : 0, sold ? "BID_WIN" : "EXPIRED", 1);
        var item = new MarketOperation(UUID.randomUUID().toString(), trade, sold ? buyer : seller,
            "ITEM", sold ? "auction_bidwin_item" : "auction_expired_return", "READY", "", "0", "snapshot", "", "", 1, 1);
        var income = new MarketOperation(UUID.randomUUID().toString(), trade, seller,
            "CURRENCY", "auction_bidwin_income", "READY", "money", "19", "", "", "", 1, 1);
        return new AuctionSettlement(listing, receipt, history, sold ? List.of(item, income) : List.of(item));
    }
}
