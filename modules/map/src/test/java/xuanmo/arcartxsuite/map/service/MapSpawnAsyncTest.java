package xuanmo.arcartxsuite.map.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;

class MapSpawnAsyncTest {
    @Test void aWorldTeleportRechecksClientPermissionWhenTheSpawnArrives() throws Exception {
        var essentials = mock(EssentialsQueryable.class);
        var world = world("target"); var origin = world("origin");
        var future = new CompletableFuture<Location>(); when(essentials.getSpawn(world)).thenReturn(future);
        var service = service(essentials); var player = player(origin);
        when(service.configuration.worlds()).thenReturn(Map.of("target", mock(MapModuleConfiguration.WorldDefinition.class)));
        try (var bukkit = mockStatic(Bukkit.class); var auth = admit(player)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            bukkit.when(() -> Bukkit.getWorld("target")).thenReturn(world);
            assertTrue(teleportFromClient(service, player, "target"));
            when(player.hasPermission("arcartxsuite.map.use")).thenReturn(false);
            future.complete(new Location(world, 12, 70, 13));
            verify(player, never()).teleport(any(Location.class));
        } finally { service.storage.close(); }
    }

    @Test void aNewerWorldRequestSupersedesTheOldLookup() throws Exception {
        var essentials = mock(EssentialsQueryable.class); var first = world("first"); var second = world("second");
        var firstResult = new CompletableFuture<Location>(); var secondResult = new CompletableFuture<Location>();
        when(essentials.getSpawn(first)).thenReturn(firstResult); when(essentials.getSpawn(second)).thenReturn(secondResult);
        var service = service(essentials); var player = player(world("origin"));
        when(service.configuration.worlds()).thenReturn(Map.of("first", mock(MapModuleConfiguration.WorldDefinition.class), "second", mock(MapModuleConfiguration.WorldDefinition.class)));
        try (var bukkit = mockStatic(Bukkit.class); var auth = admit(player)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            bukkit.when(() -> Bukkit.getWorld("first")).thenReturn(first); bukkit.when(() -> Bukkit.getWorld("second")).thenReturn(second);
            assertTrue(teleportFromClient(service, player, "first")); assertTrue(teleportFromClient(service, player, "second"));
            firstResult.complete(new Location(first, 1, 70, 2));
            verify(player, never()).teleport(any(Location.class));
            var target = new Location(second, 3, 70, 4); secondResult.complete(target);
            verify(player, times(1)).teleport(target);
        } finally { service.storage.close(); }
    }

    @Test void anotherTeleportCancelsAPendingWorldSpawnLookup() throws Exception {
        var essentials = mock(EssentialsQueryable.class); var target = world("target"); var origin = world("origin");
        var future = new CompletableFuture<Location>(); when(essentials.getSpawn(target)).thenReturn(future);
        var service = service(essentials); var player = player(origin);
        when(service.configuration.worlds()).thenReturn(Map.of("target", mock(MapModuleConfiguration.WorldDefinition.class)));
        try (var bukkit = mockStatic(Bukkit.class); var auth = admit(player)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            bukkit.when(() -> Bukkit.getWorld("target")).thenReturn(target);
            assertTrue(teleportFromClient(service, player, "target"));
            service.onPlayerTeleport(new org.bukkit.event.player.PlayerTeleportEvent(player, new Location(origin, 0, 70, 0), new Location(origin, 10, 70, 10)));
            future.complete(new Location(target, 1, 70, 2));
            verify(player, never()).teleport(any(Location.class));
        } finally { service.storage.close(); }
    }

