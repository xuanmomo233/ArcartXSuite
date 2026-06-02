package xuanmo.arcartxsuite.entitytracker.target.service;

import java.text.DecimalFormat;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.entitytracker.target.config.EntityTargetHudConfiguration;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;

public final class EntityTargetStateResolver {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private EntityTargetStateResolver() {
    }

    public static Optional<EntityTargetSnapshot> resolve(
        Player attacker,
        LivingEntity target,
        EntityTargetHudConfiguration configuration,
        long lastHitAtMillis,
        long nowMillis
    ) {
        return resolve(attacker, target, configuration, lastHitAtMillis, nowMillis, EntityCombatMetadata::resolveMythicMobId);
    }

    static Optional<EntityTargetSnapshot> resolve(
        Player attacker,
        LivingEntity target,
        EntityTargetHudConfiguration configuration,
        long lastHitAtMillis,
        long nowMillis,
        Function<LivingEntity, String> mythicMobIdResolver
    ) {
        if (attacker == null || target == null || configuration == null) {
            return Optional.empty();
        }
        if (!attacker.isOnline() || attacker.isDead()) {
            return Optional.empty();
        }
        if (target.isDead() || !target.isValid()) {
            return Optional.empty();
        }

        Location attackerLocation = attacker.getLocation();
        Location targetLocation = target.getLocation();
        if (attackerLocation == null || targetLocation == null) {
            return Optional.empty();
        }

        World attackerWorld = attackerLocation.getWorld();
        World targetWorld = targetLocation.getWorld();
        if (attackerWorld == null || targetWorld == null || !attackerWorld.getUID().equals(targetWorld.getUID())) {
            return Optional.empty();
        }

        long lastHitAgoMs = Math.max(0L, nowMillis - lastHitAtMillis);
        if (lastHitAgoMs > configuration.targetTimeoutMs()) {
            return Optional.empty();
        }

        double distanceSquared = attackerLocation.distanceSquared(targetLocation);
        double maxDistanceSquared = configuration.maxViewDistance() * configuration.maxViewDistance();
        if (distanceSquared > maxDistanceSquared) {
            return Optional.empty();
        }

        int distance = (int) Math.round(Math.sqrt(Math.max(0.0D, distanceSquared)));
        Object activeMob = EntityCombatMetadata.resolveActiveMob(target);
        String mythicMobId = nullToEmpty(EntityCombatMetadata.resolveMythicMobIdFrom(activeMob));
        double maxHealth = EntityCombatMetadata.resolveMaxHealth(target, activeMob);
        double health = EntityCombatMetadata.resolveCurrentHealth(target, maxHealth, activeMob);
        double progress = maxHealth <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(health / maxHealth, 1.0D));
        double healthPercent = progress * 100.0D;
        String entityType = EntityCombatMetadata.resolveEntityType(target);
        String entityTypeName = EntityCombatMetadata.formatEntityTypeName(target.getType());

        EntityTargetSnapshot snapshot = new EntityTargetSnapshot(
            "",
            "",
            health,
            maxHealth,
            formatNumber(health),
            formatNumber(maxHealth),
            healthPercent,
            formatNumber(healthPercent),
            progress,
            distance,
            distance + "m",
            EntityCombatMetadata.resolveDisplayName(target, mythicMobId),
            target.getUniqueId().toString(),
            nullToEmpty(targetWorld.getName()),
            targetLocation.getBlockX(),
            targetLocation.getBlockY(),
            targetLocation.getBlockZ(),
            entityType,
            entityTypeName,
            mythicMobId,
            target instanceof Player,
            lastHitAgoMs,
            configuration.targetTimeoutMs()
        );
        return Optional.of(snapshot.withRenderedTexts(configuration.titleFormat(), configuration.subtitleFormat()));
    }

    private static String formatNumber(double value) {
        return NUMBER_FORMAT.format(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

