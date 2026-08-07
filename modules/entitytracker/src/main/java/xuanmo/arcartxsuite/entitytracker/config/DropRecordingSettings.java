package xuanmo.arcartxsuite.entitytracker.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 掉落记录设置，属于 entitytracker 模块的新功能配置子系统。
 * <p>
 * 配置 Boss 掉落记录的开关、数据保留天数、统计启用及统计更新间隔。
 *
 * @param enabled                       是否启用掉落记录
 * @param retentionDays                 数据保留天数（最小 1）
 * @param enableStatistics              是否启用掉落统计
 * @param statisticsUpdateIntervalSeconds 统计更新间隔秒数（最小 30）
 */
public record DropRecordingSettings(
    boolean enabled,
    int retentionDays,
    boolean enableStatistics,
    int statisticsUpdateIntervalSeconds
) {

    /**
     * 返回未启用的默认设置。
     *
     * @return 禁用设置
     */
    public static DropRecordingSettings disabled() {
        return new DropRecordingSettings(false, 30, true, 300);
    }

    /**
     * 从配置节加载掉落记录设置。
     *
     * @param section 配置节，为 {@code null} 时返回禁用设置
     * @return 解析得到的设置
     */
    public static DropRecordingSettings load(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new DropRecordingSettings(
            section.getBoolean("enabled", false),
            Math.max(1, section.getInt("retention-days", 30)),
            section.getBoolean("enable-statistics", true),
            Math.max(30, section.getInt("statistics-update-interval", 300))
        );
    }
}
