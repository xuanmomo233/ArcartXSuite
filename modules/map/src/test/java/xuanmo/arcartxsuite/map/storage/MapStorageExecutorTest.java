package xuanmo.arcartxsuite.map.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import static org.junit.jupiter.api.Assertions.*;

class MapStorageExecutorTest {
    private static final MapOperationResult OK = MapOperationResult.success("ok");
    private static final MapOperationResult FAIL = MapOperationResult.failure("failed");

    @Test void sqlRunsOffThreadAndContinuationsRequireMainPump() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            Thread owner = Thread.currentThread();
            AtomicReference<Thread> worker = new AtomicReference<>();
            AtomicReference<Thread> callback = new AtomicReference<>();
            CountDownLatch entered = new CountDownLatch(1);
            var result = storage.query(() -> { worker.set(Thread.currentThread()); entered.countDown(); return 42; });
            var finished = result.thenAccept(value -> { assertEquals(42, value); callback.set(Thread.currentThread()); });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertNotSame(owner, worker.get());
            assertFalse(result.isDone(), "worker never runs Bukkit continuations");
            await(storage, finished);
            assertSame(owner, callback.get());
        }
    }

    @Test void writesExecuteInSubmissionOrder() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            List<Integer> writes = new ArrayList<>();
            List<CompletableFuture<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int value = i;
                results.add(storage.query(() -> { writes.add(value); return value; }));
            }
            await(storage, CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)));
            for (int i = 0; i < 100; i++) assertEquals(i, writes.get(i));
        }
    }

    @Test void duplicateReservationDoesNotChargeOrRollback() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            AtomicInteger charges = new AtomicInteger();
            AtomicInteger rollbacks = new AtomicInteger();
            var result = MapUnlockTransaction.execute(storage, () -> false, () -> true,
                () -> { charges.incrementAndGet(); return OK; }, () -> { rollbacks.incrementAndGet(); return null; }, FAIL, FAIL);
            await(storage, result);
            assertFalse(result.join().success());
            assertEquals(0, charges.get());
            assertEquals(0, rollbacks.get());
        }
    }

    @Test void settlementFailureWaitsForCompensationBeforeCompleting() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            AtomicBoolean row = new AtomicBoolean();
            AtomicInteger charged = new AtomicInteger();
            CountDownLatch rollbackEntered = new CountDownLatch(1);
            CountDownLatch releaseRollback = new CountDownLatch(1);
            var result = MapUnlockTransaction.execute(storage, () -> { row.set(true); return true; }, () -> true,
                () -> { charged.incrementAndGet(); charged.decrementAndGet(); return FAIL; },
                () -> { rollbackEntered.countDown(); releaseRollback.await(2, TimeUnit.SECONDS); row.set(false); return null; }, FAIL, FAIL);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (rollbackEntered.getCount() != 0 && System.nanoTime() < deadline) { storage.pump(); Thread.sleep(1); }
            assertEquals(0, rollbackEntered.getCount());
            assertFalse(result.isDone(), "do not release the token before rollback commits");
            releaseRollback.countDown();
            await(storage, result);
            assertFalse(result.join().success());
            assertFalse(row.get());
            assertEquals(0, charged.get());
        }
    }

    @Test void stoppingAfterReservationRollsBackWithoutCharging() throws Exception {
        var storage = new MapStorageExecutor();
        AtomicBoolean row = new AtomicBoolean();
        AtomicInteger charges = new AtomicInteger();
        CountDownLatch reserved = new CountDownLatch(1);
        var result = MapUnlockTransaction.execute(storage, () -> { row.set(true); reserved.countDown(); return true; },
            () -> !storage.isClosing(), () -> { charges.incrementAndGet(); return OK; },
            () -> { row.set(false); return null; }, FAIL, FAIL);
        assertTrue(reserved.await(2, TimeUnit.SECONDS));
        storage.close();
        assertTrue(result.isDone());
        assertFalse(result.join().success());
        assertFalse(row.get());
        assertEquals(0, charges.get());
    }

    @Test void rollbackErrorsRemainExceptionalAndDoNotReportSuccess() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            var result = MapUnlockTransaction.execute(storage, () -> true, () -> false, () -> OK,
                () -> { throw new java.sql.SQLException("rollback failed"); }, FAIL, FAIL);
            await(storage, result);
            assertTrue(result.isCompletedExceptionally());
        }
    }

    @Test void completionCanStopTheModuleWithoutDeadlockingItsOwnFuture() throws Exception {
        var storage = new MapStorageExecutor();
        var finished = storage.query(() -> 1).thenRun(storage::close);
        await(storage, finished);
        assertFalse(finished.isCompletedExceptionally());
        assertTrue(storage.query(() -> 2).isCompletedExceptionally());
    }

    private static void await(MapStorageExecutor storage, CompletableFuture<?> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) { storage.pump(); Thread.sleep(1); }
        assertTrue(future.isDone(), "asynchronous operation timed out");
    }
}
