package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.List;

public record BossDamageRankRewardDefinition(
    int rank,
    List<BossDamageRewardAction> actions
) {

    public static BossDamageRankRewardDefinition empty(int rank) {
        return new BossDamageRankRewardDefinition(Math.max(1, rank), List.of());
    }
}

