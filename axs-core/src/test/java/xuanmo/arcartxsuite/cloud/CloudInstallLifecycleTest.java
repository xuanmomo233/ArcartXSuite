package xuanmo.arcartxsuite.cloud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.SuiteCoreImpl;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.module.ModuleRegistry.CloudModuleSnapshot;

class CloudInstallLifecycleTest {
    @Test
    void replacementWaitsForTheOldRuntimeAndOwnsItsBuffersUntilLoadFinishes() throws Exception {
        var lab = new Lab();
        var oldExit = new CompletableFuture<Void>();
        var newEnable = new CompletableFuture<Boolean>();
        when(lab.registry.retirement("cloudlab")).thenReturn(oldExit);
        when(lab.registry.loadCloudModule(eq("cloudlab"),same(lab.payload.jar),same(lab.payload.seed))).thenReturn(newEnable);
        var result = lab.install();
        lab.dispatch.next();
        verify(lab.registry).unloadModule("cloudlab");
        verify(lab.registry, never()).loadCloudModule(anyString(),any(),any());
        assertTrue(lab.dispatch.queued.isEmpty());
        oldExit.complete(null);
        lab.dispatch.next();
        assertFalse(result.isDone());
        assertFalse(Arrays.equals(new byte[lab.payload.jar.length], lab.payload.jar));
        newEnable.complete(true);
        assertTrue(result.get(2, TimeUnit.SECONDS));
        lab.stopAndAssertWiped();
    }

    @Test
    void rollbackWaitsForTheFailedNewRuntimeBeforeRestoringTheSnapshot() throws Exception {
        var lab = new Lab();
        var failedExit = new CompletableFuture<Void>();
        when(lab.registry.retirement("cloudlab")).thenReturn(CompletableFuture.completedFuture(null),failedExit);
        when(lab.registry.loadCloudModule(eq("cloudlab"),same(lab.payload.jar),same(lab.payload.seed))).thenReturn(CompletableFuture.completedFuture(false));
        when(lab.registry.loadCloudModule(eq("cloudlab"),same(lab.snapshot.jarBytes()),same(lab.snapshot.moduleSeed()))).thenReturn(CompletableFuture.completedFuture(true));
        var result = lab.install();
        lab.dispatch.next(); lab.dispatch.next();
        verify(lab.registry, never()).loadCloudModule(eq("cloudlab"),same(lab.snapshot.jarBytes()),any());
        assertFalse(result.isDone());
        failedExit.complete(null);
        lab.dispatch.next();
        assertFalse(result.get(2, TimeUnit.SECONDS));
        verify(lab.registry).loadCloudModule(eq("cloudlab"),same(lab.snapshot.jarBytes()),any());
        lab.stopAndAssertWiped();
    }

    @Test
    void hostStopDuringRetirementCancelsReplacementAndStillWipesTheSnapshot() throws Exception {
        var lab = new Lab();
        var oldExit = new CompletableFuture<Void>();
        when(lab.registry.retirement("cloudlab")).thenReturn(oldExit);
        var result = lab.install(); lab.dispatch.next();
        var stopped = lab.tasks.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        assertFalse(oldExit.isCancelled());
        oldExit.complete(null);
        stopped.get(2, TimeUnit.SECONDS);
        lab.dispatch.runAll();
        assertTrue(result.isCompletedExceptionally());
        verify(lab.registry, never()).loadCloudModule(anyString(),any(),any());
        lab.assertWiped();
    }

    private static class Lab {
        final CloudTasksTest.Manual dispatch = new CloudTasksTest.Manual();
        final CloudTasks tasks = new CloudTasks(dispatch);
        final CloudPayload payload = CloudTasksTest.payload();
        final ModuleRegistry registry = mock(ModuleRegistry.class);
        final CloudModuleService service = mock(CloudModuleService.class,CALLS_REAL_METHODS);
        final CloudModuleSnapshot snapshot = new CloudModuleSnapshot(new byte[]{4,5,6},new byte[32]);
        final AtomicBoolean loaded = new AtomicBoolean(true);

        Lab() throws Exception {
            Arrays.fill(snapshot.moduleSeed(),(byte)9);
            set("tasks",tasks); set("registry",registry); set("plugin",mock(SuiteCoreImpl.class));
            when(registry.validateCloudModule(eq("cloudlab"),same(payload.jar),same(payload.seed))).thenReturn(true);
            when(registry.createSnapshot("cloudlab")).thenReturn(snapshot);
            when(registry.isModuleLoaded("cloudlab")).thenAnswer(call -> loaded.get());
            when(registry.unloadModule("cloudlab")).thenAnswer(call -> { loaded.set(false); return true; });
        }

        CompletableFuture<Boolean> install() {
            return tasks.request(() -> tasks.withCleanup(() -> {
                try {
                    var method = CloudModuleService.class.getDeclaredMethod("installUpdate",String.class,CloudPayload.class);
                    method.setAccessible(true);
                    return (CompletableFuture<Boolean>) method.invoke(service,"cloudlab",payload);
                } catch (Exception error) { return CompletableFuture.failedFuture(error); }
            },payload::close));
        }

        void stopAndAssertWiped() throws Exception { tasks.shutdown().toCompletableFuture().get(2,TimeUnit.SECONDS); assertWiped(); }
        void assertWiped() {
            CloudTasksTest.assertWiped(payload);
            assertArrayEquals(new byte[3],snapshot.jarBytes());
            assertArrayEquals(new byte[32],snapshot.moduleSeed());
        }
        void set(String name,Object value) throws Exception { var field=CloudModuleService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value); }
    }
}
