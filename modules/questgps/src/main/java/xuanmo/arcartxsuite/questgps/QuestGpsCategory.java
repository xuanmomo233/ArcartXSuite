package xuanmo.arcartxsuite.questgps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 任务分类 — 支持内置分类和用户自定义分类。
 * <p>
 * 内置分类: {@link #MAINLINE}、{@link #SIDE}、{@link #ENCOUNTER}。
 * 用户可在配置的 {@code categories} 段中定义更多分类，使用任意 ID 和显示名。
 * 同一分类的任务可以分布在不同的 yml 文件中。
 */
public final class QuestGpsCategory {

    public static final QuestGpsCategory MAINLINE = new QuestGpsCategory("mainline", "主线", 0);
    public static final QuestGpsCategory SIDE = new QuestGpsCategory("side", "支线", 100);
    public static final QuestGpsCategory ENCOUNTER = new QuestGpsCategory("encounter", "奇遇", 200);

    private static final Map<String, QuestGpsCategory> BUILTINS;
    static {
        Map<String, QuestGpsCategory> map = new LinkedHashMap<>();
        map.put(MAINLINE.id, MAINLINE);
        map.put(SIDE.id, SIDE);
        map.put(ENCOUNTER.id, ENCOUNTER);
        BUILTINS = Collections.unmodifiableMap(map);
    }

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
     * 从注册表中按 ID 查找分类；如果注册表中不存在，回退到内置分类。
     * 未找到返回 null。
     */
    public static QuestGpsCategory parse(String rawValue, Map<String, QuestGpsCategory> registry) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (registry != null) {
            QuestGpsCategory fromRegistry = registry.get(normalized);
            if (fromRegistry != null) {
                return fromRegistry;
            }
        }
        return BUILTINS.get(normalized);
    }

    /**
     * 仅从内置分类中查找（向后兼容）。
     */
    public static QuestGpsCategory parse(String rawValue) {
        return parse(rawValue, null);
    }

    /**
     * 返回内置分类列表。
     */
    public static List<QuestGpsCategory> builtins() {
        return List.copyOf(BUILTINS.values());
    }

    /**
     * 构建包含内置分类 + 自定义分类的完整注册表。
     * 自定义分类的 ID 如果与内置 ID 冲突，自定义定义会覆盖内置定义（允许用户重命名内置分类的显示名）。
     */
    public static Map<String, QuestGpsCategory> buildRegistry(List<QuestGpsCategory> customCategories) {
        Map<String, QuestGpsCategory> registry = new LinkedHashMap<>(BUILTINS);
        if (customCategories != null) {
            for (QuestGpsCategory custom : customCategories) {
                registry.put(custom.id(), custom);
            }
        }
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 返回注册表中所有分类，按 sortOrder 升序排列。
     */
    public static List<QuestGpsCategory> sorted(Map<String, QuestGpsCategory> registry) {
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
