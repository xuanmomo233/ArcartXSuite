package xuanmo.arcartxsuite.map.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigationHandler;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;

class MapExternalLifecycleTest {
    @Test
    void externalChangesRenderOnlyOncePerTickAndOnlyForOpenViews(@TempDir Path folder) {
        var fixture = new Fixture(folder);
        try (var bukkit = mockStatic(Bukkit.class)) {
            fixture.bindBukkit(bukkit);
            when(fixture.bridge.isUiOpen(fixture.player, "map_menu")).thenReturn(true);
            fixture.service.upsertExternalTarget(fixture.player, point("quest", "questgps", 1), false);
            fixture.service.upsertExternalTarget(fixture.player, point("quest", "questgps", 2), false);
            fixture.service.upsertExternalTarget(fixture.player, point("other", "questgps", 3), false);
            assertEquals(1, fixture.queued.size());
            verify(fixture.bridge, never()).sendPacket(any(), anyString(), anyString(), any());
            fixture.queued.removeFirst().run();
            verify(fixture.bridge).sendPacket(eq(fixture.player), eq("map_menu"), eq("update"), any());
            verify(fixture.bridge, never()).sendPacket(eq(fixture.player), eq("map_hud"), anyString(), any());
            assertEquals(2.0, fixture.service.exportExternalRuntime().externalTargets().get(fixture.id).get("quest").x());

            fixture.service.reconcileExternalTargets(fixture.player, "questgps",
                List.of(point("quest", "questgps", 2), point("other", "questgps", 3)));
            assertTrue(fixture.queued.isEmpty());
            when(fixture.bridge.isUiOpen(fixture.player, "map_menu")).thenReturn(false);
            fixture.service.removeExternalTarget(fixture.player, "questgps", "quest");
            fixture.queued.removeFirst().run();
            verify(fixture.bridge, times(1)).sendPacket(any(), anyString(), anyString(), any());
        }
    }

    @Test
    void readinessIsIndependentOfCarriedDataAndRestoresOnlyRunningSources(@TempDir Path folder) {
        var fixture = new Fixture(folder);
        when(fixture.bus.hasSubscribers("axs.map.ready")).thenReturn(true);
        when(fixture.plugin.isEnabled()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            fixture.bindBukkit(bukkit);
            fixture.service.publishReady();
            var ordered = inOrder(fixture.bus, fixture.bridge);
            ordered.verify(fixture.bus).publish("axs.map.ready", fixture.player, Map.of());
            ordered.verify(fixture.bridge).openUi(fixture.player, "map_hud");

            var retired = mock(MapNavigationHandler.class);
            var active = mock(MapNavigationHandler.class);
            when(active.isActive()).thenReturn(true);
            fixture.service.registerNavigationSource(fixture.plugin, "retired", retired);
            fixture.service.registerNavigationSource(fixture.plugin, "active", active);
            fixture.service.upsertExternalTarget(fixture.player, point("old", "retired", 1), false);
            fixture.service.upsertExternalTarget(fixture.player, point("live", "active", 2), false);

            MapService replacement = fixture.createMap();
            replacement.importExternalRuntime(fixture.service.exportExternalRuntime());
            var restored = replacement.exportExternalRuntime();
            assertEquals(Set.of("active"), restored.navigationSources().keySet());
            assertEquals(Set.of("live"), restored.externalTargets().get(fixture.id).keySet());
            verify(fixture.bus, times(1)).publish(eq("axs.map.ready"), eq(fixture.player), any());
            replacement.publishReady();
            verify(fixture.bus, times(2)).publish(eq("axs.map.ready"), eq(fixture.player), any());
        }
    }

    private static MapExternalTargetSpec point(String id, String source, double x) {
        return new MapExternalTargetSpec(id, source, "world", id, "", x, 64, 0, null, true, 0);
    }

    private static final class Fixture {
        private final Logger logger = Logger.getAnonymousLogger();
        private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        private final EventBusCapability bus = mock(EventBusCapability.class);
        private final JavaPlugin plugin = mock(JavaPlugin.class);
        private final Player player = mock(Player.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final UUID id = UUID.randomUUID();
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        private final MapService service;

        Fixture(Path folder) {
            when(plugin.getDataFolder()).thenReturn(folder.toFile());
            when(player.isOnline()).thenReturn(true);
            when(player.getUniqueId()).thenReturn(id);
            var world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                queued.add(call.getArgument(1));
                return mock(BukkitTask.class);
            });
            service = createMap();
            service.cacheData(id, new MapService.CachedPlayerMapData(Set.of(), List.of(), List.of()));
        }

        MapService createMap() {
            var config = MapModuleConfiguration.load(new YamlConfiguration(), logger);
            return new MapService(plugin, logger, null, config, null, bridge,
                "map_menu", "map_hud", null, null, null, mock(WaypointBridgeAPI.class), null, null, () -> bus, null, null, null);
        }

        void bindBukkit(org.mockito.MockedStatic<Bukkit> bukkit) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        }
    }
}
