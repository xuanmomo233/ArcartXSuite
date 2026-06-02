package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;

public record BossDamageSettlementRecord(
    String settlementId,
    long settledAtMillis,
    String mythicMobId,
    String bossDisplayName,
    String entityUuid,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    BossDamageRankingRewardsSettings rewardsSettings,
    List<BossDamageSettlementEntry> trackedEntries,
    Map<UUID, BossDamageSettlementEntry> entriesByPlayer
) {

    private static final BossDamageSettlementRecord EMPTY = new BossDamageSettlementRecord(
        "",
        0L,
        "",
        "",
        "",
        0,
        0,
        0.0D,
        BossDamageRankingRewardsSettings.defaults(),
        List.of(),
        Map.of()
    );

    public static BossDamageSettlementRecord empty() {
        return EMPTY;
    }

    public BossDamageSettlementEntry entry(int rank) {
        if (rank <= 0) {
            return BossDamageSettlementEntry.empty();
        }
        for (BossDamageSettlementEntry entry : trackedEntries) {
            if (entry.rank() == rank) {
                return entry;
            }
        }
        return BossDamageSettlementEntry.empty();
    }

    public BossDamageSettlementEntry entry(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamageSettlementEntry.empty();
        }
        return entriesByPlayer.getOrDefault(playerUuid, BossDamageSettlementEntry.empty());
    }

    public BossDamageSettlementEntry topEntry() {
        return entry(1);
    }

    public List<BossDamageSettlementEntry> rankedEntries() {
        return trackedEntries.stream()
            .filter(entry -> entry.rank() > 0)
            .toList();
    }

    public String rewardSummary() {
        if (trackedEntries.isEmpty()) {
            return "无结算记录";
        }

        int successCount = 0;
        int failureCount = 0;
        for (BossDamageSettlementEntry entry : trackedEntries) {
            if (entry.rank() <= 0) {
                continue;
            }
            if (entry.rewarded()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        return "成功=" + successCount + " 失败=" + failureCount;
    }
}

