package xuanmo.arcartxsuite.combateffect.packet.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;

public final class CombatPacketContext {

    private final Player attacker;
    private final LivingEntity target;
    private final String targetMythicMobId;
    private final EntityType targetEntityType;
    private final double damage;
    private final String deathMessage;

    private CombatPacketContext(
        Player attacker,
        LivingEntity target,
        String targetMythicMobId,
        EntityType targetEntityType,
        double damage,
        String deathMessage
    ) {
        this.attacker = attacker;
        this.target = target;
        this.targetMythicMobId = targetMythicMobId == null ? "" : targetMythicMobId;
        this.targetEntityType = targetEntityType;
        this.damage = damage;
        this.deathMessage = deathMessage == null ? "" : deathMessage;
    }

    public static CombatPacketContext fromKill(Player killer, LivingEntity victim, String deathMessage) {
        return new CombatPacketContext(
            killer, victim,
            resolveMythicMobId(victim),
            victim.getType(),
            0.0,
            deathMessage
        );
    }

    public static CombatPacketContext fromAttack(Player attacker, LivingEntity target, EntityDamageByEntityEvent event) {
        return new CombatPacketContext(
            attacker, target,
            resolveMythicMobId(target),
            target.getType(),
            event.getFinalDamage(),
            ""
        );
    }

    public static CombatPacketContext fromDeath(Player victim, Player killer) {
        return new CombatPacketContext(
            killer, victim,
            "",
            victim.getType(),
            0.0,
            ""
        );
    }

    public Player attacker() { return attacker; }
    public LivingEntity target() { return target; }
    public String targetMythicMobId() { return targetMythicMobId; }
    public EntityType targetEntityType() { return targetEntityType; }
    public double damage() { return damage; }
    public String deathMessage() { return deathMessage; }

    public Object renderPayload(Map<String, Object> template, PacketRecipient recipientType, Player recipient) {
        if (template == null || template.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                value = replacePlaceholders(text, recipientType, recipient);
            }
            payload.put(entry.getKey(), value);
        }
        return payload;
    }

    private String replacePlaceholders(String text, PacketRecipient recipientType, Player recipient) {
        return text
            .replace("{attacker}", attacker != null ? attacker.getName() : "")
            .replace("{target}", target instanceof Player p ? p.getName() : target.getType().name())
            .replace("{target_name}", target.getCustomName() != null ? target.getCustomName() : target.getType().name())
            .replace("{damage}", String.format("%.1f", damage))
            .replace("{death_message}", deathMessage)
            .replace("{recipient}", recipient.getName())
            .replace("{mythic_mob_id}", targetMythicMobId)
            .replace("{entity_type}", targetEntityType.name());
    }

    private static String resolveMythicMobId(LivingEntity entity) {
        return EntityCombatMetadata.resolveMythicMobId(entity);
    }
}
