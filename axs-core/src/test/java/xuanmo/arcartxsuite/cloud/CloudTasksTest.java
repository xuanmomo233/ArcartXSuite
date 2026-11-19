package xuanmo.arcartxsuite.cloud;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CloudTasksTest {
    @Test
    void transportRegisteredWhileStoppingIsCancelledWithoutReleasingItsParentEarly() throws Exception {
        var tasks = new CloudTasks(new Manual());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transport = new CompletableFuture<String>();
        var starting = CompletableFuture.supplyAsync(() -> tasks.request(() -> {
            entered.countDown(); await(release); return tasks.transfer(transport);
        }));
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        var stopped = tasks.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        release.countDown();
        assertTrue(starting.get(2,TimeUnit.SECONDS).isCompletedExceptionally());
        stopped.get(2,TimeUnit.SECONDS);
        assertTrue(transport.isCancelled());
    }

    @Test
    void stoppingCompletesCancelledRetriesAndNeverRunsTheirSupplier() throws Exception {
        var dispatch = new Manual();
        var tasks = new CloudTasks(dispatch);
        var calls = new AtomicInteger();
        var retry = tasks.request(() -> tasks.delay(40).thenApply(ignored -> calls.incrementAndGet()));
        tasks.shutdown().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertTrue(retry.isCompletedExceptionally());
        dispatch.runAll();
        assertEquals(0, calls.get());
        assertEquals(1, dispatch.cancellations.get());
    }

    @Test
    void cancellingThePublicFutureDoesNotAbandonRunningWork() throws Exception {
        var tasks = new CloudTasks(new Manual());
        var actual = new CompletableFuture<Boolean>();
        var publicResult = tasks.request(() -> actual);
        publicResult.cancel(false);
        var stopped = tasks.shutdown().toCompletableFuture();
        assertFalse(actual.isCancelled());
        assertFalse(stopped.isDone());
        actual.complete(true);
        stopped.get(2, TimeUnit.SECONDS);
    }

    @Test
    void stoppingCancelsTransportButDoesNotInvokeItsBusinessContinuation() throws Exception {
        var tasks = new CloudTasks(new Manual());
        var transport = new CompletableFuture<String>();
        var calls = new AtomicInteger();
        var result = tasks.request(() -> tasks.transfer(transport).thenApply(body -> calls.incrementAndGet()));
        tasks.shutdown().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertTrue(transport.isCancelled());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(0, calls.get());
    }

    @Test
    void aQueuedLoadIsCancelledAndItsBuffersAreWipedOffThread() throws Exception {
        var dispatch = new Manual();
        var tasks = new CloudTasks(dispatch);
        var payload = payload();
        var calls = new AtomicInteger();
        var thread = new AtomicReference<String>();
        var result = tasks.request(() -> tasks.withCleanup(() -> tasks.main(calls::incrementAndGet), () -> {
            thread.set(Thread.currentThread().getName()); payload.close();
        }));
        tasks.shutdown().toCompletableFuture().get(2, TimeUnit.SECONDS);
        dispatch.runAll();
        assertEquals(0, calls.get());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("AXS-Cloud-Cleanup", thread.get());
        assertWiped(payload);
    }

    @Test
    void aHandleArrivingAfterStopIsCancelledAndCannotRunTheOldTask() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handleCancelled = new AtomicInteger();
        var captured = new AtomicReference<Runnable>();
        var tasks = new CloudTasks((action, delay, async) -> {
            captured.set(action); entered.countDown(); await(release); return handleCancelled::incrementAndGet;
        });
        var applied = new AtomicInteger();
        var scheduling = CompletableFuture.supplyAsync(() -> tasks.request(() -> tasks.main(applied::incrementAndGet)));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var stopped = tasks.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        release.countDown();
        assertTrue(scheduling.get(2, TimeUnit.SECONDS).isCompletedExceptionally());
        stopped.get(2, TimeUnit.SECONDS);
        captured.get().run();
        assertEquals(0, applied.get());
        assertEquals(1, handleCancelled.get());
    }

    @Test
    void cleanupIsPartOfRetirementAndItsFailureIsReported() throws Exception {
        var tasks = new CloudTasks(new Manual());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var result = tasks.request(() -> tasks.withCleanup(() -> CompletableFuture.completedFuture(true), () -> {
            entered.countDown(); await(release); throw new IllegalStateException("wipe failed");
        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var stopped = tasks.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        release.countDown();
        assertThrows(java.util.concurrent.ExecutionException.class, () -> stopped.get(2, TimeUnit.SECONDS));
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void admissionIsBoundedAndRejectedOperationsNeverStart() throws Exception {
        var tasks = new CloudTasks(new Manual());
        var actual = new ArrayList<CompletableFuture<Boolean>>();
        for (int i = 0; i < 128; i++) {
            var work = new CompletableFuture<Boolean>(); actual.add(work); tasks.request(() -> work);
        }
        var started = new AtomicInteger();
        assertTrue(tasks.request(() -> { started.incrementAndGet(); return CompletableFuture.completedFuture(true); }).isCompletedExceptionally());
        assertEquals(0, started.get());
        var stopped = tasks.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        actual.forEach(work -> work.complete(true));
        stopped.get(2, TimeUnit.SECONDS);
    }

    static CloudPayload payload() {
        var payload = new CloudPayload();
        payload.key = new byte[32]; payload.seed = new byte[32]; payload.jar = new byte[8192]; payload.signature = new byte[64];
        for (byte[] data : new byte[][] {payload.key,payload.seed,payload.jar,payload.signature}) Arrays.fill(data, (byte) 7);
        return payload;
    }

    static void assertWiped(CloudPayload payload) {
        for (byte[] data : new byte[][] {payload.key,payload.seed,payload.jar,payload.signature}) assertArrayEquals(new byte[data.length],data);
    }

    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(3, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }

    static class Manual implements CloudTasks.Dispatcher {
        final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
        final AtomicInteger cancellations = new AtomicInteger();
        @Override public Runnable schedule(Runnable action, long delay, boolean async) { queued.add(action); return cancellations::incrementAndGet; }
        void next() { var action = queued.poll(); assertNotNull(action); action.run(); }
        void runAll() { for (Runnable action; (action = queued.poll()) != null;) action.run(); }
    }
}
