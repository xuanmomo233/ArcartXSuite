package xuanmo.arcartxsuite.lottery.model;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * GACHA 抽卡结果。
 *
 * @param items            本次获得的奖品列表
 * @param finalState       抽卡结束后的玩家状态（已更新保底计数等）
 * @param guaranteed       本次是否触发大保底
 * @param fatePointTriggered 本次是否触发命运点定轨命中
 */
public record GachaResult(
    @NotNull List<PoolItem> items,
    @NotNull PlayerGachaState finalState,
    boolean guaranteed,
    boolean fatePointTriggered
) {
}
