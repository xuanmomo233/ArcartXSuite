package xuanmo.arcartxsuite.api.combat;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.util.AttributeResolver;

public final class EntityCombatMetadata {

    private EntityCombatMetadata() {}

    // ─── MythicMobs availability ─────────────────────────────
    // compileOnly 依赖：运行时 MythicMobs 可能不存在，需懒检测避免 NoClassDefFoundError

    private static volatile Boolean mythicAvailable;

    private static boolean mythicAvailable() {
        Boolean cached = mythicAvailable;
        if (cached != null) {
            return cached;
        }
        boolean present = Bukkit.getPluginManager().getPlugin("MythicBukkit") != null
            || Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        mythicAvailable = present;
        return present;
    }

    // ─── Display name ────────────────────────────────────────

    public static String resolveDisplayName(LivingEntity entity, String mythicMobId) {
        if (entity == null) {
            return "Unknown";
        }
        if (entity instanceof Player player) {
            return player.getName();
        }
        if (mythicMobId != null && !mythicMobId.isBlank()) {
            return mythicMobId;
        }
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            return ChatColor.stripColor(customName);
        }
        return entity.getType().name();
    }

    // ─── Entity type helpers ─────────────────────────────────

    public static String resolveEntityType(LivingEntity entity) {
        return entity == null ? "" : entity.getType().name();
    }

    public static String normalizeEntityType(String entityType) {
        return entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
    }

    public static String formatEntityTypeName(EntityType type) {
        if (type == null) return "";
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ─── MythicMobs integration (direct API) ────────────────

    public static String normalizeMythicMobId(String mythicMobId) {
        return mythicMobId == null ? "" : mythicMobId.trim();
    }

    public static String resolveMythicMobId(LivingEntity entity) {
        if (!mythicAvailable()) return "";
        ActiveMob activeMob = resolveActiveMob(entity);
        return activeMob == null ? "" : nullToEmpty(resolveMythicMobIdFrom(activeMob));
    }

    public static ActiveMob resolveActiveMob(LivingEntity entity) {
        if (entity == null || !mythicAvailable()) return null;
        try {
            return MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    public static String resolveMythicMobIdFrom(ActiveMob activeMob) {
        if (activeMob == null) return "";
        try {
            if (activeMob.getType() != null && activeMob.getType().getInternalName() != null) {
                return activeMob.getType().getInternalName();
            }
        } catch (Exception | LinkageError ignored) {}
        return "";
    }

    // ─── Health resolution ───────────────────────────────────

    public static double resolveMaxHealth(LivingEntity entity, ActiveMob activeMob) {
        if (activeMob != null && activeMob.getEntity() != null) {
            try {
                return Math.max(activeMob.getEntity().getMaxHealth(), 1.0D);
            } catch (Exception | LinkageError ignored) {}
        }
        if (entity != null) {
            return AttributeResolver.getMaxHealth(entity);
        }
        return 20.0;
    }

    public static double resolveCurrentHealth(LivingEntity entity, double maxHealth, ActiveMob activeMob) {
        if (activeMob != null && activeMob.getEntity() != null) {
            try {
                return Math.max(0.0D, Math.min(activeMob.getEntity().getHealth(), maxHealth));
            } catch (Exception | LinkageError ignored) {}
        }
        if (entity != null) {
            return Math.min(entity.getHealth(), maxHealth);
        }
        return 0.0;
    }

    // ─── Util ────────────────────────────────────────────────

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
