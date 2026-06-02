package xuanmo.arcartxsuite.questgps;

import java.util.Locale;

public enum QuestGpsPage {
    AVAILABLE("available", "可接取"),
    ACTIVE("active", "进行中"),
    COMPLETED("completed", "已完成");

    private final String id;
    private final String displayName;

    QuestGpsPage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static QuestGpsPage parse(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (QuestGpsPage page : values()) {
            if (page.id.equals(normalized)) {
                return page;
            }
        }
        return null;
    }
}
