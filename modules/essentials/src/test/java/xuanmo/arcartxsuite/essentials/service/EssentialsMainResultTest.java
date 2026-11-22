package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class EssentialsMainResultTest {
    @Test void mainRepliesAreBoundedAndRetirementCompletesThemWithoutRunningOldActions() {
        var work = new EssentialsWork(mock(JavaPlugin.class));
        var scheduler = mock(BukkitScheduler.class);
        var queued = new ArrayList<Runnable>();
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> { queued.add(call.getArgument(1)); return null; });
        var runs = new AtomicInteger();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var results = new ArrayList<CompletableFuture<Integer>>();
            for (int i = 0; i < 256; i++) results.add(work.main(runs::incrementAndGet));
            assertTrue(work.main(runs::incrementAndGet).isCompletedExceptionally());
            assertEquals(256, queued.size());
            results.get(0).cancel(false);
            assertTrue(work.main(runs::incrementAndGet).isCompletedExceptionally());
            work.close();
            for (var result : results) assertTrue(result.isCompletedExceptionally());
            queued.forEach(Runnable::run);
            assertEquals(0, runs.get());
            assertTrue(work.stopped().toCompletableFuture().isDone());
        } finally { work.close(); }
    }

    @Test void aRunningMainContinuationKeepsItsRuntimeAlive() throws Exception {
        var work = new EssentialsWork(mock(JavaPlugin.class));
        var scheduler = mock(BukkitScheduler.class);
        var queued = new ArrayList<Runnable>();
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> { queued.add(call.getArgument(1)); return null; });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var result = work.main(() -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return 42;
            });
            var active = executor.submit(queued.get(0));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            work.close();
            assertTrue(result.isCompletedExceptionally());
            assertFalse(work.stopped().toCompletableFuture().isDone());
            release.countDown(); active.get(5, TimeUnit.SECONDS);
            work.stopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); work.close(); executor.shutdownNow(); }
    }
}
