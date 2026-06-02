package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;

public final class BossSession {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private final UUID entityUuid;
    private final BossDefinition definition;
    private final String mythicMobId;
    private final long spawnedAt;
    private final long spawnOrder;
    private final BossDamageRankingTracker damageRankingTracker;
    private boolean settled;
    private String lastKnownDisplayName;

    public BossSession(UUID entityUuid, BossDefinition definition, String mythicMobId, String displayName, long spawnOrder) {
        this.entityUuid = entityUuid;
        this.definition = definition;
        this.mythicMobId = mythicMobId;
        this.spawnedAt = System.currentTimeMillis();
        this.spawnOrder = spawnOrder;
        this.damageRankingTracker = new BossDamageRankingTracker(definition.damageRanking());
        this.lastKnownDisplayName = displayName;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public BossDefinition getDefinition() {
        return definition;
    }

    public long getSpawnedAt() {
        return spawnedAt;
    }

    public long getSpawnOrder() {
        return spawnOrder;
    }

    public boolean markSettled() {
        if (settled) {
            return false;
        }
        settled = true;
        return true;
    }

    public void rememberDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            lastKnownDisplayName = displayName;
        }
    }

    public void recordDamage(Player attacker, double amount) {
        if (attacker == null || amount <= 0.0D) {
            return;
        }
        damageRankingTracker.recordDamage(
            attacker.getUniqueId(),
            resolvePlayerName(attacker),
            amount
        );
    }

    public void recordTakenDamage(Player target, double amount) {
        if (target == null || amount <= 0.0D) {
            return;
        }
        damageRankingTracker.recordTakenDamage(
            target.getUniqueId(),
            resolvePlayerName(target),
            amount
        );
    }

    public LivingEntity resolveEntity(Server server) {
        if (server.getEntity(entityUuid) instanceof LivingEntity livingEntity) {
            String customName = livingEntity.getCustomName();
            if (customName != null && !customName.isBlank()) {
                lastKnownDisplayName = customName;
            }
            return livingEntity;
        }
        return null;
    }

    public boolean canView(Player player, LivingEntity entity, PluginConfiguration configuration) {
        if (!player.isOnline() || player.isDead()) {
            return false;
        }
        if (!player.getWorld().equals(entity.getWorld())) {
            return false;
        }
        double range = definition.effectiveRange(configuration.defaultViewerRange());
        return player.getLocation().distanceSquared(entity.getLocation()) <= range * range;
    }

    public BossRenderState captureRenderState(LivingEntity entity, double defaultViewerRange) {
        double maxHealth = resolveMaxHealth(entity);
        double health = resolveCurrentHealth(entity, maxHealth);
        double progress = maxHealth <= 0.0D ? 0.0D : health / maxHealth;
        Location location = entity.getLocation();
        String displayName = resolveDisplayName(entity);
        BossTargetSnapshot target = resolveTargetSnapshot(entity);
        double viewerRange = definition.effectiveRange(defaultViewerRange);

        return new BossRenderState(
            entityUuid,
            mythicMobId,
            spawnOrder,
            spawnedAt,
            displayName,
            location,
            target,
            health,
            maxHealth,
            progress,
            viewerRange * viewerRange
        );
    }

    public boolean canView(Player player, BossRenderState renderState) {
        if (!player.isOnline() || player.isDead()) {
            return false;
        }

        World bossWorld = renderState.location().getWorld();
        if (bossWorld == null || !player.getWorld().equals(bossWorld)) {
            return false;
        }

        return player.getLocation().distanceSquared(renderState.location()) <= renderState.viewerRangeSquared();
    }

    public Map<String, Object> toUiPacket(Player viewer, BossRenderState renderState) {
        BossViewSlot slot = toViewSlot(viewer, renderState);
        BossPlaceholderContext context = slot.context();
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("title", slot.title());
        packet.put("subtitle", slot.subtitle());
        packet.put("displayName", context.displayName());
        packet.put("health", context.health());
        packet.put("maxHealth", context.maxHealth());
        packet.put("healthText", context.healthText());
        packet.put("maxHealthText", context.maxHealthText());
        packet.put("healthPercent", context.healthPercent());
        packet.put("healthPercentText", context.healthPercentText());
        packet.put("distanceText", context.distanceText());
        packet.put("distance", context.distance());
        packet.put("progress", context.progress());
        packet.put("mobId", context.mythicMobId());
        packet.put("entityUuid", context.entityUuid());
        packet.put("hasTarget", context.hasTarget());
        packet.put("targetDisplayName", context.targetDisplayName());
        packet.put("targetUuid", context.targetUuid());
        packet.put("targetType", context.targetType());
        packet.put("spawnOrder", context.spawnOrder());
        packet.put("priority", context.priority());
        packet.put("world", context.world());
        packet.put("x", context.x());
        packet.put("y", context.y());
        packet.put("z", context.z());
        packet.put("aliveSeconds", context.aliveSeconds());
        packet.put("aliveTime", context.aliveTime());
        appendRankingPacket(packet, context);
        return packet;
    }

    public Map<String, String> toChatCardData(Player viewer, LivingEntity entity, String state) {
        double maxHealth = resolveMaxHealth(entity);
        double health = resolveCurrentHealth(entity, maxHealth);
        Location location = entity.getLocation();
        String displayName = resolveDisplayName(entity);
        BossPlaceholderContext context = createPlaceholderContext(
            viewer,
            location,
            displayName,
            health,
            maxHealth,
            resolveTargetSnapshot(entity)
        );

        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", definition.renderTitle(context));
        data.put("subtitle", definition.renderSubtitle(context));
        data.put("mob_id", mythicMobId);
        data.put("display_name", context.displayName());
        data.put("has_target", Boolean.toString(context.hasTarget()));
        data.put("target_name", context.targetDisplayName());
        data.put("target_display_name", context.targetDisplayName());
        data.put("target_uuid", context.targetUuid());
        data.put("target_type", context.targetType());
        data.put("health", context.healthText());
        data.put("max_health", context.maxHealthText());
        data.put("health_percent", context.healthPercentText());
        data.put("distance", Integer.toString(context.distance()));
        data.put("distance_text", context.distanceText());
        data.put("spawn_order", Long.toString(context.spawnOrder()));
        data.put("priority", Integer.toString(context.priority()));
        data.put("world", context.world());
        data.put("x", Integer.toString(context.x()));
        data.put("y", Integer.toString(context.y()));
        data.put("z", Integer.toString(context.z()));
        data.put("alive_seconds", Long.toString(context.aliveSeconds()));
        data.put("alive_time", context.aliveTime());
        appendRankingData(data, context);
        data.put("state", state);
        return data;
    }

    public BossViewSlot toViewSlot(Player viewer, BossRenderState renderState) {
        BossPlaceholderContext context = createPlaceholderContext(
            viewer,
            renderState.location(),
            renderState.displayName(),
            renderState.health(),
            renderState.maxHealth(),
            renderState.target()
        );
        return new BossViewSlot(
            definition.renderTitle(context),
            definition.renderSubtitle(context),
            context
        );
    }

    public BossSettlementSnapshot createSettlementSnapshot(LivingEntity entity) {
        double maxHealth = resolveMaxHealth(entity);
        double health = resolveCurrentHealth(entity, maxHealth);
        String displayName = resolveDisplayName(entity);
        BossDamageRankingSnapshot rankingSnapshot = damageRankingTracker.snapshot(maxHealth);
        return new BossSettlementSnapshot(
            entityUuid,
            mythicMobId,
            displayName == null || displayName.isBlank() ? mythicMobId : displayName,
            health,
            maxHealth,
            spawnedAt,
            spawnOrder,
            rankingSnapshot,
            definition.damageRanking().rewards()
        );
    }

    public record BossRenderState(
        UUID entityUuid,
        String mythicMobId,
        long spawnOrder,
        long spawnedAt,
        String displayName,
        Location location,
        BossTargetSnapshot target,
        double health,
        double maxHealth,
        double progress,
        double viewerRangeSquared
    ) {
    }

    public record BossSettlementSnapshot(
        UUID entityUuid,
        String mythicMobId,
        String displayName,
        double health,
        double maxHealth,
        long spawnedAt,
        long spawnOrder,
        BossDamageRankingSnapshot ranking,
        BossDamageRankingRewardsSettings rewards
    ) {
    }

    private String resolveDisplayName(LivingEntity entity) {
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            lastKnownDisplayName = customName;
            return customName;
        }
        if (lastKnownDisplayName != null && !lastKnownDisplayName.isBlank()) {
            return lastKnownDisplayName;
        }
        return mythicMobId;
    }

    private static double resolveMaxHealth(LivingEntity entity) {
        try {
            var activeMob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
            if (activeMob != null && activeMob.getEntity() != null) {
                return Math.max(activeMob.getEntity().getMaxHealth(), 1.0D);
            }
        } catch (Exception | LinkageError ignored) {
            // MythicMobs 重载或暂时不可用时，回退到 Bukkit 实体属性。
        }
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return Math.max(entity.getHealth(), 1.0D);
        }
        return Math.max(attribute.getValue(), 1.0D);
    }

    private static double resolveCurrentHealth(LivingEntity entity, double maxHealth) {
        try {
            var activeMob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
            if (activeMob != null && activeMob.getEntity() != null) {
                return Math.max(0.0D, Math.min(activeMob.getEntity().getHealth(), maxHealth));
            }
        } catch (Exception | LinkageError ignored) {
            // MythicMobs 重载或暂时不可用时，回退到 Bukkit 实体血量。
        }
        return Math.max(0.0D, Math.min(entity.getHealth(), maxHealth));
    }

    private BossPlaceholderContext createPlaceholderContext(
        Player viewer,
        Location location,
        String displayName,
        double health,
        double maxHealth,
        BossTargetSnapshot target
    ) {
        int distance = 0;
        if (location.getWorld() != null && viewer.getWorld().equals(location.getWorld())) {
            distance = (int) Math.round(Math.sqrt(viewer.getLocation().distanceSquared(location)));
        }
        long aliveSeconds = Math.max(0L, (System.currentTimeMillis() - spawnedAt) / 1000L);
        String worldName = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        double healthPercent = maxHealth <= 0.0D ? 0.0D : (health / maxHealth) * 100.0D;
        double progress = maxHealth <= 0.0D ? 0.0D : health / maxHealth;
        BossDamageRankingSnapshot damageRanking = damageRankingTracker.snapshot(maxHealth);
        BossDamageRankingEntry viewerDamageEntry = damageRanking.entry(viewer.getUniqueId());
        return new BossPlaceholderContext(
            true,
            displayName == null || displayName.isBlank() ? mythicMobId : displayName,
            mythicMobId,
            entityUuid.toString(),
            target.hasTarget(),
            target.displayName(),
            target.uuid(),
            target.type(),
            health,
            maxHealth,
            healthPercent,
            progress,
            distance,
            spawnOrder,
            definition.priority(),
            worldName,
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            aliveSeconds,
            damageRanking,
            viewerDamageEntry
        );
    }

    private void appendRankingPacket(Map<String, Object> packet, BossPlaceholderContext context) {
        BossDamageRankingSnapshot ranking = context.damageRanking();
        BossDamageRankingEntry viewerEntry = context.viewerDamageEntry();
        packet.put("rankingEnabled", ranking.enabled());
        packet.put("damageParticipantCount", ranking.participantCount());
        packet.put("damageTrackedPlayerCount", ranking.trackedPlayerCount());
        packet.put("totalDamage", ranking.totalDamage());
        packet.put("totalDamageText", context.totalDamageText());
        packet.put("viewerRank", viewerEntry.rank());
        packet.put("viewerRankText", context.viewerRankText());
        packet.put("viewerQualified", viewerEntry.qualified());
        packet.put("viewerDamage", viewerEntry.damage());
        packet.put("viewerDamageText", context.viewerDamageText());
        packet.put("viewerDamagePercent", viewerEntry.damagePercent());
        packet.put("viewerDamagePercentText", context.viewerDamagePercentText());
        packet.put("viewerTakenDamage", viewerEntry.takenDamage());
        packet.put("viewerTakenDamageText", context.viewerTakenDamageText());
        for (int rank = 1; rank <= ranking.entryLimit(); rank++) {
            BossDamageRankingEntry topEntry = ranking.entry(rank);
            String prefix = "top" + rank;
            packet.put(prefix + "Name", topEntry.playerName());
            packet.put(prefix + "Uuid", isEmptyEntry(topEntry) ? "" : topEntry.playerUuid().toString());
            packet.put(prefix + "Rank", topEntry.rank());
            packet.put(prefix + "Qualified", topEntry.qualified());
            packet.put(prefix + "Damage", topEntry.damage());
            packet.put(prefix + "DamageText", formatNumber(topEntry.damage()));
            packet.put(prefix + "DamagePercent", topEntry.damagePercent());
            packet.put(prefix + "DamagePercentText", formatNumber(topEntry.damagePercent()));
            packet.put(prefix + "TakenDamage", topEntry.takenDamage());
            packet.put(prefix + "TakenDamageText", formatNumber(topEntry.takenDamage()));
        }
    }

    private void appendRankingData(Map<String, String> data, BossPlaceholderContext context) {
        BossDamageRankingSnapshot ranking = context.damageRanking();
        BossDamageRankingEntry viewerEntry = context.viewerDamageEntry();
        data.put("ranking_enabled", Boolean.toString(ranking.enabled()));
        data.put("ranking_entry_limit", Integer.toString(ranking.entryLimit()));
        data.put("damage_participant_count", Integer.toString(ranking.participantCount()));
        data.put("damage_tracked_player_count", Integer.toString(ranking.trackedPlayerCount()));
        data.put("total_damage", context.totalDamageText());
        data.put("viewer_rank", Integer.toString(viewerEntry.rank()));
        data.put("viewer_rank_text", context.viewerRankText());
        data.put("viewer_qualified", Boolean.toString(viewerEntry.qualified()));
        data.put("viewer_damage", context.viewerDamageText());
        data.put("viewer_damage_percent", context.viewerDamagePercentText());
        data.put("viewer_taken_damage", context.viewerTakenDamageText());
        for (int rank = 1; rank <= ranking.entryLimit(); rank++) {
            BossDamageRankingEntry entry = ranking.entry(rank);
            String prefix = "top_" + rank + "_";
            data.put(prefix + "name", entry.playerName());
            data.put(prefix + "display_name", entry.playerName());
            data.put(prefix + "uuid", isEmptyEntry(entry) ? "" : entry.playerUuid().toString());
            data.put(prefix + "rank", Integer.toString(entry.rank()));
            data.put(prefix + "qualified", Boolean.toString(entry.qualified()));
            data.put(prefix + "damage", formatNumber(entry.damage()));
            data.put(prefix + "damage_percent", formatNumber(entry.damagePercent()));
            data.put(prefix + "taken_damage", formatNumber(entry.takenDamage()));
        }
    }

    private BossTargetSnapshot resolveTargetSnapshot(LivingEntity entity) {
        BossTargetSnapshot target = resolveMythicTarget(entity);
        if (target.hasTarget()) {
            return target;
        }
        if (entity instanceof Mob mob) {
            return BossTargetSnapshot.fromEntity(mob.getTarget());
        }
        return BossTargetSnapshot.empty();
    }

    private static String resolvePlayerName(Player player) {
        if (player == null) {
            return "";
        }
        String displayName = player.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return player.getName();
    }

    private static boolean isEmptyEntry(BossDamageRankingEntry entry) {
        return entry == null || entry == BossDamageRankingEntry.empty();
    }

    private static String formatNumber(double value) {
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }

    private static BossTargetSnapshot resolveMythicTarget(LivingEntity entity) {
        try {
            ActiveMob activeMob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
            if (activeMob == null) {
                return BossTargetSnapshot.empty();
            }

            BossTargetSnapshot directTarget = activeMob.getEntity() == null
                ? BossTargetSnapshot.empty()
                : BossTargetSnapshot.fromEntity(activeMob.getEntity().getTarget());
            if (directTarget.hasTarget()) {
                return directTarget;
            }

            if (activeMob.hasThreatTable() && activeMob.getThreatTable() != null) {
                BossTargetSnapshot threatTarget = BossTargetSnapshot.fromEntity(activeMob.getThreatTable().getTopThreatHolder());
                if (threatTarget.hasTarget()) {
                    return threatTarget;
                }
            }
        } catch (Exception | LinkageError ignored) {
            // MythicMobs 重载或暂时不可用时，回退到 Bukkit 目标。
        }
        return BossTargetSnapshot.empty();
    }

    private record BossTargetSnapshot(boolean hasTarget, String displayName, String uuid, String type) {

        private static final BossTargetSnapshot EMPTY = new BossTargetSnapshot(false, "", "", "");

        private static BossTargetSnapshot empty() {
            return EMPTY;
        }

        private static BossTargetSnapshot fromEntity(io.lumine.mythic.api.adapters.AbstractEntity entity) {
            if (entity == null) {
                return EMPTY;
            }
            return fromEntity(entity.getBukkitEntity());
        }

        private static BossTargetSnapshot fromEntity(Entity entity) {
            if (entity == null || !entity.isValid() || entity.isDead()) {
                return EMPTY;
            }
            return new BossTargetSnapshot(
                true,
                resolveDisplayName(entity),
                entity.getUniqueId().toString(),
                entity.getType().name().toLowerCase(Locale.ROOT)
            );
        }

        private static String resolveDisplayName(Entity entity) {
            if (entity instanceof Player player) {
                String displayName = player.getDisplayName();
                if (displayName != null && !displayName.isBlank()) {
                    return displayName;
                }
                return player.getName();
            }
            if (entity instanceof LivingEntity livingEntity) {
                String customName = livingEntity.getCustomName();
                if (customName != null && !customName.isBlank()) {
                    return customName;
                }
            }
            return entity.getName();
        }
    }
}

