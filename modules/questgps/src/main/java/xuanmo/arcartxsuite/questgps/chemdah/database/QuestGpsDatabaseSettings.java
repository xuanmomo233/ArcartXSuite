package xuanmo.arcartxsuite.questgps.chemdah.database;

/**
 * QuestGPS 侧 Chemdah 数据库接管配置。
 */
public record QuestGpsDatabaseSettings(
    boolean enabled,
    boolean loadInJoinEvent,
    boolean releaseInQuitEvent,
    boolean disableAutoSave,
    boolean disableAutoCreateTable
) {
    public static QuestGpsDatabaseSettings defaults() {
        return new QuestGpsDatabaseSettings(false, true, true, false, false);
    }
}
