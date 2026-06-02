package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * QQ 群签到打卡配置。
 */
public record QQBotSignInConfig(
    boolean enabled,
    int basePoints,
    int streakBonus,
    int maxStreakBonus,
    String signPrefix,
    List<String> aliases,
    String shopPrefix,
    String redeemPrefix,
    String pointsQueryPrefix,
    String transferPrefix,
    String redPacketPrefix,
    String grabRedPacketPrefix,
    String activityPrefix
) {
    public boolean matchesSign(String body) {
        if (body.equals(stripPrefix(signPrefix))) return true;
        for (String alias : aliases) {
            if (body.equals(stripPrefix(alias))) return true;
        }
        return false;
    }

    private static String stripPrefix(String s) {
        return s == null ? "" : s.trim();
    }
}
