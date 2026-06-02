package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BossDamageRankingSnapshot(
    boolean enabled,
    int entryLimit,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    List<BossDamageRankingEntry> rankedEntries,
    List<BossDamageRankingEntry> trackedEntries,
    Map<UUID, BossDamageRankingEntry> entriesByPlayer
) {

    public static BossDamageRankingSnapshot empty(boolean enabled, int entryLimit) {
        return new BossDamageRankingSnapshot(
            enabled,
            Math.max(1, entryLimit),
            0,
            0,
            0.0D,
            List.of(),
            List.of(),
            Map.of()
        );
    }

    public BossDamageRankingEntry entry(int rank) {
        int zeroBased = rank - 1;
        if (zeroBased < 0 || zeroBased >= rankedEntries.size()) {
            return BossDamageRankingEntry.empty();
        }
        return rankedEntries.get(zeroBased);
    }

    public BossDamageRankingEntry entry(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamageRankingEntry.empty();
        }
        return entriesByPlayer.getOrDefault(playerUuid, BossDamageRankingEntry.empty());
    }
}

