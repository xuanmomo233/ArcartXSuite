package xuanmo.arcartxsuite.title.model;

import java.time.Instant;

/**
 * 玩家拥有的单个称号记录。
 * <p>
 * 记录称号的发放时间、激活时间、过期时间、隐藏状态与发放者，
 * 提供过期/激活/生效判断及不可变派生方法。
 *
 * @param titleId     称号 ID
 * @param hidden      是否隐藏（不展示但不影响收集属性）
 * @param grantedAt   发放时间
 * @param activatesAt 激活时间，{@code null} 表示立即激活
 * @param expiresAt   过期时间，{@code null} 表示永久
 * @param updatedAt   最后更新时间
 * @param grantedBy   发放者名称
 */
public record PlayerOwnedTitle(
    String titleId,
    boolean hidden,
    Instant grantedAt,
    Instant activatesAt,
    Instant expiresAt,
    Instant updatedAt,
    String grantedBy
) {
    /**
     * @param now 当前时间
     * @return 是否已过期
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * @param now 当前时间
     * @return 是否已激活（activatesAt 为空或已过激活时间）
     */
    public boolean isActive(Instant now) {
        return activatesAt == null || !activatesAt.isAfter(now);
    }

    /**
     * @param now 当前时间
     * @return 是否生效（已激活且未过期）
     */
    public boolean isEffective(Instant now) {
        return isActive(now) && !isExpired(now);
    }

    /**
     * @param now 当前时间
     * @return 剩余毫秒数；永久称号返回 {@code -1}
     */
    public long remainingMillis(Instant now) {
        if (expiresAt == null) {
            return -1L;
        }
        return Math.max(0L, expiresAt.toEpochMilli() - now.toEpochMilli());
    }

    /**
     * 生成仅修改隐藏状态的派生记录。
     *
     * @param hidden 新的隐藏状态
     * @param now    更新时间
     * @return 新的 {@link PlayerOwnedTitle} 实例
     */
    public PlayerOwnedTitle withHidden(boolean hidden, Instant now) {
        return new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy);
    }

    /**
     * 生成刷新发放信息的派生记录（保留原隐藏状态）。
     *
     * @param grantedAt   新发放时间
     * @param activatesAt 新激活时间
     * @param expiresAt   新过期时间
     * @param now         更新时间
     * @param grantedBy   新发放者
     * @return 新的 {@link PlayerOwnedTitle} 实例
     */
    public PlayerOwnedTitle refreshed(Instant grantedAt, Instant activatesAt, Instant expiresAt, Instant now, String grantedBy) {
        return new PlayerOwnedTitle(titleId, hidden, grantedAt, activatesAt, expiresAt, now, grantedBy);
    }
}
