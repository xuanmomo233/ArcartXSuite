package xuanmo.arcartxsuite.questgps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 任务分类 Tab。仅在 {@code ArcartXQuestGPS.yml} 的 {@code categories} 段注册后生效；
 * 无内置分类，删除该段即可关闭分类 Tab 功能。
 */
public final class QuestGpsCategory {

    private final String id;
    private final String displayName;
    private final int sortOrder;

    public QuestGpsCategory(String id, String displayName, int sortOrder) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.displayName = displayName == null ? this.id : displayName.trim();
        this.sortOrder = sortOrder;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int sortOrder() {
        return sortOrder;
    }

    /**
     * 从注册表按 ID 查找；未注册返回 null。
     */
    public static QuestGpsCategory parse(String rawValue, Map<String, QuestGpsCategory> registry) {
        if (rawValue == null || registry == null || registry.isEmpty()) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return registry.get(normalized);
    }

    /**
     * 由配置 {@code categories} 段构建注册表；空列表表示未启用分类 Tab。
     */
    public static Map<String, QuestGpsCategory> buildRegistry(List<QuestGpsCategory> configuredCategories) {
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return Map.of();
        }
        Map<String, QuestGpsCategory> registry = new LinkedHashMap<>();
        for (QuestGpsCategory category : configuredCategories) {
            registry.put(category.id(), category);
        }
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 注册表中第一个分类（按 sortOrder），无则 null。
     */
    public static QuestGpsCategory firstOrNull(Map<String, QuestGpsCategory> registry) {
        List<QuestGpsCategory> sorted = sorted(registry);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /**
     * 返回注册表中所有分类，按 sortOrder 升序排列。
     */
    public static List<QuestGpsCategory> sorted(Map<String, QuestGpsCategory> registry) {
        if (registry == null || registry.isEmpty()) {
            return List.of();
        }
        List<QuestGpsCategory> list = new ArrayList<>(registry.values());
        list.sort((a, b) -> {
            int cmp = Integer.compare(a.sortOrder, b.sortOrder);
            return cmp != 0 ? cmp : a.id.compareToIgnoreCase(b.id);
        });
        return Collections.unmodifiableList(list);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QuestGpsCategory that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QuestGpsCategory{id='" + id + "', displayName='" + displayName + "', sortOrder=" + sortOrder + "}";
    }
}
