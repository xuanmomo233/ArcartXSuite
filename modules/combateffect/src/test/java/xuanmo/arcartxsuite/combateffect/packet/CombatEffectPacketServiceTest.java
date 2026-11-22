package xuanmo.arcartxsuite.combateffect.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.api.event.TaczGunKillEvent;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.module.SimpleEventBus;

class CombatEffectPacketServiceTest {
    @Test
    void bukkitAndTaczPublishOneKillAndLaterDeathsRemainEligible() {
        var log = Logger.getAnonymousLogger();
        var bus = new SimpleEventBus(log);
        var plugin = mock(JavaPlugin.class);
        var scheduler = mock(BukkitScheduler.class);
        var clearTasks = new ArrayList<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            clearTasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var service = new CombatEffectPacketService(plugin,
            CombatEffectPacketConfiguration.load(null, log), null, log);
        service.setEventBusProvider(() -> bus);
        var killer = mock(Player.class);
        var target = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getType()).thenReturn(EntityType.ZOMBIE);
        when(target.getName()).thenReturn("zombie");
        var bukkitDeath = mock(EntityDeathEvent.class);
        when(bukkitDeath.getEntity()).thenReturn(target);
        var gunDeath = new TaczGunKillEvent(killer, target, 7, false, "tacz:glock_17");
        var payloads = new ArrayList<Object>();
        bus.subscribe("axs.combateffect.kill_entity", event -> payloads.add(event.payload()));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(target.getKiller()).thenReturn(killer);
            service.onEntityDeath(bukkitDeath);
            service.onTaczGunKill(gunDeath);
            assertEquals(1, payloads.size());
            assertEquals(1, clearTasks.size());

            clearTasks.get(0).run();
            when(target.getKiller()).thenReturn(null);
            service.onEntityDeath(bukkitDeath);
            service.onTaczGunKill(gunDeath);
            assertEquals(2, payloads.size());
        }
    }

    @Test
    void damageBusSkipsUnusedPayloadAndStillServesDynamicWildcardSubscribers() {
        var log = Logger.getAnonymousLogger();
        var bus = new SimpleEventBus(log);
        var service = new CombatEffectPacketService(mock(JavaPlugin.class),
            CombatEffectPacketConfiguration.load(null, log), null, log);
        service.setEventBusProvider(() -> bus);
        var attacker = mock(Player.class);
        var target = mock(LivingEntity.class);
        var world = mock(World.class);
        when(attacker.getName()).thenReturn("attacker");
        when(attacker.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(target.getType()).thenReturn(EntityType.ZOMBIE);
        when(target.getName()).thenReturn("zombie");
        var damage = new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.BUKKIT, null, null);

        service.onCombatDamage(damage);
        verifyNoInteractions(attacker, target, world);

        var payloads = new ArrayList<Object>();
        String subscription = bus.subscribe("axs.combateffect.*", event -> payloads.add(event.payload()));
        service.onCombatDamage(damage);
        assertEquals(1, payloads.size());
        assertEquals(Map.of("attacker", "attacker", "targetType", "ZOMBIE", "targetName", "zombie",
            "damage", "3.0", "world", "world", "source", "bukkit"), payloads.get(0));

        bus.unsubscribe(subscription);
        service.onCombatDamage(damage);
        verify(target, times(1)).getType();
    }
}
