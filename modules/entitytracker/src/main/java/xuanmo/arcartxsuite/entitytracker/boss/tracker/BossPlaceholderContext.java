package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.text.DecimalFormat;
import java.util.Locale;

public record BossPlaceholderContext(
    boolean visible,
    String displayName,
    String mythicMobId,
    String entityUuid,
    boolean hasTarget,
    String targetDisplayName,
    String targetUuid,
    String targetType,
    double health,
    double maxHealth,
    double healthPercent,
    double progress,
    int distance,
    long spawnOrder,
    int priority,
    String world,
    int x,
    int y,
    int z,
    long aliveSeconds,
    BossDamageRankingSnapshot damageRanking,
    BossDamageRankingEntry viewerDamageEntry
) {

    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.##"));

    public static BossPlaceholderContext empty() {
        return new BossPlaceholderContext(
            false,
            "",
            "",
            "",
            false,
            "",
            "",
            "",
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0,
            0L,
            0,
            "",
            0,
            0,
            0,
            0L,
            BossDamageRankingSnapshot.empty(false, 1),
            BossDamageRankingEntry.empty()
        );
    }

    public String resolve(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
        String directValue = switch (normalized) {
            case "visible" -> Boolean.toString(visible);
            case "display_name" -> displayName;
            case "mob_id" -> mythicMobId;
            case "mob_id_lower" -> mythicMobId.toLowerCase(Locale.ROOT);
            case "entity_uuid" -> entityUuid;
            case "has_target" -> Boolean.toString(hasTarget);
            case "target_name", "target_display_name" -> targetDisplayName;
            case "target_uuid" -> targetUuid;
            case "target_type" -> targetType;
            case "health" -> healthText();
            case "max_health" -> maxHealthText();
            case "health_percent" -> healthPercentText();
            case "progress" -> progressText();
            case "distance" -> Integer.toString(distance);
            case "distance_text" -> distanceText();
            case "spawn_order" -> Long.toString(spawnOrder);
            case "priority" -> Integer.toString(priority);
            case "world" -> world;
            case "x" -> Integer.toString(x);
            case "y" -> Integer.toString(y);
            case "z" -> Integer.toString(z);
            case "alive_seconds" -> Long.toString(aliveSeconds);
            case "alive_time" -> aliveTime();
            case "ranking_enabled" -> Boolean.toString(damageRanking.enabled());
            case "ranking_entry_limit" -> Integer.toString(damageRanking.entryLimit());
            case "damage_participant_count" -> Integer.toString(damageRanking.participantCount());
            case "damage_tracked_player_count" -> Integer.toString(damageRanking.trackedPlayerCount());
            case "total_damage" -> totalDamageText();
            case "viewer_rank" -> Integer.toString(viewerDamageEntry.rank());
            case "viewer_rank_text" -> viewerRankText();
            case "viewer_qualified" -> Boolean.toString(viewerDamageEntry.qualified());
            case "viewer_damage" -> viewerDamageText();
            case "viewer_damage_percent" -> viewerDamagePercentText();
            case "viewer_taken_damage" -> viewerTakenDamageText();
            default -> null;
        };
        if (directValue != null) {
            return directValue;
        }
        return resolveRankedEntry(normalized);
    }

    public String healthText() {
        return formatNumber(health);
    }

    public String maxHealthText() {
        return formatNumber(maxHealth);
    }

    public String healthPercentText() {
        return formatNumber(healthPercent);
    }

    public String progressText() {
        return formatNumber(progress);
    }

    public String totalDamageText() {
        return formatNumber(damageRanking.totalDamage());
    }

    public String viewerDamageText() {
        return formatNumber(viewerDamageEntry.damage());
    }

    public String viewerDamagePercentText() {
        return formatNumber(viewerDamageEntry.damagePercent());
    }

    public String viewerTakenDamageText() {
        return formatNumber(viewerDamageEntry.takenDamage());
    }

    public String viewerRankText() {
        return viewerDamageEntry.rank() > 0 ? "#" + viewerDamageEntry.rank() : "-";
    }

    public String distanceText() {
        return distance + "m";
    }

    public String aliveTime() {
        long totalSeconds = Math.max(0L, aliveSeconds);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + ":" + pad2(minutes) + ":" + pad2(seconds);
        }
        return pad2(minutes) + ":" + pad2(seconds);
    }

    private static String pad2(long value) {
        return value < 10L ? "0" + value : Long.toString(value);
    }

    private static String formatNumber(double value) {
        return NUMBER_FORMAT.get().format(value);
    }

    private String resolveRankedEntry(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        if (!fieldName.startsWith("top_")) {
            return null;
        }

        String[] parts = fieldName.split("_", 3);
        if (parts.length < 3) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return null;
        }

        BossDamageRankingEntry entry = damageRanking.entry(rank);
        String key = parts[2];
        return switch (key) {
            case "name" -> entry.playerName();
            case "player_uuid" -> entry.playerUuid().getLeastSignificantBits() == 0L && entry.playerUuid().getMostSignificantBits() == 0L
                ? ""
                : entry.playerUuid().toString();
            case "rank" -> Integer.toString(entry.rank());
            case "qualified" -> Boolean.toString(entry.qualified());
            case "damage" -> formatNumber(entry.damage());
            case "damage_percent" -> formatNumber(entry.damagePercent());
            case "taken_damage" -> formatNumber(entry.takenDamage());
            default -> null;
        };
    }
}

