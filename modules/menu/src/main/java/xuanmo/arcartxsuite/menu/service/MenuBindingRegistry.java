package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import xuanmo.arcartxsuite.menu.config.MenuCommandBinding;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuItemBinding;

/**
 * 菜单绑定注册表，维护命令绑定和物品绑定，支持按命令行匹配对应菜单。
 * <p>
 * 在菜单重载时由 {@link MenuService} 调用 {@link #rebuild} 重建全部绑定关系。
 */
public final class MenuBindingRegistry {

    private final List<MenuCommandBinding> commandBindings = new ArrayList<>();
    private final List<MenuItemBinding> itemBindings = new ArrayList<>();

    /**
     * 从菜单定义集合和全局物品绑定重建全部绑定关系。
     *
     * @param menus               菜单定义集合
     * @param globalItemBindings  全局物品绑定列表
     * @param logger              日志记录器
     */
    public void rebuild(Iterable<MenuDefinition> menus, List<MenuItemBinding> globalItemBindings, Logger logger) {
        commandBindings.clear();
        itemBindings.clear();
        if (globalItemBindings != null) {
            itemBindings.addAll(globalItemBindings);
        }
        for (MenuDefinition definition : menus) {
            for (String command : definition.commands()) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                commandBindings.add(new MenuCommandBinding(
                    definition.id(),
                    command.trim(),
                    false,
                    null,
                    definition.permission()
                ));
            }
            for (String regex : definition.commandRegex()) {
                if (regex == null || regex.isBlank()) {
                    continue;
                }
                Pattern compiled = compilePattern(regex, definition.id(), logger);
                if (compiled != null) {
                    commandBindings.add(new MenuCommandBinding(
                        definition.id(),
                        regex.trim(),
                        true,
                        compiled,
                        definition.permission()
                    ));
                }
            }
            itemBindings.addAll(definition.itemBinds());
        }
    }

    /**
     * 匹配命令行对应的命令绑定。
     *
     * @param commandLine 命令行文本（可含前导 {@code /}）
     * @return 匹配的命令绑定，无匹配返回 {@code null}
     */
    public MenuCommandBinding matchCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return null;
        }
        String normalized = commandLine.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        for (MenuCommandBinding binding : commandBindings) {
            if (binding.matches(normalized)) {
                return binding;
            }
        }
        return null;
    }

    /**
     * 返回所有物品绑定的不可变副本。
     *
     * @return 物品绑定列表
     */
    public List<MenuItemBinding> itemBindings() {
        return List.copyOf(itemBindings);
    }

    /**
     * 返回当前命令绑定的数量。
     *
     * @return 命令绑定数量
     */
    public int commandBindingCount() {
        return commandBindings.size();
    }

    private static Pattern compilePattern(String regex, String menuId, Logger logger) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException exception) {
            if (logger != null) {
                logger.warning("Menu 命令正则无效: menu=" + menuId + " pattern=" + regex
                    + " -> " + exception.getMessage());
            }
            return null;
        }
    }
}
