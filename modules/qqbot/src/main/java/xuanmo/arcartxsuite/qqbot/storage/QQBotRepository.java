package xuanmo.arcartxsuite.qqbot.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public interface QQBotRepository {

    void initialize() throws Exception;

    void close();

    void insertBinding(long qqId, UUID playerUuid, String playerName);

    void deleteBindingByQq(long qqId);

    void deleteBindingByPlayer(UUID playerUuid);

    @Nullable
    QQBotBinding findByQq(long qqId);

    @Nullable
    QQBotBinding findByPlayer(UUID playerUuid);

    @Nullable
    QQBotBinding findByPlayerName(String playerName);

    List<QQBotBinding> findAllByQq(long qqId);

    int countByQq(long qqId);

    int countBindings();

    List<QQBotBinding> getBindingsPage(int page, int pageSize);

    List<QQBotBinding> searchBindings(String keyword, int limit);

    // ─── 签到积分 ────────────────────────────────────────

    /** 获取积分余额（账户不存在返回 0） */
    int getPoints(long qqId);

    /** 获取完整积分账户（不存在返回 null） */
    @Nullable
    PointAccount getPointAccount(long qqId);

    /**
     * 增加积分（原子 upsert + 写流水）。
     *
     * @return 操作后的新余额
     */
    int addPoints(long qqId, int delta, String action, String detail);

    /**
     * 扣减积分（余额不足返回 false，不扣减）。
     *
     * @return true 表示扣减成功
     */
    boolean deductPoints(long qqId, int amount, String action, String detail);

    /** 获取最近一次签到记录（不存在返回 null） */
    @Nullable
    SignInRecord getLastSignIn(long qqId);

    /** 指定日期是否已签到 */
    boolean hasSignedOn(long qqId, String date);

    /** 写入一条签到记录 */
    void recordSignIn(long qqId, String date, int streak, long signedAt);

    /** 积分排行榜（按余额降序） */
    List<PointAccount> getPointsLeaderboard(int limit);

    /** 今日兑换某奖品的次数（用于限购） */
    int countRedeemToday(long qqId, String prizeId, String date);

    /** 记录一次兑换流水 */
    void logRedeem(long qqId, String prizeId, int cost, String date);

    // ─── 黑名单 ────────────────────────────────────────

    /** 将指定 QQ 加入黑名单 */
    void addBlacklist(long qqId, long addedBy);

    /** 将指定 QQ 移出黑名单 */
    void removeBlacklist(long qqId);

    /** 查询指定 QQ 是否在黑名单中 */
    boolean isBlacklisted(long qqId);

    /** 获取全部黑名单 QQ 号列表 */
    List<Long> getBlacklist();

    // ─── 积分红包 ────────────────────────────────────────

    /** 创建红包，返回红包 ID */
    long createRedPacket(long senderQq, long groupId, int totalAmount, int count, long expireAt);

    /** 获取未过期且未领完的红包 */
    @Nullable
    RedPacket getActiveRedPacket(long groupId);

    /** 尝试领取红包，返回领取金额（-1=已领完，-2=已领取过，-3=红包不存在/过期） */
    int tryClaimRedPacket(long redPacketId, long claimerQq);

    /** 退还过期红包积分给发送者 */
    void refundExpiredRedPackets();

    // ─── 群活跃度 ────────────────────────────────────────

    /** 记录一次发言 */
    void recordActivity(long qqId, long groupId, String date);

    /** 获取本月/本周活跃度排行（按发言次数降序） */
    List<ActivityRecord> getActivityLeaderboard(String dateStart, String dateEnd, int limit);

    record QQBotBinding(
        long qqId,
        UUID playerUuid,
        String playerName,
        long boundAt
    ) {}

    record PointAccount(
        long qqId,
        int balance,
        int totalEarned,
        int totalSpent
    ) {}

    record SignInRecord(
        long qqId,
        String date,
        int streak,
        long signedAt
    ) {}

    record RedPacket(
        long id,
        long senderQq,
        long groupId,
        int totalAmount,
        int remainingAmount,
        int count,
        int claimedCount,
        long expireAt,
        long createdAt
    ) {}

    record ActivityRecord(
        long qqId,
        int messageCount
    ) {}
}
