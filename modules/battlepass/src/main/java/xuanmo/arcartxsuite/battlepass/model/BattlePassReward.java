package xuanmo.arcartxsuite.battlepass.model;

import java.util.Map;

/**
 * 战令奖励 record，描述某个等级与档位对应的具体奖励内容。
 *
 * @param rewardId 奖励唯一标识
 * @param level    所属等级
 * @param tier     奖励档位（FREE/PREMIUM/DELUXE）
 * @param type     奖励类型（如 command）
 * @param data     奖励数据键值对（如命令文本）
 */
public record BattlePassReward(
    String rewardId,
    int level,
    RewardTier tier,
    String type,
    Map<String, String> data
) {

    /**
     * 奖励档位枚举，与战令档位（{@link BattlePassPlayerProgress.PassTier}）对应。
     */
    public enum RewardTier {
        FREE, PREMIUM, DELUXE
    }
}
