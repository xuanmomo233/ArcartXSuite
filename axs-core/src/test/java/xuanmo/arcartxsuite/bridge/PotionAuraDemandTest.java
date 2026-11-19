package xuanmo.arcartxsuite.bridge;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PotionAuraDemandTest {
    record Pulse(Runnable run, BukkitTask handle, long period) { }
    static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final PluginManager manager = mock(PluginManager.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Player player = mock(Player.class);
        final PotionEffectType speed = mock(PotionEffectType.class);
        final List<Pulse> pulses = new ArrayList<>();
        final Map<PotionEffectType, PotionEffect> active = new HashMap<>();
        final DefaultPotionAuraService service;
        Fixture() {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(org.bukkit.command.ConsoleCommandSender.class));
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("tester");
            when(speed.getName()).thenReturn("SPEED");
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            when(player.getPotionEffect(any())).thenAnswer(call -> active.get(call.getArgument(0)));
            when(player.addPotionEffect(any(PotionEffect.class))).thenAnswer(call -> {
                PotionEffect effect = call.getArgument(0); active.put(effect.getType(), effect); return true;
            });
            doAnswer(call -> { active.remove(call.getArgument(0)); return null; }).when(player).removePotionEffect(any());
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                var handle = mock(BukkitTask.class);
                pulses.add(new Pulse(call.getArgument(1), handle, call.getArgument(3)));
                return handle;
            });
            service = new DefaultPotionAuraService(plugin);
        }
        @Override public void close() { service.shutdown(); bukkit.close(); }
    }

    @Test void idleInitializationDoesNotOwnATimer() {
        try (var f = new Fixture()) {
            f.service.initialize(); f.service.initialize();
            assertTrue(f.pulses.isEmpty());
            verify(f.manager, times(1)).registerEvents(f.service, f.plugin);
        }
    }

    @Test void firstSourceStartsOneTimerAndUnchangedReloadDoesNotRestartIt() {
        try (var f = new Fixture()) {
            f.service.initialize();
            f.service.setEffects(f.player, "title", Map.of(f.speed, 2));
            f.service.setEffects(f.player, "title", Map.of(f.speed, 2));
            f.service.initialize();
            assertEquals(1, f.pulses.size());
            verify(f.player, times(1)).addPotionEffect(any(PotionEffect.class));
            f.service.configure(true, 120, 240, false, true); f.service.initialize();
            assertEquals(2, f.pulses.size()); assertEquals(120, f.pulses.get(1).period());
            verify(f.pulses.get(0).handle()).cancel();
        }
    }

    @Test void sourceRemovalRestoresWeakerAuraAndLastRemovalStopsTheTimer() {
        try (var f = new Fixture()) {
            f.service.initialize();
            f.service.setEffects(f.player, "title", Map.of(f.speed, 1));
            f.service.setEffects(f.player, "equipment", Map.of(f.speed, 3));
            assertEquals(2, f.active.get(f.speed).getAmplifier());
            f.service.clearSource(f.player, "equipment");
            assertEquals(0, f.active.get(f.speed).getAmplifier());
            verify(f.pulses.get(0).handle(), never()).cancel();
            f.service.clearSource(f.player, "title");
            assertTrue(f.active.isEmpty()); verify(f.pulses.get(0).handle()).cancel();
        }
    }

    @Test void externalStrongEffectsRemainUntouched() {
        try (var f = new Fixture()) {
            f.service.initialize();
            PotionEffect external = new PotionEffect(f.speed, 600, 4, false);
            f.active.put(f.speed, external);
            f.service.setEffects(f.player, "title", Map.of(f.speed, 1));
            f.pulses.get(0).run().run();
            f.service.clearSource(f.player, "title");
            assertSame(external, f.active.get(f.speed));
            verify(f.player, never()).addPotionEffect(any(PotionEffect.class));
            verify(f.player, never()).removePotionEffect(any());
        }
    }

    @Test void quitAndOfflineCleanupStopIdleRefreshesAndCanResumeOnNextSource() {
        try (var f = new Fixture()) {
            f.service.initialize(); f.service.setEffects(f.player, "title", Map.of(f.speed, 2));
            var event = mock(PlayerQuitEvent.class); when(event.getPlayer()).thenReturn(f.player);
            f.service.onQuit(event); verify(f.pulses.get(0).handle()).cancel();
            f.service.setEffects(f.player, "title", Map.of(f.speed, 2)); assertEquals(2, f.pulses.size());
            when(f.player.isOnline()).thenReturn(false);
            f.pulses.get(1).run().run(); verify(f.pulses.get(1).handle()).cancel();
        }
    }

    @Test void invalidSourcesDoNotStartTimersAndDisableClearsOwnedEffects() {
        try (var f = new Fixture()) {
            f.service.initialize();
            var instant = mock(PotionEffectType.class); when(instant.isInstant()).thenReturn(true);
            f.service.setEffects(f.player, "instant", Map.of(instant, 1));
            assertTrue(f.pulses.isEmpty());
            f.service.setEffects(f.player, "title", Map.of(f.speed, 999));
            assertEquals(255, f.active.get(f.speed).getAmplifier());
            f.service.configure(false, 60, 200, false, true); f.service.initialize();
            assertFalse(f.service.available()); assertTrue(f.active.isEmpty());
            verify(f.pulses.get(0).handle()).cancel();
        }
    }
}
