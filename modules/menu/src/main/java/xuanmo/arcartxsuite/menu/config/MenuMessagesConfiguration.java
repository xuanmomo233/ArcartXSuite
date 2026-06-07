package xuanmo.arcartxsuite.menu.config;

import org.bukkit.configuration.ConfigurationSection;

public record MenuMessagesConfiguration(
    String prefix,
    String noPermission,
    String playerOnly,
    String menuNotFound,
    String menuOpenFailed,
    String menuOpenSuccess,
    String buttonUnavailable,
    String pageEmpty,
    String reloadSuccess,
    String reloadFailed
) {

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
            "&7当前页没有可用按钮。",
            "&aMenu 模块配置已重载。",
            "&cMenu 模块重载失败: &7{error}"
        );
    }

    public String format(String template, String key, String value) {
        return colorize(template.replace("{" + key + "}", value == null ? "" : value));
    }

    public String colorize(String input) {
        if (input == null) {
            return "";
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }
}
