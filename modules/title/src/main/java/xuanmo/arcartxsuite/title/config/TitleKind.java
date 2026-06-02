package xuanmo.arcartxsuite.title.config;

import java.util.Locale;

public enum TitleKind {
    TEXT("text"),
    ICON("icon");

    private final String configKey;

    TitleKind(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static TitleKind parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return TEXT;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (TitleKind value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return TEXT;
    }
}
