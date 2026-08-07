package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Menu 模块客户端配置，定义 Packet ID、面板 UI 和 ESC UI 的标识及注册策略。
 * <p>
 * 由主配置的 {@code client} 节解析，控制与 ArcartX 客户端 UI 的对接参数。
 *
 * @param packetId          客户端数据包标识，用于 UI 与服务端通信
 * @param panelUiId         面板菜单 UI 的注册 ID
 * @param escUiId           ESC 菜单 UI 的注册 ID
 * @param registerUiOnEnable 启用时是否自动注册 UI
 * @param overwriteUiFiles  是否覆盖用户已修改的 UI 文件
 * @param escMenuId         默认 ESC 菜单的菜单 ID
 */
public record MenuClientConfiguration(
    String packetId,
    String panelUiId,
    String escUiId,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles,
    String escMenuId
) {

    /**
     * 从配置节加载客户端配置，为 null 时返回默认值。
     *
     * @param section 客户端配置节
     * @return 解析后的客户端配置
     */
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
