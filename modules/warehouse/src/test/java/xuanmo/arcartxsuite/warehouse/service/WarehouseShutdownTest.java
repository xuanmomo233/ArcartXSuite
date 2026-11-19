package xuanmo.arcartxsuite.warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;

class WarehouseShutdownTest {
    @TempDir Path directory;

    @Test
    void materialReceiptsDrainWithoutBlockingTheCaller() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var materials = new WarehouseMaterials(mock(WarehouseService.class), plugin, directory.toFile(), false);
            var queue = (ModuleExecutor) field(materials, "queue");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var receipts = new AtomicInteger();
            queue.execute(() -> { entered.countDown(); await(release); });
            queue.execute(receipts::incrementAndGet);
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                long start = System.nanoTime();
                materials.close();
                assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
                assertFalse(materials.stopped().toCompletableFuture().isDone());
            } finally { release.countDown(); }
            materials.stopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, receipts.get());
        }
    }

    @Test
    void leasesReleaseOffThreadOnlyAfterAdmittedDatabaseWorkFinishes() throws Exception {
        var repository = mock(WarehouseRepository.class);
        var service = service(repository);
        var lease = new WarehouseRepository.SharedLease(UUID.randomUUID(), UUID.randomUUID(), "owner", "node");
        leases(service).put("shared", lease);
        var background = (ModuleExecutor) field(service, "background");
        var workEntered = new CountDownLatch(1);
        var workRelease = new CountDownLatch(1);
        var leaseEntered = new CountDownLatch(1);
        var leaseRelease = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        doAnswer(call -> {
            thread.set(Thread.currentThread().getName());
            leaseEntered.countDown();
            await(leaseRelease);
            return null;
        }).when(repository).releaseSharedLease("shared", lease.player(), lease.token());
        background.execute(() -> { workEntered.countDown(); await(workRelease); });
        try {
            assertTrue(workEntered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            service.shutdown();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).releaseSharedLease(anyString(), any(), any());
            workRelease.countDown();
            assertTrue(leaseEntered.await(2, TimeUnit.SECONDS));
            assertEquals("AXS-Warehouse-Close", thread.get());
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        } finally { workRelease.countDown(); leaseRelease.countDown(); service.shutdown(); }
        service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
        verify(repository, times(1)).releaseSharedLease("shared", lease.player(), lease.token());
    }

    @Test
    void activeHeartbeatFinishesBeforeTheFinalLeaseRelease() throws Exception {
        var repository = mock(WarehouseRepository.class);
        var service = service(repository);
        var active = WarehouseService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        var lease = new WarehouseRepository.SharedLease(UUID.randomUUID(), UUID.randomUUID(), "owner", "node");
        leases(service).put("shared", lease);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.renewSharedLease(eq("shared"), eq(lease.player()), eq(lease.token()), anyLong())).thenAnswer(call -> {
            entered.countDown(); await(release); return true;
        });
        var heartbeat = CompletableFuture.runAsync((Runnable) field(service, "leaseRenewal"));
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(repository, never()).releaseSharedLease(anyString(), any(), any());
        } finally { release.countDown(); service.shutdown(); }
        heartbeat.get(2, TimeUnit.SECONDS);
        service.databaseStopped().toCompletableFuture().get(2, TimeUnit.SECONDS);
        verify(repository).releaseSharedLease("shared", lease.player(), lease.token());
    }

    private WarehouseService service(WarehouseRepository repository) {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartX-Suite");
        return new WarehouseService(plugin, Logger.getAnonymousLogger(), null, null, null, null,
            mock(WarehouseModuleConfiguration.class), repository, null, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, WarehouseRepository.SharedLease> leases(WarehouseService service) throws Exception {
        return (Map<String, WarehouseRepository.SharedLease>) field(service, "sharedEditLocks");
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
}
