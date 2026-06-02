package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 黑名单配置。
 * <p>
 * 列入黑名单的 QQ 号将被完全禁止使用机器人的所有功能：
 * 指令处理、消息同步、入群欢迎、签到积分、自动回复等。
 */
public record QQBotBlacklistConfig(
    boolean enabled,
    List<Long> qqList,
    String addPrefix,
    String removePrefix,
    String listPrefix
) {

    /** 检查配置静态黑名单 */
    public boolean isConfigBlacklisted(long qqId) {
        return enabled && qqList.contains(qqId);
    }
}
