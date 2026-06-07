package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;

public record MenuClientConfiguration(
    String packetId,
    String panelUiId,
    String escUiId,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles,
    String escMenuId
) {

    public static MenuClientConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return new MenuClientConfiguration("AXS_MENU", "AXS:menu_panel", "AXS:menu_esc", true, false, "esc_main");
        }
        return new MenuClientConfiguration(
            section.getString("packet-id", "AXS_MENU"),
            section.getString("panel-ui-id", "AXS:menu_panel"),
            section.getString("esc-ui-id", "AXS:menu_esc"),
            section.getBoolean("register-ui-on-enable", true),
            section.getBoolean("overwrite-ui-files", false),
            section.getString("esc-menu-id", "esc_main")
        );
    }
}
