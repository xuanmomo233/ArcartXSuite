package xuanmo.arcartxsuite.chat.model;

import java.math.BigDecimal;
import org.jetbrains.annotations.Nullable;

/**
 * 红包领取事务结果。
 * <p>
 * {@link #status} 表示领取状态；{@link #reward} 为抽中的奖励内容（成功时非空）。
 *
 * @param status        领取状态
 * @param packet        红包快照（领取后的状态），红包不存在时为 {@code null}
 * @param reward        本次领取的奖励
 * @param slotIndex     领取序号（0 基）
 * @param currencyAmount 领取的货币金额（用于手气王判定）
 */
public record RedPacketClaimOutcome(
    Status status,
    @Nullable RedPacket packet,
    @Nullable RedPacketReward reward,
    int slotIndex,
    @Nullable BigDecimal currencyAmount
) {

    /**
     * 领取状态枚举。
     */
    public enum Status {
        /** 领取成功。 */
        SUCCESS,
        /** 红包不存在。 */
        NOT_FOUND,
        /** 红包已过期。 */
        EXPIRED,
        /** 红包已抢完。 */
        EXHAUSTED,
        /** 该玩家已领取过。 */
        ALREADY_CLAIMED,
        /** 发送者不允许抢自己的红包。 */
        SENDER_DENIED,
        /** 频道、来源节点或受众校验未通过。 */
        NOT_ELIGIBLE
    }

    public static RedPacketClaimOutcome success(RedPacket packet, RedPacketReward reward, int slotIndex, BigDecimal currencyAmount) {
        return new RedPacketClaimOutcome(Status.SUCCESS, packet, reward, slotIndex, currencyAmount);
    }

    public static RedPacketClaimOutcome of(Status status, @Nullable RedPacket packet) {
        return new RedPacketClaimOutcome(status, packet, null, -1, null);
    }

    public static RedPacketClaimOutcome of(Status status) {
        return new RedPacketClaimOutcome(status, null, null, -1, null);
    }
}
