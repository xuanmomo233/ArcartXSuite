package xuanmo.arcartxsuite.menu.config;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

public record MenuModuleConfiguration(
    boolean debug,
    MenuClientConfiguration client,
    MenuSettingsConfiguration settings,
    MenuMessagesConfiguration messages
) {

    public static MenuModuleConfiguration load(@NotNull YamlConfiguration yaml) {
        return new MenuModuleConfiguration(
            yaml.getBoolean("debug.enabled", false),
            MenuClientConfiguration.load(yaml.getConfigurationSection("client")),
            MenuSettingsConfiguration.load(yaml.getConfigurationSection("settings")),
            MenuMessagesConfiguration.load(yaml.getConfigurationSection("messages"))
        );
    }

    public @NotNull File menusDirectory(@NotNull File dataFolder) {
        return new File(dataFolder, settings.menusDirectory());
    }
}
