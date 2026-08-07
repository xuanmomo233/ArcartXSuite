package xuanmo.arcartxsuite.combateffect.packet.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;

/**
 * 战斗数据包上下文。
 * <p>
 * 属于 combateffect 模块的 packet 子模块，封装一次战斗交互（击杀、攻击、死亡）
 * 的上下文信息，包括攻击者、目标、MythicMob ID、实体类型、伤害值与死亡消息。
 * 提供占位符渲染能力，用于将模板 Map 中的占位符替换为实际战斗数据。
 */
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

    /**
     * 从击杀事件构建上下文。
     *
     * @param killer       击杀者
     * @param victim       被击杀实体
     * @param deathMessage 死亡消息
     * @return 战斗数据包上下文
     */
    public static CombatPacketContext fromKill(Player killer, LivingEntity victim, String deathMessage) {
        return new CombatPacketContext(
            killer, victim,
            resolveMythicMobId(victim),
            victim.getType(),
            0.0,
            deathMessage
        );
    }

    /**
     * 从攻击事件构建上下文。
     *
     * @param attacker 攻击者
     * @param target   被攻击实体
     * @param event    Bukkit 实体间伤害事件
     * @return 战斗数据包上下文
     */
    public static CombatPacketContext fromAttack(Player attacker, LivingEntity target, EntityDamageByEntityEvent event) {
        return new CombatPacketContext(
            attacker, target,
            resolveMythicMobId(target),
            target.getType(),
            event.getFinalDamage(),
            ""
        );
    }

    /**
     * 从玩家死亡事件构建上下文。
     *
     * @param victim 死亡的玩家
     * @param killer 击杀者
     * @return 战斗数据包上下文
     */
    public static CombatPacketContext fromDeath(Player victim, Player killer) {
        return new CombatPacketContext(
            killer, victim,
            "",
            victim.getType(),
            0.0,
            ""
        );
    }

    /**
     * 返回攻击者玩家。
     *
     * @return 攻击者，可能为 {@code null}
     */
    public Player attacker() { return attacker; }

    /**
     * 返回目标实体。
     *
     * @return 目标实体
     */
    public LivingEntity target() { return target; }

    /**
     * 返回目标的 MythicMob ID。
     *
     * @return MythicMob ID，无则为空字符串
     */
    public String targetMythicMobId() { return targetMythicMobId; }

    /**
     * 返回目标的实体类型。
     *
     * @return 实体类型
     */
    public EntityType targetEntityType() { return targetEntityType; }

    /**
     * 返回伤害值。
     *
     * @return 伤害值
     */
    public double damage() { return damage; }

    /**
     * 返回死亡消息。
     *
     * @return 死亡消息，无则为空字符串
     */
    public String deathMessage() { return deathMessage; }

    /**
     * 根据模板 Map 渲染数据包负载，将字符串值中的占位符替换为实际战斗数据。
     *
     * @param template      模板 Map
     * @param recipientType 接收者类型
     * @param recipient     接收者玩家
     * @return 渲染后的负载对象，模板为空时返回空 Map
     */
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
