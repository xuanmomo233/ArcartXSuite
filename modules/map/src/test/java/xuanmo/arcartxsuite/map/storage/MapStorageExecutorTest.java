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
                () -> { charges.incrementAndGet(); return OK; }, () -> fail("must not publish"),
                () -> { rollbacks.incrementAndGet(); return null; }, () -> FAIL, FAIL);
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
                () -> fail("must not publish"),
                () -> { rollbackEntered.countDown(); releaseRollback.await(2, TimeUnit.SECONDS); row.set(false); return null; },
                () -> FAIL, FAIL);
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

    @Test void stoppingWithSqlInFlightReturnsBeforeRollbackAndNeedsNoMoreTicks() throws Exception {
        AtomicBoolean row = new AtomicBoolean();
        AtomicInteger charges = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread main = Thread.currentThread();
        try (var storage = new MapStorageExecutor()) {
            var result = MapUnlockTransaction.execute(storage,
                () -> { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); row.set(true); return true; },
                () -> true, () -> { charges.incrementAndGet(); return OK; },
                () -> fail("must not publish"),
                () -> { assertNotSame(main, Thread.currentThread()); row.set(false); return null; }, () -> FAIL, FAIL);
            AtomicBoolean read = new AtomicBoolean();
            var abandoned = storage.query(() -> { read.set(true); return null; });
            AtomicBoolean written = new AtomicBoolean();
            var write = storage.write(() -> { written.set(true); return null; });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                storage.close();
                assertFalse(storage.stopped().toCompletableFuture().isDone());
                assertFalse(result.isDone());
                assertTrue(abandoned.isCompletedExceptionally());
                assertTrue(storage.query(() -> 1).isCompletedExceptionally());
            } finally {
                release.countDown();
            }
            storage.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertFalse(result.join().success());
            write.join();
            assertTrue(written.get());
            assertFalse(read.get());
            assertFalse(row.get());
            assertEquals(0, charges.get());
        }
    }

    @Test void rollbackFailureDuringRetirementIsVisibleToTheHost() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            CountDownLatch nextJob = new CountDownLatch(1);
            var result = MapUnlockTransaction.execute(storage, () -> true, () -> true, () -> fail("must not charge"),
                () -> fail("must not publish"), () -> { throw new java.sql.SQLException("rollback failed"); }, () -> FAIL, FAIL);
            storage.query(() -> { nextJob.countDown(); return null; });
            assertTrue(nextJob.await(2, TimeUnit.SECONDS)); // reserve result is already waiting for the old pump
            storage.close();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> storage.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals("rollback failed", failure.getCause().getMessage());
            assertTrue(result.isCompletedExceptionally());
        }
    }

    @Test void rollbackErrorsRemainExceptionalAndDoNotReportSuccess() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            var result = MapUnlockTransaction.execute(storage, () -> true, () -> false, () -> OK,
                () -> fail("must not publish"), () -> { throw new java.sql.SQLException("rollback failed"); }, () -> FAIL, FAIL);
            await(storage, result);
            assertTrue(result.isCompletedExceptionally());
        }
    }

    @Test void failedPublicationAfterPaymentDoesNotReleaseTheReservation() throws Exception {
        try (var storage = new MapStorageExecutor()) {
            var charged = new AtomicBoolean();
            var rolledBack = new AtomicBoolean();
            var result = MapUnlockTransaction.execute(storage, () -> true, () -> true,
                () -> { charged.set(true); return OK; },
                () -> {
                    assertTrue(charged.get());
                    throw new java.sql.SQLException("publication failed");
                },
                () -> { rolledBack.set(true); return null; }, () -> FAIL, FAIL);
            await(storage, result);
            assertTrue(result.isCompletedExceptionally());
            assertTrue(charged.get());
            assertFalse(rolledBack.get());
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
