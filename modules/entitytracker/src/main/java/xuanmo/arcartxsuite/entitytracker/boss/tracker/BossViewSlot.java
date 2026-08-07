package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.Locale;

/**
 * Boss 视图槽位，属于 entitytracker 模块的 boss 追踪子系统。
 * <p>
 * 表示 HUD 中单个 Boss 条目的渲染数据，包含标题、副标题和占位符上下文。
 *
 * @param title    标题文本
 * @param subtitle 副标题文本
 * @param context  占位符上下文
 */
public record BossViewSlot(String title, String subtitle, BossPlaceholderContext context) {

    private static final BossViewSlot EMPTY = new BossViewSlot("", "", BossPlaceholderContext.empty());

    /**
     * 返回空的视图槽位。
     *
     * @return 空槽位
     */
    public static BossViewSlot empty() {
        return EMPTY;
    }

    /**
     * 解析占位符字段名对应的值。
     *
     * @param fieldName 字段名
     * @return 解析结果，无法识别时委托给上下文解析
     */
    public String resolve(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "title" -> title;
            case "subtitle" -> subtitle;
            default -> context.resolve(normalized);
        };
    }
}

