package xuanmo.arcartxsuite.menu.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Menu 模块设置配置，定义菜单目录、布局默认值、分页参数、点击冷却和全局物品绑定。
 * <p>
 * 由主配置的 {@code settings} 节解析。
 *
 * @param menusDirectory    菜单定义文件的目录名
 * @param defaultLayout     默认布局类型
 * @param columns           默认列数
 * @param buttonsPerPage    每页按钮数上限
 * @param clickCooldownMs   点击冷却时间（毫秒）
 * @param closeOnAction     执行动作后是否关闭菜单
 * @param notifyOpenFailed  菜单打开失败时是否通知玩家
 * @param globalItemBinds   全局物品绑定列表
 */
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

    /**
     * 从配置节加载设置配置，为 null 时返回默认值。
     *
     * @param section 设置配置节
     * @return 解析后的设置配置
     */
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
