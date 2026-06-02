package xuanmo.arcartxsuite.combateffect.packet.config;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CombatPacketContext {

    // MythicMobs 反射缓存
    private static volatile boolean mythicReflectionInitialized;
    private static volatile boolean mythicUnavailable;
    private static Method mythicInstMethod;
    private static Method mythicGetMobManagerMethod;
    private static Method mythicGetActiveMobMethod;
    private static Method mythicGetMobTypeMethod;
    private static Method mythicGetInternalNameMethod;

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
        if (mythicUnavailable) {
            return "";
        }
        try {
            initMythicReflection();
            if (mythicUnavailable) {
                return "";
            }
            Object mythicBukkit = mythicInstMethod.invoke(null);
            Object mobManager = mythicGetMobManagerMethod.invoke(mythicBukkit);
            Object activeMob = mythicGetActiveMobMethod.invoke(mobManager, entity.getUniqueId());
            if (activeMob == null) {
                return "";
            }
            // Handle Optional return from newer MythicMobs API
            if (activeMob instanceof Optional<?> opt) {
                if (opt.isEmpty()) return "";
                activeMob = opt.get();
            }
            ensureMobTypeMethods(activeMob);
            if (mythicGetMobTypeMethod == null) return "";
            Object mobType = mythicGetMobTypeMethod.invoke(activeMob);
            if (mobType == null) return "";
            if (mythicGetInternalNameMethod == null) return "";
            Object internalName = mythicGetInternalNameMethod.invoke(mobType);
            return internalName instanceof String s ? s : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void initMythicReflection() {
        if (mythicReflectionInitialized) {
            return;
        }
        synchronized (CombatPacketContext.class) {
            if (mythicReflectionInitialized) {
                return;
            }
            try {
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                mythicInstMethod = mythicBukkitClass.getMethod("inst");
                mythicGetMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");
                Object inst = mythicInstMethod.invoke(null);
                Object mm = mythicGetMobManagerMethod.invoke(inst);
                mythicGetActiveMobMethod = mm.getClass().getMethod("getActiveMob", UUID.class);
                // 需要从实际返回的 ActiveMob 实例上获取方法，这里先用返回类型探测
                Class<?> activeMobReturnType = mythicGetActiveMobMethod.getReturnType();
                // 如果返回类型是 Optional，获取其泛型参数的实际类型的方法
                // 为了兼容性，延迟到第一次实际调用时解析 getMobType/getInternalName
                mythicReflectionInitialized = true;
            } catch (Exception exception) {
                mythicUnavailable = true;
                mythicReflectionInitialized = true;
            }
        }
    }

    private static void ensureMobTypeMethods(Object activeMob) throws Exception {
        if (mythicGetMobTypeMethod == null) {
            mythicGetMobTypeMethod = activeMob.getClass().getMethod("getMobType");
        }
        if (mythicGetInternalNameMethod == null) {
            Object mobType = mythicGetMobTypeMethod.invoke(activeMob);
            if (mobType != null) {
                mythicGetInternalNameMethod = mobType.getClass().getMethod("getInternalName");
            }
        }
    }
}
