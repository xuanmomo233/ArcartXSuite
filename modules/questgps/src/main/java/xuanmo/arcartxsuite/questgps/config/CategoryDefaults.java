package xuanmo.arcartxsuite.questgps.config;

public record CategoryDefaults(FallbackCategory fallback) {
    public record FallbackCategory(boolean enabled, String id, String displayName, int sortOrder) {
        public static FallbackCategory disabled() {
            return new FallbackCategory(false, "uncategorized", "未分类", 1000);
        }
    }

    public static CategoryDefaults defaults() {
        return new CategoryDefaults(new FallbackCategory(true, "uncategorized", "未分类", 1000));
    }
}
