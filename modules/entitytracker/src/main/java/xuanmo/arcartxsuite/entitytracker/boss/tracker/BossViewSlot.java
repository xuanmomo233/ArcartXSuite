package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.Locale;

public record BossViewSlot(String title, String subtitle, BossPlaceholderContext context) {

    private static final BossViewSlot EMPTY = new BossViewSlot("", "", BossPlaceholderContext.empty());

    public static BossViewSlot empty() {
        return EMPTY;
    }

    public String resolve(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "title" -> title;
            case "subtitle" -> subtitle;
            default -> context.resolve(normalized);
        };
    }
}

