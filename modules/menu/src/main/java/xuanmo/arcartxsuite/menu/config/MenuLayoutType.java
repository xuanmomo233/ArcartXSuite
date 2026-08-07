package xuanmo.arcartxsuite.menu.config;

/**
 * 菜单布局类型枚举，区分面板菜单和 ESC 菜单两种展示形态。
 * <p>
 * 面板菜单通过 ArcartX 面板 UI 展示，ESC 菜单在玩家暂停界面展示。
 */
public enum MenuLayoutType {
    PANEL,
    ESC;

    /**
     * 将文本解析为布局类型，无法识别时返回回退值。
     *
     * @param raw      原始文本
     * @param fallback 回退布局类型
     * @return 解析后的布局类型
     */
    public static MenuLayoutType parse(String raw, MenuLayoutType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "esc", "pause", "pause-menu" -> ESC;
            default -> PANEL;
        };
    }

    /**
     * 返回该布局类型在配置中使用的小写关键字。
     *
     * @return 小写布局名称
     */
    public String configKey() {
        return name().toLowerCase();
    }
}
