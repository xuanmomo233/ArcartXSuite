package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;

class ModuleShutdownCompletionTest {
    @Test
    void globalDetachFailureStillWaitsForEveryModuleAndRepeatedShutdownCannotSkipTheWait() throws Exception {
        var registry = mock(ModuleRegistry.class, CALLS_REAL_METHODS);
        doNothing().when(registry).unloadAll();
        var factory = mock(GlobalBridgeFactory.class);
        doThrow(new NoClassDefFoundError("optional attribute backend")).when(factory).detachGlobalListeners();
        var factoryField = ModuleRegistry.class.getDeclaredField("globalBridgeFactory");
        factoryField.setAccessible(true); factoryField.set(registry, factory);
        var retired = new CompletableFuture<Void>();
        var map = new java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<Void>>();
        map.put("market", retired);
        var retiringField = ModuleRegistry.class.getDeclaredField("retiringModules");
        retiringField.setAccessible(true); retiringField.set(registry, map);
        var first = registry.shutdown().toCompletableFuture();
        first.cancel(false);
        var second = registry.shutdown().toCompletableFuture();
        assertFalse(retired.isCancelled());
        assertFalse(second.isDone());
        retired.complete(null);
        assertThrows(java.util.concurrent.CompletionException.class, second::join);
        verify(registry, times(1)).unloadAll();
    }

    @Test
    void aMissingDependencyDuringStopStillClearsTasksAndRegistrations() throws Exception {
        var module = new AbstractAXSModule() {
            @Override public ModuleDescriptor descriptor() { return ModuleDescriptor.builder("lab").build(); }
            @Override protected void loadConfiguration(File file) { }
            @Override protected void startService() { }
            @Override protected void stopService() { throw new NoClassDefFoundError("simulated removed dependency"); }
        };
        var context = mock(ModuleContext.class);
        when(context.logger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.cancelScheduledTasks()).thenReturn(CompletableFuture.completedFuture(null));
        var injected = AbstractAXSModule.class.getDeclaredField("context");
        injected.setAccessible(true);
        injected.set(module, context);
        var lifecycle = AbstractAXSModule.class.getDeclaredField("lifecycleOpen");
        lifecycle.setAccessible(true);
        lifecycle.setBoolean(module, true);
        assertDoesNotThrow(module::onDisable);
        verify(context).cancelScheduledTasks();
        verify(context).unregisterDynamicCommands();
        verify(context).unregisterListeners();
        verify(context.expansionRegistry()).unregisterAll();
        assertTrue(module.shutdownCompletion().toCompletableFuture().isDone());
    }
    @Test
    void unloadReturnsBeforeBackgroundExitAndKeepsTheLoaderAvailable() throws Exception {
        var stopped = new CompletableFuture<Void>();
        var module = mock(AXSModule.class);
        when(module.shutdownCompletion()).thenReturn(stopped);
        var loader = new TestLoader();
        closeLoader(module, loader);
        assertFalse(loader.closed);
        stopped.complete(null);
        assertTrue(loader.closed);
    }

    @Test
    void failedRetirementStillReleasesTheLoader() throws Exception {
        var stopped = new CompletableFuture<Void>();
        var module = mock(AXSModule.class);
        when(module.shutdownCompletion()).thenReturn(stopped);
        var loader = new TestLoader();
        var registry = closeLoader(module, loader);
        var originalWaiter = registry.retirement("title").toCompletableFuture();
        stopped.completeExceptionally(new IllegalStateException("simulated close failure"));
        assertTrue(loader.closed);
        assertThrows(java.util.concurrent.CompletionException.class, originalWaiter::join);
        assertDoesNotThrow(() -> registry.retirement("title").toCompletableFuture().join());
    }

    @Test
    void overlappingReloadsKeepEveryOutstandingGeneration() {
        var module = new TrackingModule();
        var first = new CompletableFuture<Void>();
        var second = new CompletableFuture<Void>();
        module.track(first);
        module.track(second);
        var completed = module.shutdownCompletion().toCompletableFuture();
        completed.cancel(false);
        assertFalse(first.isCancelled());
        second.complete(null);
        assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
        first.complete(null);
        assertTrue(module.shutdownCompletion().toCompletableFuture().isDone());
    }

    @Test
    void anEarlierRetirementFailureRemainsVisibleAfterOtherResourcesExit() {
        var module = new TrackingModule();
        var failed = new CompletableFuture<Void>();
        var waiting = new CompletableFuture<Void>();
        module.track(failed);
        module.track(waiting);
        failed.completeExceptionally(new IllegalStateException("resource close failed"));
        var completion = module.shutdownCompletion().toCompletableFuture();
        assertFalse(completion.isDone());
        waiting.complete(null);
        assertThrows(java.util.concurrent.CompletionException.class, completion::join);
        assertThrows(java.util.concurrent.CompletionException.class,
            () -> module.shutdownCompletion().toCompletableFuture().join());
    }

    private ModuleRegistry closeLoader(AXSModule module, TestLoader loader) throws Exception {
        var loaded = new LoadedModule(ModuleDescriptor.builder("title").name("Title").build(),
            module, loader, (File) null);
        var registry = mock(ModuleRegistry.class, CALLS_REAL_METHODS);
        var retiring = ModuleRegistry.class.getDeclaredField("retiringModules");
        retiring.setAccessible(true);
        retiring.set(registry, new java.util.concurrent.ConcurrentHashMap<>());
        var close = ModuleRegistry.class.getDeclaredMethod("closeClassLoader", LoadedModule.class);
        close.setAccessible(true);
        close.invoke(registry, loaded);
        return registry;
    }

    private static final class TestLoader extends ClassLoader implements Closeable {
        boolean closed;
        @Override public void close() { closed = true; }
    }

    private static final class TrackingModule extends AbstractAXSModule {
        void track(CompletableFuture<Void> stopped) { trackShutdown(stopped); }
        @Override public ModuleDescriptor descriptor() { return ModuleDescriptor.builder("title").build(); }
        @Override protected void loadConfiguration(File file) { }
        @Override protected void startService() { }
        @Override protected void stopService() { }
    }
}
