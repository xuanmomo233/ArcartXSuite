package xuanmo.arcartxsuite.chat.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 红包记录，对应 {@code chat_red_packets} 表的一行。
 * <p>
 * 普通/拼手气红包的每份金额在创建时预拆分并保存在 {@code amounts} 中，
 * 领取序号即 {@code claimedCount}；奖励池红包的奖项另存于 {@code chat_red_packet_entries} 表。
 *
 * @param id              红包唯一 ID（8 位随机码）
 * @param type            红包类型
 * @param senderUuid      发送者 UUID，控制台发放时为 {@code null}
 * @param senderName      发送者名（控制台为 "Console" 或自定义）
 * @param senderDisplay   发送者显示名
 * @param channelId       广播频道 ID
 * @param currencyId      货币 ID（奖励池红包可为空串）
 * @param totalAmount     货币总额（奖励池红包为 0）
 * @param remainingAmount 剩余未领货币金额（过期退款依据）
 * @param amounts         预拆分的每份金额列表（普通/拼手气红包）
 * @param totalCount      总份数
 * @param claimedCount    已领取份数
 * @param wish            祝福语
 * @param presetId        预设 ID（奖励池红包）
 * @param admin           是否为管理员/预设红包（免扣款，过期不退款）
 * @param status          状态：active / exhausted / expired
 * @param createdAt       创建时间
 * @param expiresAt       过期时间
 * @param originNode      发起节点 ID（跨服）
 */
public record RedPacket(
    String id,
    RedPacketType type,
    @Nullable String senderUuid,
    String senderName,
    String senderDisplay,
    String channelId,
    String currencyId,
    BigDecimal totalAmount,
    BigDecimal remainingAmount,
    List<BigDecimal> amounts,
    int totalCount,
    int claimedCount,
    String wish,
    String presetId,
    boolean admin,
    String status,
    Instant createdAt,
    Instant expiresAt,
    String originNode,
    String audienceKey,
    boolean refundOnExpire
) {

    public static final String STATUS_ACTIVE = "active_v2";
    public static final String STATUS_EXHAUSTED = "exhausted";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_DEBITING = "debiting";
    public static final String STATUS_CANCELLED = "cancelled";

    public RedPacket withStatus(String value) {
        return new RedPacket(id, type, senderUuid, senderName, senderDisplay, channelId, currencyId,
            totalAmount, remainingAmount, amounts, totalCount, claimedCount, wish, presetId, admin,
            value, createdAt, expiresAt, originNode, audienceKey, refundOnExpire);
    }

    /**
     * 红包当前是否可领取。
     *
     * @param now 当前时间
     * @return {@code true} 表示可领取
     */
    public boolean claimable(Instant now) {
        return STATUS_ACTIVE.equals(status) && claimedCount < totalCount && expiresAt.isAfter(now);
    }

    /**
     * 红包是否已过期（按时间判断，与状态无关）。
     */
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
