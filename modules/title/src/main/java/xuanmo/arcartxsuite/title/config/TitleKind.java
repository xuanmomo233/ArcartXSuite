package xuanmo.arcartxsuite.title.config;

import java.util.Locale;

/**
 * 称号类型枚举。
 * <p>
 * 区分文本型称号与图标型称号，用于 UI 渲染与属性处理。
 */
public enum TitleKind {
    TEXT("text"),
    ICON("icon");

    private final String configKey;

    TitleKind(String configKey) {
        this.configKey = configKey;
    }

    /**
     * @return 该类型在配置中使用的键名
     */
    public String configKey() {
        return configKey;
    }

    /**
     * 将配置文本解析为称号类型。
     *
     * @param rawValue 原始配置文本
     * @return 解析结果，无法识别时返回 {@link #TEXT}
     */
    public static TitleKind parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return TEXT;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (TitleKind value : values()) {
            if (value.configKey.equals(normalized)) {
                return value;
            }
        }
        return TEXT;
    }
}
