package xuanmo.arcartxsuite.entitytracker.boss.tracker;

/**
 * 奖励发放结果，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 记录单次奖励发放的成功状态和描述消息，供命令和日志反馈使用。
 *
 * @param success  是否发放成功
 * @param message  结果描述消息
 */
public record BossDamageRewardDispatchResult(
    boolean success,
    String message
) {

    /**
     * 构造成功结果。
     *
     * @param message 成功描述消息
     * @return 成功结果实例
     */
    public static BossDamageRewardDispatchResult success(String message) {
        return new BossDamageRewardDispatchResult(true, message);
    }

    /**
     * 构造失败结果。
     *
     * @param message 失败描述消息
     * @return 失败结果实例
     */
    public static BossDamageRewardDispatchResult failure(String message) {
        return new BossDamageRewardDispatchResult(false, message);
    }
}

