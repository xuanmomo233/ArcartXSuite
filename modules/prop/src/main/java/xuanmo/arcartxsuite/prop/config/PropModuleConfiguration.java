package xuanmo.arcartxsuite.prop.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PropModuleConfiguration(
    boolean debug,
    PropMythicLibConfiguration mythicLib
) {

    public static PropModuleConfiguration load(FileConfiguration configuration) {
        return new PropModuleConfiguration(
            configuration.getBoolean("settings.debug", false),
            new PropMythicLibConfiguration(
                configuration.getBoolean("mythiclib.enabled", false),
                configuration.getString("mythiclib.source-prefix", "AXS_PROP")
            )
        );
    }
}
