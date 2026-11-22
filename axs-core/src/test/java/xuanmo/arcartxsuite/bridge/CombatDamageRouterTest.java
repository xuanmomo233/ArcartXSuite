package xuanmo.arcartxsuite.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.event.AttackUnregisteredEvent;
import io.lumine.mythic.lib.damage.AttackMetadata;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.symphony.api.damage.DamageBreakdown;
import priv.seventeen.artist.symphony.api.damage.DamageOutcome;
import priv.seventeen.artist.symphony.api.damage.DamageRequest;
import priv.seventeen.artist.symphony.api.damage.DamageResult;
import priv.seventeen.artist.symphony.api.damage.DamageResultState;
import priv.seventeen.artist.symphony.api.event.SymphonyDamageConfirmedEvent;
import priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent;
import priv.seventeen.artist.symphony.api.event.SymphonyDamagePrepareEvent;
import priv.seventeen.artist.symphony.api.event.SymphonyHitCheckEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;

class CombatDamageRouterTest {
    private final Player attacker = mock(Player.class);
    private final LivingEntity target = mock(LivingEntity.class);
    private final Entity bullet = mock(Entity.class);
    private final List<CombatDamageEvent> delivered = new ArrayList<>();
    private final CombatDamageRouter router = new CombatDamageRouter(delivered::add);

    @BeforeEach
    void identifyEntities() {
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(bullet.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getHealth()).thenReturn(20.0);
    }

    @Test
    void bukkitUsesFinalCancellationAndKeepsDistinctHitsBetweenSameEntities() {
        var cancelled = damage(attacker, 12);
        routeBukkit(router, cancelled);
        cancelled.setCancelled(true);
        routeBukkit(router, damage(attacker, 4));
        routeBukkit(router, damage(attacker, 4));
        routeBukkit(router, damage(attacker, 0));
        router.nextTick();
        assertEquals(List.of(4.0, 4.0), delivered.stream().map(CombatDamageEvent::getDamage).toList());
    }

    @Test
    void taczAggregatesBukkitPartsAndKeepsIndependentMelee() {
        beginGun();
        routeBukkit(router, damage(bullet, 3));
        routeBukkit(router, damage(bullet, 5));
        router.endGun(bullet.getUniqueId(), target);
        router.endGun(bullet.getUniqueId(), target);
        routeBukkit(router, damage(attacker, 3));
        router.nextTick();
        assertEquals(List.of(CombatDamageEvent.Source.TACZ, CombatDamageEvent.Source.BUKKIT),
            delivered.stream().map(CombatDamageEvent::getSource).toList());
        assertEquals(8.0, delivered.get(0).getDamage());
    }

    @Test
    void taczCancellationAndNoBukkitFallbackUseSettlementEvidence() {
        beginGun();
        var denied = damage(bullet, 7);
        routeBukkit(router, denied);
        denied.setCancelled(true);
        when(target.getHealth()).thenReturn(12.0);
        router.endGun(bullet.getUniqueId(), target);
        assertTrue(delivered.isEmpty());

        beginGun();
        router.endGun(bullet.getUniqueId(), target);
        assertTrue(delivered.isEmpty());
        beginGun();
        when(target.getHealth()).thenReturn(9.0);
        router.endGun(bullet.getUniqueId(), target);
        assertEquals(3.0, delivered.get(0).getDamage());
    }

    @Test
    void cancelledNativePreCannotClaimUnrelatedBukkitHit() {
        AtomicBoolean cancelled = new AtomicBoolean();
        router.beginGun(bullet.getUniqueId(), attacker, target, "rifle", true, cancelled::get);
        cancelled.set(true);
        routeBukkit(router, damage(attacker, 6));
        router.endGun(bullet.getUniqueId(), target);
        router.nextTick();
        assertEquals(1, delivered.size());
        assertEquals(CombatDamageEvent.Source.BUKKIT, delivered.get(0).getSource());
    }

