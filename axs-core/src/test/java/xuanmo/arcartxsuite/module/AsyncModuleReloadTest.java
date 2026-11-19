package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

class AsyncModuleReloadTest {
    @Test
    void unloadDuringEnableKeepsLateInitializationFromRegisteringCommands() throws Exception {
        var module = mock(AXSModule.class);
        var activeStartup = new CompletableFuture<Boolean>();
        var preparation = new CompletableFuture<Void>();
        when(module.shutdownCompletion()).thenReturn(preparation);
        var loaded = loaded(module);
        loaded.setEnabled(false);
        loaded.setEnabling(activeStartup);
        var registry = registry(loaded);
        var finish = ModuleRegistry.class.getDeclaredMethod("finishEnable", LoadedModule.class,
            CompletableFuture.class, boolean.class, Throwable.class);
        finish.setAccessible(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            assertFalse(registry.isModuleLoaded("title"));
            assertTrue(registry.unloadModule("title"));
            assertFalse(activeStartup.join());
            assertFalse(registry.retirement("title").toCompletableFuture().isDone());
            preparation.complete(null);
            finish.invoke(registry, loaded, activeStartup, true, null);
            assertFalse(registry.isModuleLoaded("title"));
            assertFalse(registry.commandHandlerMap().containsKey("title"));
            verify(module).onDisable();
        }
    }