    @Test void aNewAdmissionCannotExecuteTheOldWorldSpawnLookup() throws Exception {
        var essentials = mock(EssentialsQueryable.class);
        var target = world("target");
        var future = new CompletableFuture<Location>();
        when(essentials.getSpawn(target)).thenReturn(future);
        var service = service(essentials);
        var player = player(world("origin"));
        UUID id = player.getUniqueId();
        UUID connection = UUID.randomUUID();
        when(service.configuration.worlds()).thenReturn(Map.of("target", mock(MapModuleConfiguration.WorldDefinition.class)));
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(admission(id, connection, 0));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(() -> Bukkit.getWorld("target")).thenReturn(target);
            assertTrue(teleportFromClient(service, player, "target"));
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(admission(id, connection, 1));
            future.complete(new Location(target, 1, 70, 2));
            verify(player, never()).teleport(any(Location.class));
        } finally { service.storage.close(); }
    }

    private MockedStatic<AuthAccess> admit(Player player) {
        UUID id = player.getUniqueId();
        var auth = mockStatic(AuthAccess.class);
        auth.when(() -> AuthAccess.snapshot(id)).thenReturn(admission(id, UUID.randomUUID(), 0));
        return auth;
    }

    private AuthSnapshot admission(UUID id, UUID connection, long generation) {
        return new AuthSnapshot(id, connection, generation, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
    }

    private World world(String name) { var world = mock(World.class); when(world.getName()).thenReturn(name); return world; }
    private Player player(World origin) {
        var player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(origin); when(player.isOnline()).thenReturn(true); when(player.hasPermission("arcartxsuite.map.use")).thenReturn(true);
        return player;
    }
    private boolean teleportFromClient(MapService service, Player player, String world) throws Exception {
        var method = MapService.class.getDeclaredMethod("teleportToWorld", Player.class, String.class, boolean.class); method.setAccessible(true);
        return (Boolean)method.invoke(service, player, world, true);
    }

    @Test void anAsynchronousSpawnIsFilteredByWorldAndCannotReplyAfterMapStops() {
        var essentials = mock(EssentialsQueryable.class);
        var world = mock(World.class); when(world.getName()).thenReturn("world");
        var fallback = new Location(world, 1, 70, 3); when(world.getSpawnLocation()).thenReturn(fallback);
        var future = new CompletableFuture<Location>(); when(essentials.getSpawn(world)).thenReturn(future);
        var service = service(essentials);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var result = new AtomicReference<Location>();
            service.resolveWorldSpawn(world, result::set);
            assertNull(result.get());
            var elsewhere = mock(World.class); when(elsewhere.getName()).thenReturn("other");
            future.complete(new Location(elsewhere, 8, 70, 9));
            assertSame(fallback, result.get());
            var late = new CompletableFuture<Location>(); when(essentials.getSpawn(world)).thenReturn(late);
            result.set(null); service.resolveWorldSpawn(world, result::set);
            service.storage.close(); late.complete(fallback);
            assertNull(result.get());
        } finally { service.storage.close(); }
    }

    @Test void snapshotBuildersShareOnePendingSpawnReadPerWorld() {
        var essentials = mock(EssentialsQueryable.class);
        var world = mock(World.class); when(world.getName()).thenReturn("world");
        var future = new CompletableFuture<Location>(); when(essentials.getSpawn(world)).thenReturn(future);
        var service = service(essentials);
        var definition = mock(MapModuleConfiguration.WorldDefinition.class); when(definition.id()).thenReturn("world");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            for (int i = 0; i < 48; i++) assertNull(service.resolveWorldCenter(definition));
            verify(essentials, times(1)).getSpawn(world);
            var spawn = new Location(world, 12, 70, 13); future.complete(spawn);
            for (int i = 0; i < 48; i++) assertEquals(spawn, service.resolveWorldCenter(definition));
            verify(essentials, times(1)).getSpawn(world);
        } finally { service.storage.close(); }
    }

    private MapService service(EssentialsQueryable essentials) {
        var config = mock(MapModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(config.client().packetId()).thenReturn("map");
        return new MapService(null, Logger.getAnonymousLogger(), null, config, null, null,
            "map", "hud", null, null, null, null, null, null, null, () -> essentials, null, null);
    }
}
