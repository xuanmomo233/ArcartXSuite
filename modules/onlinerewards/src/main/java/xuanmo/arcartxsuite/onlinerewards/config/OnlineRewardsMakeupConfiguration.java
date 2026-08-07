package xuanmo.arcartxsuite.onlinerewards.config;

/**
 * 补签功能配置。
 * <p>
 * 属于 {@code onlinerewards} 模块签到子系统，控制补签卡机制：玩家消耗补签卡对历史未签到日期进行补签。
 *
 * @param enabled              是否启用补签功能
 * @param cardName             补签卡显示名称
 * @param successMessage       补签成功提示消息
 * @param noCardMessage        补签卡不足提示消息
 * @param invalidDateMessage   日期无效提示消息
 * @param alreadySignedMessage 该日期已签到提示消息
 */
public record OnlineRewardsMakeupConfiguration(
    boolean enabled,
    String cardName,
    String successMessage,
    String noCardMessage,
    String invalidDateMessage,
    String alreadySignedMessage
) {
}
