package xuanmo.arcartxsuite.combateffect.combo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;

class ConfirmedComboTest {
    @Test
    void milestoneWithExactCountReplacesPendingHudUpdate(@TempDir Path definitions) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("milestone.trigger", "combo");
        yaml.set("milestone.conditions.combo-min", 2);
        yaml.set("milestone.ui-id", "AXS:combo_effect");
        yaml.set("milestone.packet-handler", "combo");
        yaml.save(definitions.resolve("test.yml").toFile());
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        var target = mock(LivingEntity.class);
        var bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.sendPacket(eq(player), eq("AXS:combo_effect"), eq("combo"), any())).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var tick = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(any(), tick.capture(), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var logger = Logger.getAnonymousLogger();
            var config = new ComboTrackerConfiguration(true, ComboTrackerConfiguration.ComboSource.BUKKIT,
                2000, Set.of("attack"), true, false, false);
            var combo = new ComboTrackerService(mock(JavaPlugin.class), config,
                CombatEffectPacketConfiguration.load(yaml, logger, definitions.toFile()), bridge, logger);
            combo.start(null, false);
            var damage = new CombatDamageEvent(player, target, 3, CombatDamageEvent.Source.BUKKIT, null, null);
            combo.onCombatDamage(damage);
            combo.onCombatDamage(damage);
            tick.getValue().run();
            var order = inOrder(bridge);
            order.verify(bridge).sendPacket(player, "AXS:combo_effect", "combo_sync", 1);
            order.verify(bridge).sendPacket(player, "AXS:combo_effect", "combo", java.util.Map.of("combo_count", 2));
            verify(bridge, never()).sendPacket(player, "AXS:combo_effect", "combo_sync", 2);
            combo.shutdown();
        }
    }

    @Test
    void coalescesClientVariablesWithoutCoalescingHitsAndClearsOnShutdown() {
        var attacker = mock(Player.class);
        var target = mock(LivingEntity.class);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(attacker.isOnline()).thenReturn(true);
        var bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        var task = mock(BukkitTask.class);
        var tick = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L))).thenReturn(task);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
            var logger = Logger.getAnonymousLogger();
            var config = new ComboTrackerConfiguration(true, ComboTrackerConfiguration.ComboSource.BUKKIT,
                2000, Set.of("attack"), true, false, false);
            var combo = new ComboTrackerService(plugin, config, CombatEffectPacketConfiguration.load(null, logger),
                bridge, logger);
            combo.start(null, false);
            var damage = new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.BUKKIT, null, null);
            combo.onCombatDamage(damage);
            combo.onCombatDamage(damage);
            combo.onCombatDamage(damage);
            assertEquals(3, combo.getComboCount(attacker));
            verify(bridge).sendPacket(attacker, "AXS:combo_effect", "combo_sync", 1);

            tick.getValue().run();
            tick.getValue().run();
            verify(bridge).sendPacket(attacker, "AXS:combo_effect", "combo_sync", 3);
            verify(bridge, never()).sendPacket(attacker, "AXS:combo_effect", "combo_sync", 2);
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));

            combo.shutdown();
            tick.getValue().run();
            verify(task).cancel();
            verify(bridge).sendPacket(attacker, "AXS:combo_effect", "combo_sync", 0);
            assertEquals(0, combo.getComboCount(attacker));
        }
    }

    @Test
    void autoUsesEffectiveDamageAndCountsARecognizedGunHitOnce() {
        var attacker = mock(Player.class);
        var target = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.isOnline()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        var chronos = mock(ChronosAccess.class);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var logger = Logger.getAnonymousLogger();
            var config = new ComboTrackerConfiguration(true, ComboTrackerConfiguration.ComboSource.AUTO,
                2000, Set.of("attack"), false, true, false);
            var combo = new ComboTrackerService(plugin, config, CombatEffectPacketConfiguration.load(null, logger),
                null, logger);
            combo.start(chronos, true);
            verifyNoInteractions(chronos);
            var hit = new CombatDamageEvent.GunHit(UUID.randomUUID(), "tacz:rifle", true);
            combo.onCombatDamage(new CombatDamageEvent(attacker, target, 5, CombatDamageEvent.Source.SYMPHONY,
                UUID.randomUUID(), hit));
            combo.onCombatDamage(new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.SYMPHONY,
                UUID.randomUUID(), hit));
            assertEquals(1, combo.getComboCount(attacker));
            combo.onCombatDamage(new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.BUKKIT, null, null));
            assertEquals(2, combo.getComboCount(attacker));
            combo.shutdown();
        }
    }

    @Test
    void timeoutsFollowTheLastHitAndSwitchingTargetsUpdatesTheHudImmediately() {
        var first = mock(Player.class);
        var second = mock(Player.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(first.getUniqueId()).thenReturn(firstId);
        when(second.getUniqueId()).thenReturn(secondId);
        when(first.isOnline()).thenReturn(true);
        when(second.isOnline()).thenReturn(true);
        var target = mock(LivingEntity.class);
        var otherTarget = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(otherTarget.getUniqueId()).thenReturn(UUID.randomUUID());
        var bridge = mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        var task = mock(BukkitTask.class);
        var tick = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L))).thenReturn(task);
        var time = new AtomicLong();
        var logger = Logger.getAnonymousLogger();
        var config = new ComboTrackerConfiguration(true, ComboTrackerConfiguration.ComboSource.BUKKIT,
            2000, Set.of("attack"), true, true, false);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(firstId)).thenReturn(first);
            bukkit.when(() -> Bukkit.getPlayer(secondId)).thenReturn(second);
            var combo = new ComboTrackerService(plugin, config, CombatEffectPacketConfiguration.load(null, logger),
                bridge, logger, time::get);
            combo.start(null, false);
            combo.onCombatDamage(hit(first, target));
            time.set(TimeUnit.MILLISECONDS.toNanos(500));
            combo.onCombatDamage(hit(second, target));
            time.set(TimeUnit.MILLISECONDS.toNanos(1000));
            combo.onCombatDamage(hit(first, target));

            time.set(TimeUnit.MILLISECONDS.toNanos(2500));
            tick.getValue().run();
            verify(bridge).sendPacket(second, "AXS:combo_effect", "combo_sync", 0);
            verify(bridge).sendPacket(first, "AXS:combo_effect", "combo_sync", 2);
            bukkit.verify(() -> Bukkit.getPlayer(firstId), never());

            time.set(TimeUnit.MILLISECONDS.toNanos(2600));
            combo.onCombatDamage(hit(first, otherTarget));
            verify(bridge, times(2)).sendPacket(first, "AXS:combo_effect", "combo_sync", 1);
            time.set(TimeUnit.MILLISECONDS.toNanos(3000));
            tick.getValue().run();
            verify(bridge, never()).sendPacket(first, "AXS:combo_effect", "combo_sync", 0);
            time.set(TimeUnit.MILLISECONDS.toNanos(4600));
            tick.getValue().run();
            verify(bridge).sendPacket(first, "AXS:combo_effect", "combo_sync", 0);
            verify(task).cancel();
            combo.shutdown();
        }
    }

    private static CombatDamageEvent hit(Player attacker, LivingEntity target) {
        return new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.BUKKIT, null, null);
    }
}
