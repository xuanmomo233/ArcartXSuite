package xuanmo.arcartxsuite.module;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ModuleUiExportTest {
    @TempDir Path dataDirectory;

    @Test
    void sameUiNameUsesSeparateModuleDirectoriesAndKeepsCustomFiles() throws Exception {
        String resource = "arcartx/ui/panel.yml";
        ClassLoader loader = getClass().getClassLoader();
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        ModuleRegistry registry = mock(ModuleRegistry.class);
        when(registry.openResource("menu", resource, loader))
            .thenAnswer(ignored -> stream("menu template"));
        when(registry.openResource("map", resource, loader))
            .thenAnswer(ignored -> stream("map template"));

        PluginManager pluginManager = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            var menu = context(plugin, "menu", registry, loader);
            var map = context(plugin, "map", registry, loader);

            Path menuUi = menu.exportUiResource(resource, "ui/menu/panel.yml", false, loader).toPath();
            Path mapUi = map.exportUiResource(resource, "ui/panel.yml", false, loader).toPath();
            assertEquals(dataDirectory.resolve("ui/menu/panel.yml"), menuUi);
            assertEquals(dataDirectory.resolve("ui/map/panel.yml"), mapUi);
            assertEquals(menuUi.getParent(), menu.uiFolder().toPath());
            assertFalse(Files.exists(dataDirectory.resolve("ui/panel.yml")));
            assertEquals("map template", Files.readString(mapUi));

            Files.writeString(menuUi, "custom menu");
            menu.exportUiResource(resource, "ui/menu/panel.yml", false, loader);
            assertEquals("custom menu", Files.readString(menuUi));
            menu.exportUiResource(resource, "ui/menu/panel.yml", true, loader);
            assertEquals("menu template", Files.readString(menuUi));
        }
    }

    private static DefaultModuleContext context(JavaPlugin plugin, String module, ModuleRegistry registry, ClassLoader loader) {
        return new DefaultModuleContext(plugin, module, null, null, null, null, null, null, registry, loader,
            null, null, null, null, null, mock(SchedulerAPI.class));
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
