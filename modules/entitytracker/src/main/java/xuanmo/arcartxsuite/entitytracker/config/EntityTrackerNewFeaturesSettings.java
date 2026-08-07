package xuanmo.arcartxsuite.entitytracker.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * EntityTracker 新功能总设置，属于 entitytracker 模块的配置子系统。
 * <p>
 * 聚合掉落记录、掉落分配和跨服排名三个新功能子设置，
 * 并提供是否需要数据库的判断。
 *
 * @param dropRecording     掉落记录设置
 * @param dropAllocation    掉落分配设置
 * @param crossServerRanking 跨服排名设置
 */
public record EntityTrackerNewFeaturesSettings(
    DropRecordingSettings dropRecording,
    DropAllocationSettings dropAllocation,
    CrossServerRankingSettings crossServerRanking
) {

    /**
     * 从配置节加载新功能设置。
     *
     * @param section 配置节，为 {@code null} 时返回默认设置
     * @return 解析得到的设置
     */
    public static EntityTrackerNewFeaturesSettings load(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        return new EntityTrackerNewFeaturesSettings(
            DropRecordingSettings.load(section.getConfigurationSection("drop-recording")),
            DropAllocationSettings.load(section.getConfigurationSection("drop-allocation")),
            CrossServerRankingSettings.load(section.getConfigurationSection("cross-server-ranking"))
        );
    }

    /**
     * 返回默认的新功能设置（全部禁用）。
     *
     * @return 默认设置
     */
    public static EntityTrackerNewFeaturesSettings defaults() {
        return new EntityTrackerNewFeaturesSettings(
            DropRecordingSettings.disabled(),
            DropAllocationSettings.disabled(),
            CrossServerRankingSettings.disabled()
        );
    }

    /**
     * 判断是否有任一新功能启用，需要数据库支持。
     *
     * @return {@code true} 表示需要数据库
     */
    public boolean needsDatabase() {
        return dropRecording.enabled()
            || dropAllocation.enabled()
            || crossServerRanking.enabled();
    }
}
