package xuanmo.arcartxsuite.menu.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record MenuSettingsConfiguration(
    String menusDirectory,
    MenuLayoutType defaultLayout,
    int columns,
    int buttonsPerPage,
    long clickCooldownMs,
    boolean closeOnAction,
    boolean notifyOpenFailed,
    java.util.List<MenuItemBinding> globalItemBinds
) {

    public static MenuSettingsConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return new MenuSettingsConfiguration("menus", MenuLayoutType.PANEL, 2, 12, 300L, true, true, List.of());
        }
        java.util.List<MenuItemBinding> globalItemBinds = MenuItemBinding.loadList(section.getMapList("item-binds"), "");
        return new MenuSettingsConfiguration(
            section.getString("menus-directory", "menus"),
            MenuLayoutType.parse(section.getString("default-layout"), MenuLayoutType.PANEL),
            Math.max(1, section.getInt("columns", 2)),
            Math.max(1, section.getInt("buttons-per-page", 12)),
            Math.max(0L, section.getLong("click-cooldown-ms", 300L)),
            section.getBoolean("close-on-action", true),
            section.getBoolean("notify-open-failed", true),
            globalItemBinds
        );
    }
}
