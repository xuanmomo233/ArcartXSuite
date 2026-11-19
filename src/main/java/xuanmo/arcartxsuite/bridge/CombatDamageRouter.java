package xuanmo.arcartxsuite.bridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import xuanmo.arcartxsuite.api.combat.CombatEventSupport;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent.GunHit;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent.Source;

/** Correlates synchronous ingress events and consumes each Symphony transaction once. Main thread only. */
final class CombatDamageRouter implements Listener {
    // Symphony allows up to 20 ticks between Bukkit settlement and its confirmation.
    private static final long TRANSACTION_TICKS = 40;
    private static final int MAX_TRANSACTIONS = 16384;

    record Participants(Player attacker, LivingEntity target, GunHit gunHit) {}
    private record Pending(Participants participants, EntityDamageEvent original, long expires) {}
    private record ShotKey(UUID bullet, UUID target) {}

    private static final class Ingress {
        private final EntityDamageEvent event;
        private final Player attacker;
        private final Shot shot;
        private UUID symphonyTransaction;

        private Ingress(EntityDamageEvent event, Player attacker, Shot shot) {
            this.event = event;
            this.attacker = attacker;
            this.shot = shot;
        }
    }

    private static final class Shot {
        private final Participants participants;
        private final double healthBefore;
        private final BooleanSupplier cancelled;
        private final List<EntityDamageEvent> bukkit = new ArrayList<>(2);
        private boolean observedBukkit;

        private Shot(Participants participants, double healthBefore, BooleanSupplier cancelled) {
            this.participants = participants;
            this.healthBefore = healthBefore;
            this.cancelled = cancelled;
        }
    }

    private final Consumer<CombatDamageEvent> emit;
    private final ArrayDeque<Ingress> ingress = new ArrayDeque<>();
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private final Map<ShotKey, Shot> shots = new LinkedHashMap<>();
    private List<Ingress> bukkitResults = new ArrayList<>();
    private long tick;

    CombatDamageRouter(Consumer<CombatDamageEvent> emit) {
        this.emit = emit;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void beginBukkit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        Player attacker = event instanceof EntityDamageByEntityEvent attack
            ? CombatEventSupport.resolvePlayerAttacker(attack) : null;
        Shot shot = findShot(event, target, attacker);
        if (shot != null) {
            shot.observedBukkit = true;
            attacker = shot.participants.attacker();
        }
        ingress.addLast(new Ingress(event, attacker, shot));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void endBukkit(EntityDamageEvent event) {
        Ingress hit = ingress.peekLast();
        if (hit == null || hit.event != event) return;
        ingress.removeLast();
        if (hit.symphonyTransaction != null) {
            if (event.isCancelled()) consumeSymphony(hit.symphonyTransaction);
            return;
        }
        if (hit.shot != null) {
            hit.shot.bukkit.add(event);
        } else if (hit.attacker != null) {
            bukkitResults.add(hit);
        }
    }

    void beginSymphony(UUID id, Player attacker, LivingEntity target) {
        Ingress hit = ingress.peekLast();
        if (hit != null && hit.event.getEntity().getUniqueId().equals(target.getUniqueId())) {
            hit.symphonyTransaction = id;
            GunHit gun = hit.shot == null ? null : hit.shot.participants.gunHit();
            Player owner = gun != null || attacker == null ? hit.attacker : attacker;
            pending.putIfAbsent(id, new Pending(new Participants(owner, target, gun), hit.event, tick + TRANSACTION_TICKS));
        } else {
            pending.putIfAbsent(id, new Pending(new Participants(attacker, target, null), null, tick + TRANSACTION_TICKS));
        }
        if (pending.size() > MAX_TRANSACTIONS) {
            Iterator<UUID> oldest = pending.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    Participants consumeSymphony(UUID id) {
        Pending result = pending.get(id);
        if (result == null || result.participants() == null) return null;
        // Keep the consumed ID until expiry without retaining the event or its entities.
        pending.put(id, new Pending(null, null, result.expires()));
        if (result.original() != null && result.original().isCancelled()) return null;
        return result.participants();
    }

    void beginGun(UUID bullet, Player attacker, LivingEntity target, String gunId, boolean headShot,
                  BooleanSupplier cancelled) {
        ShotKey key = new ShotKey(bullet, target.getUniqueId());
        GunHit gun = new GunHit(UUID.randomUUID(), gunId, headShot);
        shots.put(key, new Shot(new Participants(attacker, target, gun), target.getHealth(), cancelled));
    }

    void endGun(UUID bullet, LivingEntity target) {
        Shot shot = shots.remove(new ShotKey(bullet, target.getUniqueId()));
        if (shot == null || shot.cancelled.getAsBoolean()) return;
        double damage = shot.observedBukkit
            ? shot.bukkit.stream().filter(event -> !event.isCancelled()).mapToDouble(EntityDamageEvent::getFinalDamage)
                .filter(CombatDamageRouter::positive).sum()
            : shot.healthBefore - target.getHealth();
        publish(shot.participants, damage, Source.TACZ, null);
    }

    void publish(Participants participants, double damage, Source source, UUID transactionId) {
        Player attacker = participants.attacker();
        if (attacker != null && positive(damage)
                && !attacker.getUniqueId().equals(participants.target().getUniqueId())) {
            emit.accept(new CombatDamageEvent(attacker, participants.target(), damage, source,
                transactionId, participants.gunHit()));
        }
    }

    private Shot findShot(EntityDamageEvent event, LivingEntity target, Player attacker) {
        if (shots.isEmpty() || !(event instanceof EntityDamageByEntityEvent attack)) return null;
        Entity damager = attack.getDamager();
        Shot exact = shots.get(new ShotKey(damager.getUniqueId(), target.getUniqueId()));
        if (exact != null && !exact.cancelled.getAsBoolean()) return exact;
        // TaCZ can also present the shooter as the direct damager for selected entity types.
        if (attacker == null || !(damager instanceof Player)) return null;
        Shot match = null;
        for (var entry : shots.entrySet()) {
            Shot candidate = entry.getValue();
            if (entry.getKey().target().equals(target.getUniqueId())
                    && attacker.getUniqueId().equals(candidate.participants.attacker().getUniqueId())
                    && !candidate.cancelled.getAsBoolean()) {
                match = candidate;
            }
        }
        return match;
    }

    void nextTick() {
        tick++;
        shots.clear();
        ingress.clear();
        Iterator<Pending> oldest = pending.values().iterator();
        while (oldest.hasNext() && oldest.next().expires() <= tick) oldest.remove();
        if (!bukkitResults.isEmpty()) {
            var settled = bukkitResults;
            bukkitResults = new ArrayList<>();
            for (Ingress hit : settled) {
                if (!hit.event.isCancelled() && hit.event.getEntity() instanceof LivingEntity target) {
                    publish(new Participants(hit.attacker, target, null), hit.event.getFinalDamage(), Source.BUKKIT, null);
                }
            }
        }
    }

    void clear() {
        shots.clear();
        ingress.clear();
        pending.clear();
        bukkitResults.clear();
    }

    static boolean positive(double damage) {
        return Double.isFinite(damage) && damage > 0.0;
    }
}
