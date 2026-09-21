package xuanmo.arcartxsuite.chat.model;

import java.math.BigDecimal;
import org.jetbrains.annotations.Nullable;

/**
 * 单份红包奖励，描述玩家领取一次红包所获得的内容。
 * <p>
 * 按 {@link #kind} 区分生效字段：货币奖励使用 {@code currencyId}+{@code amount}；
 * 物品奖励使用 {@code itemData}（Base64 序列化 ItemStack）；指令奖励使用 {@code command}。
 *
 * @param kind        奖励种类
 * @param currencyId  货币 ID（kind=CURRENCY 时有效）
 * @param amount      货币金额（kind=CURRENCY 时有效）
 * @param itemData    Base64 序列化物品数据（kind=ITEM 时有效）
 * @param command     待执行命令（kind=COMMAND 时有效，{player} 为领取者占位符）
 * @param description 奖励描述，用于播报（如 "100 金币"、"钻石 x1"）
 */
public record RedPacketReward(
    RedPacketRewardKind kind,
    String currencyId,
    @Nullable BigDecimal amount,
    String itemData,
    String command,
    String description
) {

    /**
     * 构造货币奖励。
     */
    public static RedPacketReward currency(String currencyId, BigDecimal amount, String description) {
        return new RedPacketReward(RedPacketRewardKind.CURRENCY, currencyId, amount, "", "", description);
    }

    /**
     * 构造物品奖励。
     */
    public static RedPacketReward item(String itemData, String description) {
        return new RedPacketReward(RedPacketRewardKind.ITEM, "", null, itemData, "", description);
    }

    /**
     * 构造指令奖励。
     */
    public static RedPacketReward command(String command, String description) {
        return new RedPacketReward(RedPacketRewardKind.COMMAND, "", null, "", command, description);
    }
}
