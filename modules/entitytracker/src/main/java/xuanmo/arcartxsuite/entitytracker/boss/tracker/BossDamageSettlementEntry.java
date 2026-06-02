package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.UUID;

public record BossDamageSettlementEntry(
    UUID playerUuid,
    String playerName,
    int rank,
    boolean qualified,
    double damage,
    double damagePercent,
    double takenDamage,
    boolean rewarded,
    String rewardFailure,
    List<BossDamageSettlementActionResult> actionResults
) {

    private static final BossDamageSettlementEntry EMPTY = new BossDamageSettlementEntry(
        new UUID(0L, 0L),
        "",
        0,
        false,
        0.0D,
        0.0D,
        0.0D,
        false,
        "",
        List.of()
    );

    public static BossDamageSettlementEntry empty() {
        return EMPTY;
    }

    public static BossDamageSettlementEntry fromRankingEntry(
        BossDamageRankingEntry entry,
        boolean rewarded,
        String rewardFailure,
        List<BossDamageSettlementActionResult> actionResults
    ) {
        if (entry == null) {
            return empty();
        }
        return new BossDamageSettlementEntry(
            entry.playerUuid(),
            entry.playerName(),
            entry.rank(),
            entry.qualified(),
            entry.damage(),
            entry.damagePercent(),
            entry.takenDamage(),
            rewarded,
            rewardFailure == null ? "" : rewardFailure,
            actionResults == null ? List.of() : List.copyOf(actionResults)
        );
    }

    public String rankText() {
        return rank > 0 ? "#" + rank : "-";
    }
}

