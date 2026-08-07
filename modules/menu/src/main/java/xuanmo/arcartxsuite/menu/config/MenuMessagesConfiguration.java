package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Menu 模块消息配置，定义各类提示消息的文本模板。
 * <p>
 * 由主配置的 {@code messages} 节或外部消息文件解析，支持颜色代码和占位符替换。
 *
 * @param prefix                 消息前缀
 * @param noPermission           无权限提示
 * @param playerOnly             仅玩家可执行提示
 * @param menuNotFound           菜单未找到提示
 * @param menuOpenFailed         菜单打开失败提示
 * @param menuOpenSuccess        菜单打开成功提示
 * @param buttonUnavailable      按钮不可用提示
 * @param buttonConditionDenied  按钮条件不满足提示
 * @param pageEmpty              当前页无按钮提示
 * @param reloadSuccess          重载成功提示
 * @param reloadFailed           重载失败提示
 */
public record MenuMessagesConfiguration(
    String prefix,
    String noPermission,
    String playerOnly,
    String menuNotFound,
    String menuOpenFailed,
    String menuOpenSuccess,
    String buttonUnavailable,
    String buttonConditionDenied,
    String pageEmpty,
    String reloadSuccess,
    String reloadFailed
) {

    /**
     * 从配置节加载消息配置，缺失的字段使用默认值。
     *
     * @param section 消息配置节
     * @return 解析后的消息配置
     */
    public static MenuMessagesConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        MenuMessagesConfiguration defaults = defaults();
        return new MenuMessagesConfiguration(
            section.getString("prefix", defaults.prefix()),
            section.getString("no-permission", defaults.noPermission()),
            section.getString("player-only", defaults.playerOnly()),
            section.getString("menu-not-found", defaults.menuNotFound()),
            section.getString("menu-open-failed", defaults.menuOpenFailed()),
            section.getString("menu-open-success", defaults.menuOpenSuccess()),
            section.getString("button-unavailable", defaults.buttonUnavailable()),
            section.getString("button-condition-denied", defaults.buttonConditionDenied()),
            section.getString("page-empty", defaults.pageEmpty()),
            section.getString("reload-success", defaults.reloadSuccess()),
            section.getString("reload-failed", defaults.reloadFailed())
        );
    }

    private static MenuMessagesConfiguration defaults() {
        return new MenuMessagesConfiguration(
            "&3◆ &6ArcartXSuite &7| &r",
            "&c你没有权限执行此操作。",
            "&c该命令只能由玩家执行。",
            "&c未找到菜单: &f{menu}",
            "&c无法打开菜单 &f{menu}&c: &7{reason}",
            "&a已打开菜单 &f{menu}",
            "&c该按钮当前不可用。",
            "&c条件未满足，无法使用该按钮。",
            "&7当前页没有可用按钮。",
            "&aMenu 模块配置已重载。",
            "&cMenu 模块重载失败: &7{error}"
        );
    }

    /**
     * 将模板中的指定占位符替换为给定值并应用颜色代码。
     *
     * @param template 消息模板
     * @param key      占位符名称（不含花括号）
     * @param value    替换值
     * @return 替换并着色后的消息文本
     */
    public String format(String template, String key, String value) {
        return colorize(template.replace("{" + key + "}", value == null ? "" : value));
    }

    /**
     * 将 {@code &} 颜色代码转换为 Bukkit ChatColor 格式。
     *
     * @param input 原始文本
     * @return 着色后的文本，null 返回空字符串
     */
    public String colorize(String input) {
        if (input == null) {
            return "";
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }
}
