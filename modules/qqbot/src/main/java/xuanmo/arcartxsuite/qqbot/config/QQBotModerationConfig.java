package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 群管理 moderation 指令配置（踢人 / 封禁）以及 QQ 禁言同步。
 */
public record QQBotModerationConfig(
    boolean enabled,
    String kickPrefix,
    String banPrefix,
    String kickCommand,
    String banCommand,
    boolean syncBanEnabled,
    String syncBanCommand,
    boolean syncBanUseDuration,
    String syncBanReason,
    AutoModeration autoModeration
) {
    public record AutoModeration(
        boolean enabled,
        List<String> keywords,
        int banDurationSeconds,
        int cooldownSeconds
    ) {}
}
