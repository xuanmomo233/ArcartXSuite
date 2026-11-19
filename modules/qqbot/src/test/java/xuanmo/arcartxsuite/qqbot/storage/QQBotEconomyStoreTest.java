package xuanmo.arcartxsuite.qqbot.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.*;

class QQBotEconomyStoreTest {
    @TempDir Path folder;
    SQLiteDataSource source;
    QQBotEconomyStore store;

    @BeforeEach void initialize() throws Exception {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve("qqbot.db"));
        store = new QQBotEconomyStore(source, false, "test_");
        sql("CREATE TABLE test_points (qq_id BIGINT PRIMARY KEY,balance INT NOT NULL DEFAULT 0,"
            + "total_earned INT NOT NULL DEFAULT 0,total_spent INT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
        sql("CREATE TABLE test_signin (qq_id BIGINT NOT NULL,sign_date VARCHAR(10) NOT NULL,streak INT NOT NULL,"
            + "signed_at BIGINT NOT NULL,PRIMARY KEY(qq_id,sign_date))");
        sql("CREATE TABLE test_redeem_log (qq_id BIGINT,prize_id VARCHAR(64),redeem_date VARCHAR(10))");
        sql("CREATE TABLE test_red_packets (id INTEGER PRIMARY KEY AUTOINCREMENT,sender_qq BIGINT,group_id BIGINT,"
            + "total_amount INT,remaining_amount INT,count INT,claimed_count INT,expire_at BIGINT,created_at BIGINT)");
        sql("CREATE TABLE test_red_packet_claims (id INTEGER PRIMARY KEY AUTOINCREMENT,red_packet_id BIGINT,claimer_qq BIGINT,amount INT,claimed_at BIGINT)");
        try (Connection conn = source.getConnection()) { store.initialize(conn); }
    }

    @AfterEach void shutdown() { if (store != null) store.close(); }

