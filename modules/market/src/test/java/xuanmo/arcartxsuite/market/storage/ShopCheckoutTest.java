package xuanmo.arcartxsuite.market.storage;

import java.nio.file.Path;
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
import xuanmo.arcartxsuite.market.shop.ShopService;

import static org.junit.jupiter.api.Assertions.*;

class ShopCheckoutTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private JdbcMarketRepository repository;
    private final ShopCheckout checkout = new ShopCheckout("shop", "item", 1, "global", 3, 1, "never");

    @BeforeEach
    void initialize() throws Exception {
        repository = openRepository();
    }

    private JdbcMarketRepository openRepository() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("market.db"));
        var store = new JdbcMarketRepository(source, StorageDescriptor.sqlite("market.db"),
            directory.toFile(), Logger.getAnonymousLogger());
        store.initialize();
        return store;
    }

    @Test
    void concurrentOrdersCompeteForTheSameQuotaAndReplayKeepsTheOriginalOrder() throws Exception {
        var first = order();
        var second = order();
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { gate.await(); return repository.reserveShopOrder(first, checkout, List.of(reward(first))); });
            var b = pool.submit(() -> { gate.await(); return repository.reserveShopOrder(second, checkout, List.of(reward(second))); });
            gate.countDown();
            boolean firstWon = a.get(10, TimeUnit.SECONDS).started();
            assertNotEquals(firstWon, b.get(10, TimeUnit.SECONDS).started());
            var winner = firstWon ? first : second;
            var rejected = firstWon ? second : first;
            assertEquals("FAILED", repository.shopOrder(rejected.id()).state());
            assertFalse(repository.reserveShopOrder(winner, checkout, List.of(reward(winner))).started());
            assertEquals(2, availability().stock());
            assertEquals(0, availability().limit());
            repository.finishShopOrder(winner, true, "paid");
            assertEquals("DONE", repository.shopOrder(winner.id()).state());
            assertEquals(1, repository.pendingDeliveries(player, "node", 20).size());
            assertTrue(ShopService.matchesOrder(repository.shopOrder(winner.id()), player, "quote"));
            assertFalse(ShopService.matchesOrder(repository.shopOrder(winner.id()), UUID.randomUUID(), "quote"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void declinedPaymentReleasesQuotaAndStockAndNeverPublishesItsReward() {
        var first = order();
        assertTrue(repository.reserveShopOrder(first, checkout, List.of(reward(first))).started());
        repository.finishShopOrder(first, false, "declined");
        assertEquals("FAILED", repository.shopOrder(first.id()).state());
        assertEquals(3, availability().stock());
        assertEquals(1, availability().limit());
        assertTrue(repository.pendingDeliveries(player, "node", 20).isEmpty());
        assertThrows(IllegalStateException.class, () -> repository.finishShopOrder(first, false, "duplicate"));
        assertTrue(repository.reserveShopOrder(order(), checkout, List.of()).started());
    }

    @Test
    void interruptedOrderWithConfirmedDebitPublishesOnlyItsSavedReward() {
        var order = order();
        assertTrue(repository.reserveShopOrder(order, checkout, List.of(reward(order))).started());
        repository.insertOperation(new MarketOperation(UUID.randomUUID().toString(), order.id(), player,
            "DEBIT", "currency", "DONE", "money", "10", "", "", order.owner(), 1, 1));
        repository.recoverAbandonedOperations();
        repository.recoverAbandonedOperations();
        assertEquals("DONE", repository.shopOrder(order.id()).state());
        assertEquals(1, repository.pendingDeliveries(player, "node", 20).size());
    }

    @Test
    void completedRequestAndItsSingleRewardSurviveReopeningTheDatabase() throws Exception {
        var first = order();
        assertTrue(repository.reserveShopOrder(first, checkout, List.of(reward(first))).started());
        repository.finishShopOrder(first, true, "paid");
        repository.shutdown();
        repository = openRepository();
        assertTrue(ShopService.orderResult(repository.shopOrder(first.id())).success());
        assertFalse(repository.reserveShopOrder(first, checkout, List.of(reward(first))).started());
        assertEquals(1, repository.pendingDeliveries(player, "node", 20).size());
        assertEquals(2, availability().stock());
        var noQuota = new ShopCheckout.Product("item", "global", 3, 0);
        assertEquals(Integer.MAX_VALUE, repository.shopAvailability(player, "shop", List.of(noQuota)).get("item").limit());
    }

    private ShopCheckout.Availability availability() {
        return repository.shopAvailability(player, "shop", List.of(new ShopCheckout.Product("item", "global", 3, 1))).get("item");
    }

    private MarketOperation order() {
        String id = UUID.randomUUID().toString();
        return new MarketOperation(id, id, player, "SHOP_BUY", "shop-order:quote:shop/item",
            "RUNNING", "money", "10", "", "", "worker", 1, 1);
    }

    private MarketOperation reward(MarketOperation order) {
        return new MarketOperation(UUID.randomUUID().toString(), order.id(), player,
            "ITEM", "shop_item:item", "PREPARED", "", "0", "snapshot", "", order.owner(), 1, 1);
    }

    @Test
    void dailyQuotaUsesTheLocalCalendarAndCannotReleaseIntoANewDay() {
        var daily = new ShopCheckout("shop", "item", 1, "global", 3, 1, "daily");
        var midnight = java.time.ZonedDateTime.parse("2026-11-14T00:00:00+08:00[Asia/Shanghai]");
        assertEquals(midnight.toInstant().toEpochMilli(), daily.nextLimitReset(midnight.minusNanos(1)));
        assertEquals(midnight.plusDays(1).toInstant().toEpochMilli(), daily.nextLimitReset(midnight));
        var first = order();
        var reservation = repository.reserveShopOrder(first, daily, List.of());
        assertTrue(reservation.started());
        long elapsed = System.currentTimeMillis() - 1;
        repository.upsertShopLimit(new xuanmo.arcartxsuite.market.shop.ShopLimitRecord(
            player, "shop", "item", 1, elapsed, elapsed));
        var second = order();
        var nextDay = repository.reserveShopOrder(second, daily, List.of());
        assertTrue(nextDay.started());
        assertEquals(0, availability().limit());
        repository.finishShopOrder(first, false, "declined in previous period");
        assertEquals(0, availability().limit());
        assertEquals(2, availability().stock());
    }

    @Test
    void declinedPaymentDoesNotReleaseIntoResetStockOrQuota() {
        var first = order();
        assertTrue(repository.reserveShopOrder(first, checkout, List.of(reward(first))).started());
        assertTrue(repository.adjustShopStock("shop", "item", "global", 3, "set", 2, null));
        repository.resetShopLimits("shop", "item", player);
        assertTrue(repository.reserveShopOrder(order(), checkout, List.of()).started());

        repository.finishShopOrder(first, false, "declined after reset");

        assertEquals("FAILED", repository.shopOrder(first.id()).state());
        assertEquals(1, availability().stock());
        assertEquals(0, availability().limit());
        assertTrue(repository.pendingDeliveries(player, "node", 20).isEmpty());
    }

    @Test
    void confirmedDebitCanOnlyPublishItsOriginalRewardOnce() {
        var order = uncertainOrder();
        var reward = repository.operations("REVIEW", player, 0, 20).stream()
            .filter(op -> "ITEM".equals(op.kind())).findFirst().orElseThrow();
        assertFalse(repository.resolveOperation(reward.id(), "retry-confirmed", "bypass"));
        assertEquals("已确认扣款，按原快照补发", repository.reviewShopOrder(order.id(), "fulfill-paid", "verified"));
        repository.reviewShopOrder(order.id(), "fulfill-paid", "repeat");
        repository.reviewShopOrder(order.id(), "refund-paid", "repeat");
        assertEquals("DONE", repository.shopOrder(order.id()).state());
        var pending = repository.pendingDeliveries(player, "node", 20);
        assertEquals(1, pending.size());
        assertEquals(reward.id(), pending.get(0).id());
        assertEquals(2, availability().stock());
        assertEquals(0, availability().limit());
    }

    @Test
    void confirmedRefundReleasesTheOriginalReservationOnlyOnce() {
        var order = uncertainOrder();
        repository.reviewShopOrder(order.id(), "refund-paid", "verified");
        repository.reviewShopOrder(order.id(), "refund-paid", "repeat");
        var pending = repository.pendingDeliveries(player, "node", 20);
        assertEquals(1, pending.size());
        var refund = pending.get(0);
        assertEquals("CURRENCY", refund.kind());
        assertEquals("REFUND_PENDING", repository.shopOrder(order.id()).state());
        assertEquals(2, availability().stock());
        assertEquals(0, availability().limit());
        assertTrue(repository.transitionOperation(refund.id(), "READY", "RUNNING", "claimed", "refund-worker"));
        assertTrue(repository.transitionOperation(refund.id(), "RUNNING", "DONE", "credited", "refund-worker"));
        repository.finishConfirmedShopRefunds();
        repository.finishConfirmedShopRefunds();
        assertEquals("REFUNDED", repository.shopOrder(order.id()).state());
        assertEquals(3, availability().stock());
        assertEquals(1, availability().limit());
        assertTrue(repository.pendingDeliveries(player, "node", 20).isEmpty());
    }

    @Test
    void manualRefundConfirmationDoesNotRestoreIntoResetStockOrQuota() {
        var order = uncertainOrder();
        repository.reviewShopOrder(order.id(), "refund-paid", "verified");
        var refund = repository.pendingDeliveries(player, "node", 20).get(0);
        assertTrue(repository.transitionOperation(refund.id(), "READY", "RUNNING", "claimed", "refund-worker"));
        assertTrue(repository.transitionOperation(refund.id(), "RUNNING", "REVIEW", "unknown", "refund-worker"));
        assertFalse(repository.resolveOperation(refund.id(), "retry-confirmed", "bypass"));
        assertTrue(repository.adjustShopStock("shop", "item", "global", 3, "set", 2, null));
        repository.resetShopLimits("shop", "item", player);
        assertTrue(repository.reserveShopOrder(order(), checkout, List.of()).started());
        assertEquals("已登记人工确认的退款回执", repository.reviewShopOrder(order.id(), "confirm-refunded", "wallet proof"));
        assertEquals("REFUNDED", repository.shopOrder(order.id()).state());
        assertEquals(1, availability().stock());
        assertEquals(0, availability().limit());
        assertTrue(repository.pendingDeliveries(player, "node", 20).isEmpty());
    }

    private MarketOperation uncertainOrder() {
        var order = order();
        assertTrue(repository.reserveShopOrder(order, checkout, List.of(reward(order))).started());
        repository.insertOperation(new MarketOperation(UUID.randomUUID().toString(), order.id(), player,
            "DEBIT", "currency", "RUNNING", "money", "10", "", "", order.owner(), 1, 1));
        repository.recoverAbandonedOperations();
        assertEquals("REVIEW", repository.shopOrder(order.id()).state());
        return order;
    }
}
