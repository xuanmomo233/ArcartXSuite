package xuanmo.arcartxsuite.api.event;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/** A positive, settled player damage result, routed by the Suite host. */
@ApiStability.Internal
public final class CombatDamageEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Source { BUKKIT, TACZ, SYMPHONY }

    public record GunHit(UUID id, String gunId, boolean headShot) {}

    private final Player attacker;
    private final LivingEntity target;
    private final double damage;
    private final Source source;
    private final UUID transactionId;
    private final GunHit gunHit;

    public CombatDamageEvent(Player attacker, LivingEntity target, double damage, Source source,
                             @Nullable UUID transactionId, @Nullable GunHit gunHit) {
        this.attacker = attacker;
        this.target = target;
        this.damage = damage;
        this.source = source;
        this.transactionId = transactionId;
        this.gunHit = gunHit;
    }

    public Player getAttacker() { return attacker; }
    public LivingEntity getTarget() { return target; }
    public double getDamage() { return damage; }
    public Source getSource() { return source; }
    public @Nullable UUID getTransactionId() { return transactionId; }
    public @Nullable GunHit getGunHit() { return gunHit; }

    public Map<String, String> payload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("attacker", attacker.getName());
        payload.put("targetType", target.getType().name());
        payload.put("targetName", target.getName());
        payload.put("damage", String.valueOf(damage));
        payload.put("world", attacker.getWorld().getName());
        payload.put("source", source.name().toLowerCase(Locale.ROOT));
        if (transactionId != null) payload.put("transaction_id", transactionId.toString());
        if (gunHit != null) {
            payload.put("headshot", String.valueOf(gunHit.headShot()));
            payload.put("gun_id", gunHit.gunId());
        }
        return payload;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