    @Test void retirementDoesNotWaitForBlockedLeaseSqlOnTheCaller() throws Exception {
        var data = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var block = new java.util.concurrent.atomic.AtomicBoolean();
        Thread caller = Thread.currentThread();
        org.mockito.Mockito.when(data.getConnection()).thenAnswer(call -> {
            if (block.get()) {
                assertNotSame(caller, Thread.currentThread());
                entered.countDown();
                assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
            }
            return source.getConnection();
        });
        var retiring = new QQBotEconomyStore(data, false, "test_");
        try (Connection conn = source.getConnection()) { retiring.initialize(conn); }
        block.set(true);
        try {
            var stopped = retiring.retire().toCompletableFuture();
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(stopped.isDone());
            assertEquals(2, value("SELECT COUNT(*) FROM test_economy_workers WHERE active=1"));
            release.countDown();
            stopped.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, value("SELECT COUNT(*) FROM test_economy_workers WHERE active=1"));
        } finally {
            release.countDown();
            retiring.close();
        }
    }

    @Test void simultaneousSignInsCreditExactlyOnce() throws Exception {
        var outcomes = concurrent(12, () -> store.signIn(1, "2026-11-07", 10, 2, 50));
        assertEquals(1, outcomes.stream().filter(r -> r.status() == MutationStatus.SUCCESS).count());
        assertEquals(11, outcomes.stream().filter(r -> r.status() == MutationStatus.ALREADY).count());
        assertEquals(10, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(1, value("SELECT COUNT(*) FROM test_signin"));
        assertEquals(1, value("SELECT COUNT(*) FROM test_point_log WHERE action='signin'"));
    }

    @Test void failedCreditRollsBackSignInAndCanBeRetried() throws Exception {
        sql("CREATE TRIGGER reject_credit BEFORE UPDATE OF balance ON test_points WHEN NEW.balance>OLD.balance"
            + " BEGIN SELECT RAISE(ABORT,'injected credit failure'); END");
        assertEquals(MutationStatus.FAILED, store.signIn(1, "2026-11-07", 10, 2, 50).status());
        assertEquals(0, value("SELECT COUNT(*) FROM test_signin"));
        assertEquals(0, value("SELECT COUNT(*) FROM test_point_log"));
        sql("DROP TRIGGER reject_credit");
        assertEquals(MutationStatus.SUCCESS, store.signIn(1, "2026-11-07", 10, 2, 50).status());
    }

    @Test void failedRecipientCreditCannotDebitSender() throws Exception {
        store.adjust(1, 100, "seed", "");
        sql("CREATE TRIGGER reject_recipient BEFORE UPDATE OF balance ON test_points WHEN NEW.qq_id=2"
            + " BEGIN SELECT RAISE(ABORT,'injected recipient failure'); END");
        assertEquals(MutationStatus.FAILED, store.transfer(1, 2, 50).status());
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(0, value("SELECT COUNT(*) FROM test_point_log WHERE action='transfer'"));
    }

    @Test void simultaneousTransfersConservePointsAndRejectOverdraft() throws Exception {
        store.adjust(1, 100, "seed", "");
        var results = concurrent(12, () -> store.transfer(1, 2, 20));
        assertEquals(5, results.stream().filter(r -> r.status() == MutationStatus.SUCCESS).count());
        assertEquals(7, results.stream().filter(r -> r.status() == MutationStatus.INSUFFICIENT).count());
        assertEquals(100, value("SELECT SUM(balance) FROM test_points"));
        assertEquals(0, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=2"));
    }

    @Test void legacyMixedCaseRedemptionsCountAgainstCanonicalLimit() throws Exception {
        store.adjust(1, 100, "seed", "");
        sql("INSERT INTO test_redeem_log VALUES(1,'dIaMoNd','2026-11-07')");
        assertEquals(MutationStatus.LIMIT, reserve("DIAMOND", 1, List.of("reward")).status());
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
    }

    @Test void concurrentReservationLocksQuotaAndBalance() throws Exception {
        store.adjust(1, 100, "seed", "");
        var outcomes = concurrent(12, () -> reserve("diamond", 1, List.of("reward")));
        assertEquals(1, outcomes.stream().filter(r -> r.status() == MutationStatus.SUCCESS).count());
        assertEquals(11, outcomes.stream().filter(r -> r.status() == MutationStatus.REVIEW).count());
        assertEquals(90, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(1, value("SELECT COUNT(*) FROM test_redemptions"));
        String id = outcomes.stream().filter(r -> r.redemption() != null).findFirst().orElseThrow().redemption().id();
        assertTrue(store.beginItem(id, 0));
        store.recordItem(id, 0, RedemptionItemState.SENT, "mail-id");
        assertEquals(RedemptionState.COMPLETE, store.finish(id).state());
        assertEquals(MutationStatus.LIMIT, reserve("DIAMOND", 1, List.of("reward")).status());
    }

    @Test void partiallyDeliveredRewardCannotBeFullyRefundedOrReplayed() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 1, List.of("first", "second")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        store.recordItem(id, 0, RedemptionItemState.SENT, "mail-1");
        assertFalse(store.beginItem(id, 0));
        assertTrue(store.beginItem(id, 1));
        store.recordItem(id, 1, RedemptionItemState.FAILED, "preset missing");
        assertEquals(RedemptionState.REVIEW, store.finish(id).state());
        assertEquals(MutationStatus.REVIEW, store.resolve(id, RedemptionResolution.CONFIRM_NOT_DELIVERED, "已核对邮件"));
        assertEquals(90, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(MutationStatus.SUCCESS, store.resolve(id, RedemptionResolution.CONFIRM_DELIVERED, "已人工补齐第二封"));
        assertEquals(MutationStatus.ALREADY, store.resolve(id, RedemptionResolution.CONFIRM_DELIVERED, "重复核对请求"));
        assertEquals(RedemptionState.COMPLETE, store.get(id).state());
    }

    @Test void allDefiniteFailuresRefundOnceAndReleaseQuota() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 1, List.of("missing")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        store.recordItem(id, 0, RedemptionItemState.FAILED, "not found");
        assertEquals(RedemptionState.REFUNDED, store.finish(id).state());
        assertEquals(RedemptionState.REFUNDED, store.finish(id).state());
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(100, value("SELECT total_earned FROM test_points WHERE qq_id=1"));
        assertEquals(0, value("SELECT total_spent FROM test_points WHERE qq_id=1"));
        assertEquals(1, value("SELECT COUNT(*) FROM test_point_log WHERE action='redeem_refund'"));
        assertEquals(MutationStatus.SUCCESS, reserve("DIAMOND", 1, List.of("reward")).status());
    }

    @Test void restartFreezesUnknownDeliveryUntilExplicitAdjudication() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 0, List.of("reward")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        store.close();
        store = new QQBotEconomyStore(source, false, "test_");
        try (Connection conn = source.getConnection()) { store.initialize(conn); }
        assertEquals(RedemptionState.REVIEW, store.get(id).state());
        assertEquals(RedemptionItemState.SENDING, store.get(id).items().get(0).state());
        assertFalse(store.beginItem(id, 0));
        assertEquals(MutationStatus.REVIEW, reserve("diamond", 0, List.of("reward")).status());
        assertEquals(1, store.pending(10).size());
        assertEquals(MutationStatus.SUCCESS, store.resolve(id, RedemptionResolution.CONFIRM_NOT_DELIVERED, "已核对服务端邮件记录且确认未发送"));
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
    }

    @Test void recipientOverflowRollsBackBothBalances() throws Exception {
        store.adjust(1, 100, "seed", "");
        store.adjust(2, Integer.MAX_VALUE, "seed", "");
        assertEquals(MutationStatus.FAILED, store.transfer(1, 2, 1).status());
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertEquals(Integer.MAX_VALUE, value("SELECT balance FROM test_points WHERE qq_id=2"));
    }

    @Test void healthyOtherNodeCannotStealOrAdjudicateActiveDelivery() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 0, List.of("first", "second")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        QQBotEconomyStore other = new QQBotEconomyStore(source, false, "test_");
        try {
            try (Connection conn = source.getConnection()) { other.initialize(conn); }
            assertEquals(RedemptionState.RESERVED, other.get(id).state());
            assertFalse(other.beginItem(id, 1));
            assertEquals(MutationStatus.REVIEW, other.resolve(id, RedemptionResolution.CONFIRM_NOT_DELIVERED, "其他节点尝试退款"));
            assertEquals(90, value("SELECT balance FROM test_points WHERE qq_id=1"));
            store.recordItem(id, 0, RedemptionItemState.UNKNOWN, "result unknown");
            assertEquals(MutationStatus.REVIEW, other.resolve(id, RedemptionResolution.CONFIRM_NOT_DELIVERED, "仍有活跃发放节点"));
            store.finish(id);
            assertEquals(MutationStatus.SUCCESS, other.resolve(id, RedemptionResolution.CONFIRM_NOT_DELIVERED, "已停止发放并独立核实全部未发"));
        } finally { other.close(); }
    }

    @Test void closedOwnerCannotStartAnotherMailItem() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 0, List.of("first", "second")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        store.close();
        assertFalse(store.beginItem(id, 1));
        // A result of the call already in flight can still be recorded, without starting another.
        store.recordItem(id, 0, RedemptionItemState.SENT, "already-in-flight mail");
        assertEquals(RedemptionState.REVIEW, store.finish(id).state());
    }

    @Test void expiredOwnerIsFencedBeforeAnotherItemCanStart() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 0, List.of("first", "second")).redemption().id();
        assertTrue(store.beginItem(id, 0));
        sql("UPDATE test_economy_workers SET heartbeat_at=0");
        QQBotEconomyStore other = new QQBotEconomyStore(source, false, "test_");
        try {
            try (Connection conn = source.getConnection()) { other.initialize(conn); }
            assertEquals(RedemptionState.REVIEW, other.get(id).state());
            assertFalse(store.beginItem(id, 1));
            assertFalse(other.beginItem(id, 1));
            assertEquals(90, value("SELECT balance FROM test_points WHERE qq_id=1"));
        } finally { other.close(); }
    }

    @Test void phaseWriteFailureDoesNotAuthorizeMailAndUnknownResultNeverAutoRefunds() throws Exception {
        store.adjust(1, 100, "seed", "");
        String id = reserve("diamond", 0, List.of("reward")).redemption().id();
        sql("CREATE TRIGGER reject_phase BEFORE UPDATE OF state ON test_redemption_items WHEN NEW.state='SENDING'"
            + " BEGIN SELECT RAISE(ABORT,'injected phase failure'); END");
        assertThrows(StorageFailure.class, () -> store.beginItem(id, 0));
        assertEquals(RedemptionItemState.PENDING, store.get(id).items().get(0).state());
        sql("DROP TRIGGER reject_phase");
        assertTrue(store.beginItem(id, 0));
        sql("CREATE TRIGGER reject_result BEFORE UPDATE OF state ON test_redemption_items WHEN NEW.state='SENT'"
            + " BEGIN SELECT RAISE(ABORT,'injected result failure'); END");
        assertThrows(StorageFailure.class, () -> store.recordItem(id, 0, RedemptionItemState.SENT, "remote mail already committed"));
        store.freeze(id, "结果落库失败，停止发放后待独立核对");
        assertEquals(RedemptionState.REVIEW, store.finish(id).state());
        assertEquals(RedemptionItemState.SENDING, store.get(id).items().get(0).state());
        assertEquals(90, value("SELECT balance FROM test_points WHERE qq_id=1"));
        assertFalse(store.beginItem(id, 0));
    }

    @Test void failedPacketCreationRollsBackSenderDebit() throws Exception {
        store.adjust(1, 100, "seed", "");
        sql("CREATE TRIGGER reject_packet BEFORE INSERT ON test_red_packets BEGIN SELECT RAISE(ABORT,'injected packet failure'); END");
        assertEquals(MutationStatus.FAILED, store.sendRedPacket(1, 10001, 100, 10, System.currentTimeMillis()+60_000).status());
        assertEquals(100, value("SELECT balance FROM test_points WHERE qq_id=1"));
    }

    @Test void concurrentPacketClaimsDistributeEveryPointExactlyOnce() throws Exception {
        store.adjust(1, 100, "seed", "");
        var packet = store.sendRedPacket(1, 10001, 100, 10, System.currentTimeMillis()+60_000);
        assertEquals(MutationStatus.SUCCESS, packet.status());
        var next = new java.util.concurrent.atomic.AtomicLong(2);
        var results = concurrent(24, () -> store.claimRedPacket(packet.id(), next.getAndIncrement()));
        assertEquals(10, results.stream().filter(amount -> amount > 0).count());
        assertEquals(100, results.stream().filter(amount -> amount > 0).mapToInt(Integer::intValue).sum());
        assertEquals(0, value("SELECT remaining_amount FROM test_red_packets"));
        assertEquals(10, value("SELECT COUNT(*) FROM test_red_packet_claims"));
        assertEquals(100, value("SELECT SUM(balance) FROM test_points"));
    }

    @Test void sameUserCannotClaimTwiceAndConcurrentRefundsConservePoints() throws Exception {
        store.adjust(1, 100, "seed", "");
        var packet = store.sendRedPacket(1, 10001, 100, 10, System.currentTimeMillis()+60_000);
        var results = concurrent(12, () -> store.claimRedPacket(packet.id(), 2));
        assertEquals(1, results.stream().filter(amount -> amount > 0).count());
        assertEquals(11, results.stream().filter(amount -> amount == -2).count());
        sql("UPDATE test_red_packets SET expire_at=0");
        concurrent(8, () -> { store.refundRedPackets(); return true; });
        assertEquals(100, value("SELECT SUM(balance) FROM test_points"));
        assertEquals(0, value("SELECT remaining_amount FROM test_red_packets"));
        assertEquals(1, value("SELECT COUNT(*) FROM test_point_log WHERE action='redpacket_refund'"));
    }

    private RedemptionReservation reserve(String prize, int limit, List<String> presets) {
        return store.reserve(1, prize, UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alice", 10,
            "2026-11-07", limit, presets);
    }

    private <T> List<T> concurrent(int count, Callable<T> work) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        try {
            List<Callable<T>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) tasks.add(work);
            List<T> results = new ArrayList<>();
            for (var future : executor.invokeAll(tasks)) results.add(future.get());
            return results;
        } finally { executor.shutdownNow(); }
    }

    private void sql(String sql) throws Exception {
        try (Connection conn = source.getConnection(); Statement statement = conn.createStatement()) { statement.execute(sql); }
    }

    private int value(String sql) throws Exception {
        try (Connection conn = source.getConnection(); Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
