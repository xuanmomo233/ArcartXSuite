package xuanmo.arcartxsuite.qqbot.config;

/**
 * 群管理员公告广播配置。
 * 管理员在群内发送公告，同步到游戏内（聊天栏 + 可选标题）。
 */
public record QQBotAnnounceConfig(
    boolean enabled,
    String prefix,
    String gameFormat,
    String qqReceipt,
    boolean titleEnabled,
    String titleText,
    String subtitleFormat
) {}