    @Test
    void offThreadReloadDoesNotTouchRegistrationsBeforeThePrimaryThreadRuns() throws Exception {
        var module = mock(AXSModule.class);
        when(module.onReload()).thenReturn(CompletableFuture.completedFuture(null));
        var loaded = loaded(module);
        var registry = registry(loaded);
        var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        inject(registry, "plugin", plugin);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var queued = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued.set(call.getArgument(1));
            return mock(org.bukkit.scheduler.BukkitTask.class);
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            var result = registry.reloadModule("title").toCompletableFuture();
            assertFalse(result.isDone());
            assertTrue(loaded.isEnabled());
            verify(module, never()).onReload();
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            queued.get().run();
            assertTrue(result.join());
            verify(module).onReload();
        }
    }
    @Test
    void timingOutALoadDoesNotRemoveItsLiveRetirementFenceOrAccumulateWaiters() throws Exception {
        var module = mock(AXSModule.class);
        var stopped = new CompletableFuture<Void>();
        when(module.shutdownCompletion()).thenReturn(stopped);
        var registry = registry(loaded(module));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            assertTrue(registry.unloadModule("title"));
            var request = registry.loadModuleById("title").toCompletableFuture();
            var field = ModuleRegistry.class.getDeclaredField("pendingLoads");
            field.setAccessible(true);
            var pending = (java.util.Map<?, ?>) field.get(registry);
            ((CompletableFuture<?>) pending.get("title")).orTimeout(1, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> request.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(stopped.isDone());
            for (int i = 0; i < 1000; i++) assertThrows(java.util.concurrent.CompletionException.class,
                () -> registry.loadModuleById("title").toCompletableFuture().join());
            assertEquals(1, pending.size());
            stopped.complete(null);
            assertTrue(pending.isEmpty());
            verify(module, never()).onEnable(any());
        }
    }
    @Test
    void cancelledQueuedLoadsStayBoundedAndDoNotEnableAfterRetirement() throws Exception {
        var module = mock(AXSModule.class);
        var stopped = new CompletableFuture<Void>();
        when(module.shutdownCompletion()).thenReturn(stopped);
        var loaded = loaded(module);
        var registry = registry(loaded);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            assertTrue(registry.unloadModule("title"));
            var first = registry.loadModuleById("title").toCompletableFuture();
            var second = registry.loadModuleById("title").toCompletableFuture();
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertTrue(registry.unloadModule("title"));
            assertFalse(first.join());
            assertFalse(second.join());
            for (int i = 0; i < 1000; i++) assertFalse(registry.loadModuleById("title").toCompletableFuture().join());
            var field = ModuleRegistry.class.getDeclaredField("pendingLoads");
            field.setAccessible(true);
            assertEquals(1, ((java.util.Map<?, ?>) field.get(registry)).size());
            stopped.complete(null);
            assertTrue(((java.util.Map<?, ?>) field.get(registry)).isEmpty());
            verify(module, never()).onEnable(any());
            assertFalse(registry.isModuleLoaded("title"));
        }
    }
    @Test
    void duplicateReloadWaitsForTheSameAttemptAndDoesNotReportEarlySuccess() throws Exception {
        var module = mock(AXSModule.class);
        var finishing = new CompletableFuture<Void>();
        when(module.onReload()).thenReturn(finishing);
        var loaded = loaded(module);
        var registry = registry(loaded);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            var first = registry.reloadModule("title").toCompletableFuture();
            var second = registry.reloadModule("title").toCompletableFuture();
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertFalse(loaded.isEnabled());
            verify(module, times(1)).onReload();
            first.cancel(false);
            assertFalse(finishing.isCancelled());
            finishing.complete(null);
            assertTrue(second.join());
            assertTrue(loaded.isEnabled());
            assertTrue(registry.commandHandlerMap().containsKey("title"));
        }
    }

    @Test
    void unloadDuringReloadRejectsTheLateCompletion() throws Exception {
        var module = mock(AXSModule.class);
        var finishing = new CompletableFuture<Void>();
        when(module.onReload()).thenReturn(finishing);
        when(module.shutdownCompletion()).thenReturn(CompletableFuture.completedFuture(null));
        var loaded = loaded(module);
        var registry = registry(loaded);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            var result = registry.reloadModule("title").toCompletableFuture();
            assertTrue(registry.unloadModule("title"));
            assertFalse(result.join());
            finishing.complete(null);
            assertFalse(registry.isModuleLoaded("title"));
            assertFalse(registry.commandHandlerMap().containsKey("title"));
            verify(module).onDisable();
        }
    }

    @Test
    void disablingAnInFlightModuleInConfigurationCancelsItsReload() throws Exception {
        var module = mock(AXSModule.class);
        var finishing = new CompletableFuture<Void>();
        when(module.onReload()).thenReturn(finishing);
        when(module.shutdownCompletion()).thenReturn(CompletableFuture.completedFuture(null));
        var loaded = loaded(module);
        var registry = registry(loaded);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            var result = registry.reloadModule("title").toCompletableFuture();
            var config = new org.bukkit.configuration.file.YamlConfiguration();
            config.set("modules.title.enabled", false);
            var stopped = new java.util.ArrayList<String>();
            var failed = new java.util.ArrayList<String>();
            var stopConfigured = ModuleRegistry.class.getDeclaredMethod("stopDisabledModules",
                org.bukkit.configuration.file.YamlConfiguration.class, java.util.List.class, java.util.List.class);
            stopConfigured.setAccessible(true);
            stopConfigured.invoke(registry, config, stopped, failed);
            assertEquals(java.util.List.of("title"), stopped);
            assertTrue(failed.isEmpty());
            assertFalse(result.join());
            finishing.complete(null);
            assertFalse(registry.isModuleLoaded("title"));
        }
    }

    private LoadedModule loaded(AXSModule module) {
        var loaded = new LoadedModule(ModuleDescriptor.builder("title").name("Title").build(),
            module, getClass().getClassLoader(), (File) null);
        loaded.setEnabled(true);
        return loaded;
    }

    private ModuleRegistry registry(LoadedModule loaded) throws Exception {
        var registry = mock(ModuleRegistry.class, CALLS_REAL_METHODS);
        var modules = new LinkedHashMap<String, LoadedModule>();
        modules.put("title", loaded);
        inject(registry, "modules", modules);
        inject(registry, "commandHandlers", new LinkedHashMap<>());
        inject(registry, "retiringModules", new java.util.concurrent.ConcurrentHashMap<>());
        inject(registry, "pendingLoads", new java.util.concurrent.ConcurrentHashMap<>());
        for (String field : new String[]{"clientPacketRouter", "capabilityRegistry"}) {
            inject(registry, field, mock(ModuleRegistry.class.getDeclaredField(field).getType()));
        }
        return registry;
    }

    private void inject(Object target, String name, Object value) throws Exception {
        var field = ModuleRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
