package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Menu 模块客户端配置，仅保留与 UI 无关的客户端字段。
 * <p>
 * 由主配置的 {@code client} 节解析。UI 相关字段（packet-id、panel-ui-id、esc-ui-id、
 * register-ui-on-enable、overwrite-ui-files）已抽离到 {@link MenuUiConfiguration} 的 {@code ui} 节。
 *
 * @param escMenuId 默认 ESC 菜单的菜单 ID（菜单定义 ID，非 UI ID）
 */
public record MenuClientConfiguration(
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
            return new MenuClientConfiguration("esc_main");
        }
        return new MenuClientConfiguration(
            section.getString("esc-menu-id", "esc_main")
        );
    }
}
