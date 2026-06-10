package xuanmo.arcartxsuite.battlepass.model;

import java.util.Map;

public record BattlePassReward(
    String rewardId,
    int level,
    RewardTier tier,
    String type,
    Map<String, String> data
) {

    public enum RewardTier {
        FREE, PREMIUM, DELUXE
    }
}
