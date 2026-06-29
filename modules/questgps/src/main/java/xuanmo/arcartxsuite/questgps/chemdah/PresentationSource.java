package xuanmo.arcartxsuite.questgps.chemdah;

/**
 * UI 展示字段数据来源：Chemdah 模板或 QuestGPS overlay（全局二选一）。
 */
public enum PresentationSource {
    CHEMDAH,
    OVERLAY;

    public static PresentationSource parseGlobal(String raw, PresentationSource fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("overlay")) {
            return OVERLAY;
        }
        if (normalized.equals("chemdah")) {
            return CHEMDAH;
        }
        return fallback;
    }
}
