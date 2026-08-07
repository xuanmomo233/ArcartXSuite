package xuanmo.arcartxsuite.lottery.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家在某个 CASE 奖池的开箱状态。
 *
 * @param playerUuid   玩家 UUID
 * @param poolId       奖池 ID
 * @param openCount    累计开箱次数
 * @param lastOpenTime 上次开箱时间戳（毫秒）
 */
public record PlayerCaseState(
    @NotNull UUID playerUuid,
    @NotNull String poolId,
    int openCount,
    long lastOpenTime
) {
    public PlayerCaseState {
        if (openCount < 0) openCount = 0;
        if (lastOpenTime < 0) lastOpenTime = 0;
    }

    /**
     * 创建初始空状态（开箱次数为 0）。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @return 初始状态实例
     */
    public static PlayerCaseState empty(@NotNull UUID playerUuid, @NotNull String poolId) {
        return new PlayerCaseState(playerUuid, poolId, 0, 0);
    }
}
