package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 待发放的钓鱼奖励（用于离线玩家赛季结算/等级奖励补发）。
 * <p>
 * 当发放策略为 ONLINE_DIRECT 且玩家离线时，奖励暂存到此记录，
 * 玩家上线时由 FishingService 检查并发放。
 *
 * @param id         记录唯一 ID（自增）
 * @param uuid       玩家 UUID
 * @param seasonId   赛季 ID
 * @param rewardType 奖励类型：CURRENCY / ITEM / COMMAND
 * @param currencyId 货币 ID（仅 CURRENCY 类型有效）
 * @param amount     货币数量（仅 CURRENCY 类型有效）
 * @param itemData   物品序列化数据（仅 ITEM 类型有效）
 * @param command    委托执行的命令（仅 COMMAND 类型有效）
 * @param description 奖励描述
 * @param createdAt  创建时间戳（毫秒）
 */
public record FishingPendingReward(
    long id,
    @NotNull UUID uuid,
    @NotNull String seasonId,
    @NotNull String rewardType,
    @Nullable String currencyId,
    double amount,
    @Nullable String itemData,
    @Nullable String command,
    @NotNull String description,
    long createdAt
) {

    public enum RewardType {
        CURRENCY, ITEM, COMMAND
    }
}
