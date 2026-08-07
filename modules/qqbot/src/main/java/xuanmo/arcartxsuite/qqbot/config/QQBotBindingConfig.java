package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的「QQ-游戏账号绑定」配置 record。
 * <p>
 * 关联模块：{@code qqbot}。定义绑定功能开关、验证方式、验证码有效期、
 * 单 QQ 最大绑定数，以及群内绑定 / 解绑 / 查绑的指令前缀。
 */
public record QQBotBindingConfig(
    /** 绑定功能是否启用 */
    boolean enabled,
    /** 绑定方式，如 {@code code}（验证码） */
    String method,
    /** 验证码有效期（秒） */
    int codeExpireSeconds,
    /** 单个 QQ 允许绑定的最大玩家数 */
    int maxBindingsPerQq,
    /** 群内发起绑定的指令前缀 */
    String bindPrefix,
    /** 群内发起解绑的指令前缀 */
    String unbindPrefix,
    /** 群内查询绑定关系的指令前缀 */
    String queryPrefix
) {}
