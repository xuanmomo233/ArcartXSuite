package xuanmo.arcartxsuite.questgps.config;

/**
 * 任务菜单分类的唯一数据来源（全局二选一，不做多层回退）。
 */
public enum CategorySource {
    /** 仅读 Chemdah 任务模板 meta.type，映射到 ArcartXQuestGPS.yml categories */
    CHEMDAH,
    /** 仅读 overlay quests/*.yml 的 category 字段 */
    OVERLAY;

    public static CategorySource parse(String raw, CategorySource fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("overlay")) {
            return OVERLAY;
        }
        if (normalized.equals("chemdah") || normalized.equals("meta-type") || normalized.equals("meta_type")) {
            return CHEMDAH;
        }
        return fallback;
    }
}
