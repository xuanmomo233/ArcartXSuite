package xuanmo.arcartxsuite.map.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;

class MapDisplayNameTest {
    @Test
    void usesWorldIdWithoutLinkingUnavailableMultiverse() {
        PluginManager plugins = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        var configuration = mock(MapModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(configuration.client().packetId()).thenReturn("map");
        var world = mock(WorldDefinition.class);
        when(world.id()).thenReturn("test_world");
        when(world.displayName()).thenReturn("");
        var service = new MapService(null, logger, null, configuration, null, null, "map", "map_hud",
            null, null, null, null);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            assertEquals("test_world", service.resolveDisplayName(world));

            verify(plugins).isPluginEnabled("Multiverse-Core");
            verifyNoInteractions(logger);
        }
    }
}
