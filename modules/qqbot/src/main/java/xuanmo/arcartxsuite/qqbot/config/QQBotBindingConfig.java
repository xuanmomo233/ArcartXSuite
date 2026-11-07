package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的「QQ-游戏账号绑定」配置 record。
 * <p>
 * 只负责入口开关和群指令前缀；验证码、绑定事实及策略由宿主认证管理。
 */
public record QQBotBindingConfig(
    /** 绑定功能是否启用 */
    boolean enabled,
    /** 群内发起绑定的指令前缀 */
    String bindPrefix,
    /** 群内发起解绑的指令前缀 */
    String unbindPrefix,
    /** 群内查询绑定关系的指令前缀 */
    String queryPrefix
) {
    public QQBotBindingConfig {
        if (bindPrefix == null || bindPrefix.isBlank() || unbindPrefix == null || unbindPrefix.isBlank()
                || queryPrefix == null || queryPrefix.isBlank()) throw new IllegalArgumentException("绑定指令前缀不能为空");
    }
}
