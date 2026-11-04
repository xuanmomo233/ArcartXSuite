package xuanmo.arcartxsuite.conversation.cinematic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicServiceTest {
    @TempDir Path directory;

    @Test
    void stopDuringCameraRestoresPlayerAndInvalidatesStaleTick() throws Exception {
        Files.writeString(directory.resolve("scene.yml"), """
            cinematics:
              intro:
                duration-ms: 1000
                timeline:
                  - type: camera_path
                    at-ms: 0
                    world: world
                    duration-ms: 1000
                    points:
                      - { x: 0, y: 64, z: 0, yaw: 170 }
                      - { x: 10, y: 64, z: 0, yaw: -170 }
            """);
        Logger logger = Logger.getAnonymousLogger();
        var config = CinematicConfiguration.loadFromDirectory(directory.toFile(), logger);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        var task = mock(BukkitTask.class);
        var callback = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(eq(plugin), callback.capture(), eq(1L), eq(1L))).thenReturn(task);
        var world = mock(World.class);
        var player = mock(Player.class);
        var original = new Location(world, 50, 70, 12);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(original);
        when(player.teleport(any(Location.class))).thenReturn(true);
        var service = new CinematicService(plugin, logger, config, null, null, null, null, null);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertTrue(service.play(player, "intro"));
            Runnable tick = callback.getValue();
            for (int i = 0; i <= 10; i++) tick.run();
            verify(player).teleport(new Location(world, 5, 64, 0, 180, 0));

            service.stop(player);
            verify(task).cancel();
            verify(player).teleport(original);
            assertFalse(service.isPlaying(player));
            clearInvocations(player);
            tick.run();
            verify(player, never()).teleport(any(Location.class));
        }
    }

    @Test
    void invalidCueRejectsSequenceWithoutDroppingOtherSequences() throws Exception {
        Files.writeString(directory.resolve("scene.yml"), """
            cinematics:
              broken:
                duration-ms: 1000
                timeline:
                  - { at-ms: 0, type: unknown }
              valid:
                duration-ms: 1000
                timeline:
                  - { at-ms: 500, type: action_bar, message: later }
                  - { at-ms: 0, type: title, title: first }
                  - { at-ms: 0, type: action_bar, message: second }
            """);
        var config = CinematicConfiguration.loadFromDirectory(directory.toFile(), Logger.getAnonymousLogger());
        assertNull(config.getSequence("broken"));
        var cues = config.getSequence("valid").cues();
        assertEquals(java.util.List.of(0L, 0L, 10L), cues.stream().map(CinematicSequence.Cue::tick).toList());
        assertEquals("title", cues.get(0).action().type());
        assertEquals("action_bar", cues.get(1).action().type());
    }

    @Test
    void rehearsalRebuildsActorAtSeekPositionAndSkipsProductionCommand() throws Exception {
        Files.writeString(directory.resolve("scene.yml"), """
            cinematics:
              intro:
                duration-ms: 1000
                timeline:
                  - { at-ms: 0, type: actor_place, actor: guide, world: world, x: 0, y: 64, z: 0 }
                  - at-ms: 0
                    type: actor_move
                    actor: guide
                    world: world
                    duration-ms: 1000
                    from: { x: 0, y: 64, z: 0 }
                    to: { x: 10, y: 64, z: 0 }
                  - { at-ms: 500, type: command, command: "give @s apple" }
            """);
        Logger logger = Logger.getAnonymousLogger();
        var config = CinematicConfiguration.loadFromDirectory(directory.toFile(), logger);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(mock(BukkitTask.class));
        var world = mock(World.class);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world, 50, 70, 12));
        when(player.teleport(any(Location.class))).thenReturn(true);
        var npc = mock(AdyeshachNpcBridgeAPI.class);
        when(npc.isAvailable()).thenReturn(true);
        when(npc.spawnPrivateMarker(eq(player), anyString(), any(Location.class))).thenReturn(new Object());
        when(npc.teleportMarker(eq(player), anyString(), any(Location.class))).thenReturn(true);
        var service = new CinematicService(plugin, logger, config, npc, null, null, null, null);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertTrue(service.preview(player, "intro"));
            assertTrue(service.control(player, "seek", 10, 0).contains("暂停"));
            verify(npc).teleportMarker(eq(player), anyString(), eq(new Location(world, 5, 64, 0)));
            assertTrue(service.control(player, "step", 0, 0).contains("暂停"));
            verify(player, never()).performCommand(anyString());
            service.stop(player);
            verify(npc, atLeastOnce()).removePrivateMarker(eq(player), anyString());
        }
    }
}
