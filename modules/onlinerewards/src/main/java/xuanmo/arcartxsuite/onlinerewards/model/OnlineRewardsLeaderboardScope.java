package xuanmo.arcartxsuite.onlinerewards.model;

import java.util.Locale;

/**
 * 排行榜统计周期范围。
 * <p>
 * 属于 {@code onlinerewards} 模块，定义日/周/月/总四种排行榜维度，
 * 通过 {@link #parse(String)} 从配置或命令参数解析。
 */
public enum OnlineRewardsLeaderboardScope {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    TOTAL("total");

    private final String key;

    OnlineRewardsLeaderboardScope(String key) {
        this.key = key;
    }

    /**
     * 返回该范围在配置/命令中使用的键名。
     *
     * @return 键名
     */
    public String key() {
        return key;
    }

    /**
     * 从字符串解析排行榜范围，无法识别时返回 {@code null}。
     *
     * @param rawValue 原始字符串
     * @return 解析得到的范围，无法识别时为 {@code null}
     */
    public static OnlineRewardsLeaderboardScope parse(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (OnlineRewardsLeaderboardScope value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
