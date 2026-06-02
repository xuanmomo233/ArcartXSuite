package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.text.DecimalFormat;
import java.util.Locale;

public record BossDamageSettlementPlaceholderContext(
    BossDamageSettlementRecord settlement,
    BossDamageSettlementEntry viewerEntry
) {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    public static BossDamageSettlementPlaceholderContext empty() {
        return new BossDamageSettlementPlaceholderContext(
            BossDamageSettlementRecord.empty(),
            BossDamageSettlementEntry.empty()
        );
    }

    public String resolve(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
        String directValue = switch (normalized) {
            case "settlement_id" -> settlement.settlementId();
            case "boss_id" -> settlement.mythicMobId();
            case "boss_display_name" -> settlement.bossDisplayName();
            case "entity_uuid" -> settlement.entityUuid();
            case "participant_count" -> Integer.toString(settlement.participantCount());
            case "tracked_player_count" -> Integer.toString(settlement.trackedPlayerCount());
            case "total_damage" -> formatNumber(settlement.totalDamage());
            case "rank" -> Integer.toString(viewerEntry.rank());
            case "rank_text" -> viewerEntry.rankText();
            case "damage" -> formatNumber(viewerEntry.damage());
            case "damage_percent" -> formatNumber(viewerEntry.damagePercent());
            case "taken_damage" -> formatNumber(viewerEntry.takenDamage());
            case "rewarded" -> Boolean.toString(viewerEntry.rewarded());
            case "reward_failure" -> viewerEntry.rewardFailure();
            default -> null;
        };
        if (directValue != null) {
            return directValue;
        }
        return resolveRankEntry(normalized);
    }

    private String resolveRankEntry(String fieldName) {
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

        BossDamageSettlementEntry entry = settlement.entry(rank);
        String key = parts[2];
        return switch (key) {
            case "name" -> entry.playerName();
            case "player_uuid" -> entry.playerUuid().getLeastSignificantBits() == 0L && entry.playerUuid().getMostSignificantBits() == 0L
                ? ""
                : entry.playerUuid().toString();
            case "rank" -> Integer.toString(entry.rank());
            case "qualified" -> Boolean.toString(entry.qualified());
            case "rewarded" -> Boolean.toString(entry.rewarded());
            case "reward_failure" -> entry.rewardFailure();
            case "damage" -> formatNumber(entry.damage());
            case "damage_percent" -> formatNumber(entry.damagePercent());
            case "taken_damage" -> formatNumber(entry.takenDamage());
            default -> null;
        };
    }

    private static String formatNumber(double value) {
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }
}

