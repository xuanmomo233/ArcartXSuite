package xuanmo.arcartxsuite.onlinerewards.model;

import java.util.Locale;

public enum OnlineRewardsLeaderboardScope {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    TOTAL("total");

    private final String key;

    OnlineRewardsLeaderboardScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

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
