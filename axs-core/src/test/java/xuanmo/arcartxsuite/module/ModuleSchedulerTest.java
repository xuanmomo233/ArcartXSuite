package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

class ModuleSchedulerTest {
    @Test
    void stoppingAnOwnerCancelsOnlyItsTasksAndRejectsDequeuedCallbacks() {
        var delegate = mock(SchedulerAPI.class);
        var first = mock(ScheduledTask.class);
        var second = mock(ScheduledTask.class);
        var callback = new AtomicReference<Runnable>();
        when(delegate.scheduleAtFixedRate(any(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(1));
            return first;
        }).thenReturn(second);
        var old = scope(delegate);
        var next = scope(delegate);
        var calls = new AtomicInteger();
        old.scheduleAtFixedRate(null, calls::incrementAndGet, 1, 1);
        Runnable oldCallback = callback.get();
        next.scheduleAtFixedRate(null, calls::incrementAndGet, 1, 1);
        old.close();
        old.close();
        oldCallback.run();
        assertEquals(0, calls.get());
        verify(first, times(1)).cancel();
        verify(second, never()).cancel();
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
            () -> old.scheduleOnce(null, calls::incrementAndGet, 1));
        next.close();
    }

    @Test
    void runningAsyncCallbackDelaysRetirementWithoutBlockingClose() throws Exception {
        var delegate = mock(SchedulerAPI.class);
        var callback = new AtomicReference<Runnable>();
        when(delegate.scheduleAsyncAtFixedRate(any(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        });
        var scope = scope(delegate);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        scope.scheduleAsyncAtFixedRate(null, () -> { entered.countDown(); await(release); }, 1, 1);
        var active = CompletableFuture.runAsync(callback.get());
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            var stopped = scope.close().toCompletableFuture();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(stopped.isDone());
            stopped.cancel(false);
            assertFalse(scope.close().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        active.get(2, TimeUnit.SECONDS);
        scope.close().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }

    @Test
    void registrationFinishingAfterCloseCancelsTheLateHandle() throws Exception {
        var delegate = mock(SchedulerAPI.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handle = mock(ScheduledTask.class);
        when(delegate.scheduleOnce(any(), any(), anyLong())).thenAnswer(invocation -> {
            entered.countDown();
            await(release);
            return handle;
        });
        var scope = scope(delegate);
        var registration = CompletableFuture.runAsync(() -> scope.scheduleOnce(null, () -> fail("late callback"), 1));
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(scope.close().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        registration.get(2, TimeUnit.SECONDS);
        scope.close().toCompletableFuture().get(2, TimeUnit.SECONDS);
        verify(handle, times(1)).cancel();
        assertEquals(0, scope.taskCount());
    }

    @Test
    void completedOneShotTasksDoNotAccumulateEvenWhenDispatchedInline() {
        var delegate = mock(SchedulerAPI.class);
        when(delegate.scheduleOnce(any(), any(), anyLong())).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return mock(ScheduledTask.class);
        });
        var scope = scope(delegate);
        var calls = new AtomicInteger();
        for (int i = 0; i < 1000; i++) scope.scheduleOnce(null, calls::incrementAndGet, 1);
        assertEquals(1000, calls.get());
        assertEquals(0, scope.taskCount());
        assertTrue(scope.close().toCompletableFuture().isDone());
    }

    @Test
    void failedRegistrationDoesNotKeepTheScopeAlive() {
        var delegate = mock(SchedulerAPI.class);
        when(delegate.scheduleOnce(any(), any(), anyLong())).thenThrow(new IllegalStateException("registration failed"));
        var scope = scope(delegate);
        assertThrows(IllegalStateException.class, () -> scope.scheduleOnce(null, () -> {}, 1));
        assertEquals(0, scope.taskCount());
        assertTrue(scope.close().toCompletableFuture().isDone());
    }

    @Test
    void callbackFailureStillReleasesRetirement() {
        var delegate = mock(SchedulerAPI.class);
        var callback = new AtomicReference<Runnable>();
        when(delegate.scheduleOnce(any(), any(), anyLong())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        });
        var scope = scope(delegate);
        scope.scheduleOnce(null, () -> { throw new IllegalStateException("callback failed"); }, 1);
        assertThrows(IllegalStateException.class, () -> callback.get().run());
        assertEquals(0, scope.taskCount());
        assertTrue(scope.close().toCompletableFuture().isDone());
    }

    private static ModuleScheduler scope(SchedulerAPI delegate) {
        return new ModuleScheduler(delegate, Logger.getAnonymousLogger());
    }

    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
    }
}
