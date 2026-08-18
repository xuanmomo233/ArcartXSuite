package xuanmo.arcartxsuite.battlepass.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 赛季结算奖励 record，描述按等级阈值发放的额外结算奖励。
 * <p>
 * 与 {@link BattlePassReward} 不同，结算奖励按 {@code min-level} 阈值判定，
 * 玩家达到该等级即可在赛季结算时获得对应档位的奖励。
 *
 * @param settlementId 结算奖励唯一标识
 * @param minLevel     最低等级阈值（玩家等级 ≥ 此值才发放）
 * @param free         免费档奖励，可为 null
 * @param premium      高级档奖励，可为 null
 * @param deluxe       典藏档奖励，可为 null
 */
public record SeasonSettlementReward(
    String settlementId,
    int minLevel,
    @Nullable BattlePassReward free,
    @Nullable BattlePassReward premium,
    @Nullable BattlePassReward deluxe
) {
}
