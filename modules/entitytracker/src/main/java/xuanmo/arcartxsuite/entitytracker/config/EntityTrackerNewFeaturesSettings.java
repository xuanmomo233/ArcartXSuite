package xuanmo.arcartxsuite.entitytracker.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record EntityTrackerNewFeaturesSettings(
    DropRecordingSettings dropRecording,
    DropAllocationSettings dropAllocation,
    CrossServerRankingSettings crossServerRanking
) {

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

    public static EntityTrackerNewFeaturesSettings defaults() {
        return new EntityTrackerNewFeaturesSettings(
            DropRecordingSettings.disabled(),
            DropAllocationSettings.disabled(),
            CrossServerRankingSettings.disabled()
        );
    }

    public boolean needsDatabase() {
        return dropRecording.enabled()
            || dropAllocation.enabled()
            || crossServerRanking.enabled();
    }
}
