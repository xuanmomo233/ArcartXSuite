package xuanmo.arcartxsuite.tab.config;

import java.util.Locale;

public enum TabSortMode {
    NAME("name"),
    PREM("prem"),
    PAPI("papi");

    private final String configValue;

    TabSortMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static TabSortMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NAME;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (TabSortMode value : values()) {
            if (value.configValue.equals(normalized)) {
                return value;
            }
        }
        return NAME;
    }
}
