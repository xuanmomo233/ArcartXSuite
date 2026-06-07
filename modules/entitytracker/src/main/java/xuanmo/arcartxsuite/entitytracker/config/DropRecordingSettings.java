package xuanmo.arcartxsuite.entitytracker.config;

import org.bukkit.configuration.ConfigurationSection;

public record DropRecordingSettings(
    boolean enabled,
    int retentionDays,
    boolean enableStatistics,
    int statisticsUpdateIntervalSeconds
) {

    public static DropRecordingSettings disabled() {
        return new DropRecordingSettings(false, 30, true, 300);
    }

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
