package xuanmo.arcartxsuite.questgps.config;

/**
 * 任务分类全局策略：只使用一种数据源，见 {@link CategorySource}。
 */
public record CategoryDefaults(CategorySource source, FallbackCategory fallback) {

    public record FallbackCategory(boolean enabled, String id, String displayName, int sortOrder) {

        public static FallbackCategory disabled() {
            return new FallbackCategory(false, "uncategorized", "未分类", 1000);
        }
    }

    public static CategoryDefaults defaults() {
        return new CategoryDefaults(CategorySource.CHEMDAH, FallbackCategory.disabled());
    }
}
