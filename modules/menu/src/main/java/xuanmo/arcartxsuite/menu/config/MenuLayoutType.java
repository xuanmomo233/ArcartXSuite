package xuanmo.arcartxsuite.menu.config;

public enum MenuLayoutType {
    PANEL,
    ESC;

    public static MenuLayoutType parse(String raw, MenuLayoutType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "esc", "pause", "pause-menu" -> ESC;
            default -> PANEL;
        };
    }

    public String configKey() {
        return name().toLowerCase();
    }
}
