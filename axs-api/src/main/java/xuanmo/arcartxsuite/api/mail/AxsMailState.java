package xuanmo.arcartxsuite.api.mail;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 邮件状态枚举，对外公开的邮件生命周期阶段。
 * <p>
 * 与内部 {@code MailStatus} 对应，但作为公开 API 独立定义，
 * 避免外部插件依赖模块内部 model 类。
 *
 * @since 1.5.0
 */
@ApiStability.Stable
public enum AxsMailState {
    /** 未读 */
    UNREAD,
    /** 已读 */
    READ,
    /** 部分附件已发放，可从已保存的进度继续领取 */
    CLAIM_PENDING,
    /** 发放结果待核对，不会自动重发 */
    CLAIM_REVIEW,
    /** 已领取 */
    CLAIMED,
    /** 已删除 */
    DELETED,
    /** 已过期 */
    EXPIRED
}
