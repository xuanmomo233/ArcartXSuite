package xuanmo.arcartxsuite.tab.config;

import java.util.Locale;

/**
 * Tab 列表排序模式枚举。
 * <p>
 * 定义 Tab 模块中玩家条目的排序策略，关联模块：{@code tab}。
 * 通过配置字符串解析为对应的排序模式，无法识别时默认按名称排序。
 */
public enum TabSortMode {
    NAME("name"),
    PREM("prem"),
    PAPI("papi");

    private final String configValue;

    TabSortMode(String configValue) {
        this.configValue = configValue;
    }

    /**
     * @return 该排序模式对应的配置字符串标识
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 从配置字符串解析排序模式。
     * <p>
     * 输入为 {@code null} 或空白时返回 {@link #NAME}；
     * 无法识别的字符串同样返回 {@link #NAME}。
     *
     * @param raw 原始配置字符串
     * @return 解析得到的排序模式，永不为 {@code null}
     */
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