    @Test
    void realSymphonyEventsOnlyEmitConfirmedPositiveTransactionsAndSeparateDodgeFeedback() {
        var host = mock(JavaPlugin.class);
        var manager = mock(PluginManager.class);
        var scheduler = mock(BukkitScheduler.class);
        var provider = mock(Plugin.class);
        when(provider.isEnabled()).thenReturn(true);
        when(manager.getPlugin("Symphony")).thenReturn(provider);
        List<Listener> listeners = new ArrayList<>();
        doAnswer(call -> { listeners.add(call.getArgument(0)); return null; })
            .when(manager).registerEvents(any(), eq(host));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CombatDamageEvent event) delivered.add(event);
            return null;
        }).when(manager).callEvent(any());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var dispatcher = new AttributeDamageDispatcher(host);
            var observed = new ArrayList<AttributeDamageEvent>();
            dispatcher.registerListener(observed::add);
            dispatcher.initialize(false, false, false, true);
            var routed = dispatcher.combatRouter();
            var listener = listeners.get(0);
            var request = new DamageRequest(attacker, target, List.of(), "bukkit:projectile", null, true, Map.of());

            var id = UUID.randomUUID();
            routed.beginGun(bullet.getUniqueId(), attacker, target, "rifle", true, () -> false);
            var hit = damage(bullet, 12);
            routed.beginBukkit(hit);
            fire(listener, new SymphonyDamagePrepareEvent(id, request, Map.of()));
            fire(listener, new SymphonyDamageEvent(id, request, DamageBreakdown.EMPTY, 12.0));
            routed.endBukkit(hit);
            when(target.getHealth()).thenReturn(14.0);
            routed.endGun(bullet.getUniqueId(), target);
            routed.nextTick();
            assertTrue(delivered.isEmpty());
            assertTrue(observed.isEmpty());

            fire(listener, result(id, DamageResultState.CONFIRMED, 6));
            fire(listener, result(id, DamageResultState.CONFIRMED, 6));
            fire(listener, new SymphonyDamagePrepareEvent(id, request, Map.of()));
            fire(listener, result(id, DamageResultState.CONFIRMED, 6));
            assertEquals(1, delivered.size());
            assertEquals(id, delivered.get(0).getTransactionId());
            assertNotNull(delivered.get(0).getGunHit());
            assertEquals(6.0, observed.get(0).damage());
            assertTrue(observed.get(0).critical());

            var late = UUID.randomUUID();
            var lateHit = damage(attacker, 4);
            routed.beginBukkit(lateHit);
            fire(listener, new SymphonyDamagePrepareEvent(late, request, Map.of()));
            routed.endBukkit(lateHit);
            lateHit.setCancelled(true);
            fire(listener, result(late, DamageResultState.CONFIRMED, 4));

            var cancelled = UUID.randomUUID();
            var deniedHit = damage(attacker, 9);
            routed.beginBukkit(deniedHit);
            fire(listener, new SymphonyDamagePrepareEvent(cancelled, request, Map.of()));
            deniedHit.setCancelled(true);
            routed.endBukkit(deniedHit);
            fire(listener, result(cancelled, DamageResultState.CANCELLED, 9));
            fire(listener, result(cancelled, DamageResultState.CONFIRMED, 9));

            var missed = UUID.randomUUID();
            fire(listener, new SymphonyDamagePrepareEvent(missed, request, Map.of()));
            var miss = new SymphonyHitCheckEvent(missed, request, 1, 1, 1, 0, false);
            fire(listener, miss);
            fire(listener, miss);
            assertEquals(2, observed.size());
            assertTrue(observed.get(1).dodged());
            assertEquals(1, delivered.size());
            dispatcher.shutdown();
        }
    }

    @Test
    void mythicLibUsesTheCurrentHitAndDoesNotCreditAmbientDamageToThePreviousPlayer() {
        var host = mock(JavaPlugin.class);
        var manager = mock(PluginManager.class);
        var scheduler = mock(BukkitScheduler.class);
        var provider = mock(Plugin.class);
        when(provider.isEnabled()).thenReturn(true);
        when(manager.getPlugin("MythicLib")).thenReturn(provider);
        List<Listener> listeners = new ArrayList<>();
        doAnswer(call -> { listeners.add(call.getArgument(0)); return null; })
            .when(manager).registerEvents(any(), eq(host));
        when(target.getLastDamageCause()).thenReturn(damage(mock(Player.class), 3));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var dispatcher = new AttributeDamageDispatcher(host);
            var observed = new ArrayList<AttributeDamageEvent>();
            dispatcher.registerListener(observed::add);
            dispatcher.initialize(false, false, true, false);
            var listener = listeners.get(0);
            var metadata = mock(AttackMetadata.class);
            when(metadata.getTarget()).thenReturn(target);

            fire(listener, new AttackUnregisteredEvent(damage(attacker, 7), metadata));
            fire(listener, new AttackUnregisteredEvent(new EntityDamageEvent(target, DamageCause.FIRE, 1), metadata));
            var cancelled = damage(attacker, 8);
            cancelled.setCancelled(true);
            fire(listener, new AttackUnregisteredEvent(cancelled, metadata));

            assertEquals(1, observed.size());
            assertSame(attacker, observed.get(0).attacker());
            assertEquals(7.0, observed.get(0).damage());
            dispatcher.shutdown();
        }
    }

    private void beginGun() {
        router.beginGun(bullet.getUniqueId(), attacker, target, "rifle", true, () -> false);
    }

    private EntityDamageByEntityEvent damage(Entity damager, double amount) {
        return new EntityDamageByEntityEvent(damager, target, DamageCause.PROJECTILE, amount);
    }

    private static void routeBukkit(CombatDamageRouter router, EntityDamageByEntityEvent event) {
        router.beginBukkit(event);
        router.endBukkit(event);
    }

    private static SymphonyDamageConfirmedEvent result(UUID id, DamageResultState state, double damage) {
        return new SymphonyDamageConfirmedEvent(new DamageResult(id, null, state, List.of(), List.of(),
            damage, true, null, DamageBreakdown.EMPTY, state == DamageResultState.CONFIRMED
                ? DamageOutcome.HIT : DamageOutcome.CANCELLED));
    }

    private static void fire(Listener listener, Event event) {
        for (var method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.bukkit.event.EventHandler.class)
                    && method.getParameterTypes()[0].isInstance(event)) {
                try {
                    method.setAccessible(true);
                    method.invoke(listener, event);
                } catch (InvocationTargetException e) {
                    throw new AssertionError(e.getCause());
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }
}
