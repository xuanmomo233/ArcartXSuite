package xuanmo.arcartxsuite.chat.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * 红包领取记录，对应 {@code chat_red_packet_claims} 表的一行。
 *
 * @param packetId   红包 ID
 * @param playerUuid 领取者 UUID
 * @param playerName 领取者名
 * @param rewardDesc 奖励描述（用于播报）
 * @param amount     领取的货币金额（非货币奖励为 {@code null}）
 * @param claimedAt  领取时间
 */
public record RedPacketClaim(
    String packetId,
    String playerUuid,
    String playerName,
    String rewardDesc,
    @Nullable BigDecimal amount,
    Instant claimedAt
) {
}
