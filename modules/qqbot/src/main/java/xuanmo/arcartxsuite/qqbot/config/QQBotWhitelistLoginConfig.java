package xuanmo.arcartxsuite.qqbot.config;

/**
 * 白名单登录门控配置。
 * 结合 authlib-injector (LittleSkin) 和 QQ 绑定实现分级准入。
 */
public record QQBotWhitelistLoginConfig(
    boolean enabled,
    boolean microsoftPass,
    boolean littleskinRequireBind,
    boolean denyOffline,
    String kickNotBound,
    String kickOffline,
    String kickDenied
) {}
