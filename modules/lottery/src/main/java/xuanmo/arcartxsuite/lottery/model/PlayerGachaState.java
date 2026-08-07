package xuanmo.arcartxsuite.lottery.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家在某个 GACHA 奖池的抽卡状态。
 *
 * @param playerUuid   玩家 UUID
 * @param poolId       奖池 ID（共享保底组时为组名）
 * @param pity5star    5 星保底当前抽数
 * @param pity4star    4 星保底当前抽数
 * @param guaranteedUp 下次 5 星是否大保底（必出 UP）
 * @param fatePoints   当前命运点数（武器池定轨）
 * @param fateTarget   定轨目标物品 ID，未定轨时为 {@code null}
 */
public record PlayerGachaState(
    @NotNull UUID playerUuid,
    @NotNull String poolId,
    int pity5star,
    int pity4star,
    boolean guaranteedUp,
    int fatePoints,
    @Nullable String fateTarget
) {
    public PlayerGachaState {
        if (pity5star < 0) pity5star = 0;
        if (pity4star < 0) pity4star = 0;
        if (fatePoints < 0) fatePoints = 0;
    }

    /**
     * 创建初始空状态（保底计数为 0，无大保底，无定轨）。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @return 初始状态实例
     */
    public static PlayerGachaState empty(@NotNull UUID playerUuid, @NotNull String poolId) {
        return new PlayerGachaState(playerUuid, poolId, 0, 0, false, 0, null);
    }
}
