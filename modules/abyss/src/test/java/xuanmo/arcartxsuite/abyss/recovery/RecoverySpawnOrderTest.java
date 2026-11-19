package xuanmo.arcartxsuite.abyss.recovery;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration.RecoverySection;
import xuanmo.arcartxsuite.abyss.links.AbyssLinks;

class RecoverySpawnOrderTest {
    @TempDir Path directory;

    @Test void aLeadingSpawnDoesNotConsumeTheMarkerIfTheModuleRetiresDuringLookup() throws Exception { verifyMarker(true, false, false); }
    @Test void anOldLookupCannotConsumeANewerRecoveryMarker() throws Exception { verifyMarker(false, true, false); }
    @Test void aPreparedSpawnClaimsAndRunsItsActionsOnce() throws Exception { verifyMarker(false, false, false); }
    @Test void aWorldChangeSkipsTheOldTeleportAndStillRestoresGameMode() throws Exception { verifyMarker(false, false, true); }

    private void verifyMarker(boolean retire, boolean replace, boolean switchWorld) throws Exception {
        var player = mock(Player.class); var world = mock(World.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(player.getName()).thenReturn("Player");
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        var future = new CompletableFuture<Location>();
        var essentials = mock(EssentialsQueryable.class); when(essentials.getSpawn(world)).thenReturn(future);
        var links = new AbyssLinks(new AbyssLinks.CapabilityResolver() {
            public <T> T get(Class<T> type) { return type == EssentialsQueryable.class ? type.cast(essentials) : null; }
        }, null, Logger.getAnonymousLogger(), null);
        var config = new RecoverySection(true, List.of("message before", "teleport spawn", "message after"));
        var manager = new RecoveryManager(directory.toFile(), config, Logger.getAnonymousLogger(), links);
        var marker = directory.resolve("recovery").resolve(id + ".yml");
        try (var bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var finished = new AtomicBoolean();
            manager.mark(id, "first", GameMode.SURVIVAL.name());
            manager.runIfMarked(player, () -> finished.set(true));
            assertTrue(java.nio.file.Files.exists(marker));
            assertFalse(finished.get());
            verify(player, never()).sendMessage(anyString());
            if (retire) links.close();
            if (replace) manager.mark(id, "replacement", null);
            if (switchWorld) {
                var newWorld = mock(World.class);
                when(player.getWorld()).thenReturn(newWorld);
            }
            Location point = new Location(world, 10, 70, 20); future.complete(point);
            assertEquals(!retire, finished.get());
            if (retire || replace) {
                assertTrue(java.nio.file.Files.exists(marker));
                verify(player, never()).sendMessage(anyString()); verify(player, never()).teleport(any(Location.class));
                if (replace) assertEquals("replacement", org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(marker.toFile()).getString("dungeon"));
            } else {
                assertFalse(java.nio.file.Files.exists(marker));
                var order = inOrder(player);
                order.verify(player).setGameMode(GameMode.SURVIVAL);
                order.verify(player).sendMessage("before");
                if (switchWorld) verify(player, never()).teleport(any(Location.class));
                else order.verify(player).teleport(point);
                order.verify(player).sendMessage("after");
                manager.runIfMarked(player, () -> {});
                verify(essentials, times(1)).getSpawn(world);
                verify(player, times(switchWorld ? 0 : 1)).teleport(point);
            }
        } finally { links.close(); }
    }

    @Test void actionsAfterAnAsyncSpawnWaitForTheTeleportAndRetiredLinksCannotResumeThem() throws Exception {
        verifyOrder(false);
        verifyOrder(true);
    }

    private void verifyOrder(boolean retire) throws Exception {
        var player = mock(Player.class); var world = mock(World.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(player.getName()).thenReturn("Player");
        var spawn = new CompletableFuture<Location>();
        var essentials = mock(EssentialsQueryable.class); when(essentials.getSpawn(world)).thenReturn(spawn);
        var links = new AbyssLinks(new AbyssLinks.CapabilityResolver() {
            public <T> T get(Class<T> type) { return type == EssentialsQueryable.class ? type.cast(essentials) : null; }
        }, null, Logger.getAnonymousLogger(), null);
        var config = new RecoverySection(true, List.of("player_command before", "teleport spawn", "message after"));
        var manager = new RecoveryManager(directory.toFile(), config, Logger.getAnonymousLogger(), links);
        try (var bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            manager.mark(id, "test", null);
            manager.runIfMarked(player, () -> {});
            verify(player, never()).sendMessage(anyString()); verify(player, never()).teleport(any(Location.class));
            if (retire) links.close();
            var destination = new Location(world, 10, 70, 20); spawn.complete(destination);
            if (retire) {
                verify(player, never()).sendMessage(anyString()); verify(player, never()).teleport(any(Location.class));
            } else {
                var order = inOrder(player); order.verify(player).teleport(destination); order.verify(player).sendMessage("after");
            }
        } finally { links.close(); }
    }
}
