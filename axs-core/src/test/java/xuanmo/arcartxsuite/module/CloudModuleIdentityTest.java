package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

class CloudModuleIdentityTest {
    @Test
    void anotherModulesArchiveIsRejectedBeforeBridgesOrTheOldRuntimeAreTouched() throws Exception {
        var registry = mock(ModuleRegistry.class,CALLS_REAL_METHODS);
        var buffer = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(buffer)) {
            jar.putNextEntry(new JarEntry("module.yml"));
            jar.write("id: other\nname: Other\nversion: '1'\nmain: lab.Other\napi-version: '3.9'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        byte[] data = buffer.toByteArray();
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getConsoleSender).thenReturn(mock(org.bukkit.command.ConsoleCommandSender.class));
            assertFalse(registry.validateCloudModule("cloudlab",data,new byte[32]));
            assertFalse(registry.loadCloudModule("cloudlab",data,new byte[32]).toCompletableFuture().join());
        }
        verify(registry,never()).unloadModule(anyString());
    }

    @Test
    void aMissingModuleKeyIsNotReplacedWithTheHostsRootKey() {
        var registry = mock(ModuleRegistry.class,CALLS_REAL_METHODS);
        assertFalse(registry.loadCloudModule("cloudlab",new byte[0],null).toCompletableFuture().join());
        assertFalse(registry.validateCloudModule("cloudlab",new byte[0],new byte[31]));
    }
}
