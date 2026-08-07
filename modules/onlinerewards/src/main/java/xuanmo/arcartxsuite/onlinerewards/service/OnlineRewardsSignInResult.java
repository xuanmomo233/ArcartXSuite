package xuanmo.arcartxsuite.onlinerewards.service;

/**
 * 签到操作结果。
 * <p>
 * 属于 {@code onlinerewards} 模块，记录一次签到的成败、是否重复签到、
 * 连续与累计天数、签到日期与当月日期序号。
 *
 * @param success    签到是否成功
 * @param repeated   是否为重复签到
 * @param streak     连续签到天数
 * @param total      累计签到天数
 * @param signInDate 签到日期键
 * @param dayOfMonth 当月日期序号
 */
public record OnlineRewardsSignInResult(
    boolean success,
    boolean repeated,
    int streak,
    int total,
    String signInDate,
    int dayOfMonth
) {
}
