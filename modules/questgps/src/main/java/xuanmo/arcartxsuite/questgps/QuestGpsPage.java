package xuanmo.arcartxsuite.questgps;

import java.util.Locale;

/**
 * QuestGPS 菜单分页枚举。
 * <p>
 * 属于 {@code questgps} 模块，定义菜单中任务的三种状态页：
 * 可接取（AVAILABLE）、进行中（ACTIVE）、已完成（COMPLETED）。
 */
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

    /** @return 分页标识符 */
    public String id() {
        return id;
    }

    /** @return 分页显示名称 */
    public String displayName() {
        return displayName;
    }

    /**
     * 根据原始字符串解析分页枚举，忽略大小写与首尾空白。
     *
     * @param rawValue 原始字符串
     * @return 匹配的分页枚举，无匹配时返回 {@code null}
     */
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
