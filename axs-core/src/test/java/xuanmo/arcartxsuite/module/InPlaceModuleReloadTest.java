package xuanmo.arcartxsuite.module;

import java.util.LinkedHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InPlaceModuleReloadTest {
    @Test void failedInPlaceReloadKeepsRegistrationsAndTheActiveModule() throws Exception {
        var module = mock(AXSModule.class);
        when(module.reloadsInPlace()).thenReturn(true);
        var loaded = new LoadedModule(ModuleDescriptor.builder("eventpacket").name("EventPacket").build(),
            module, getClass().getClassLoader(), (java.io.File) null);
        loaded.setEnabled(true);
        var context = mock(DefaultModuleContext.class);
        loaded.setContext(context);
        var registry = mock(ModuleRegistry.class, CALLS_REAL_METHODS);
        var modules = new LinkedHashMap<String, LoadedModule>();
        modules.put("eventpacket", loaded);
        var field = ModuleRegistry.class.getDeclaredField("modules");
        field.setAccessible(true);
        field.set(registry, modules);
        when(module.onReload()).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
            new IllegalArgumentException("Invalid replacement")), java.util.concurrent.CompletableFuture.completedFuture(null));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertFalse(registry.reloadModule("eventpacket").toCompletableFuture().join());
            assertSame(loaded, modules.get("eventpacket"));
            assertTrue(loaded.isEnabled());
            verifyNoInteractions(context);
            verify(module, never()).onDisable();
            assertTrue(registry.reloadModule("eventpacket").toCompletableFuture().join());
            assertSame(loaded, modules.get("eventpacket"));
            verifyNoInteractions(context);
            verify(module, times(2)).onReload();
        }
    }
}
