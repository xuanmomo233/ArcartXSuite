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

public final class MenuBindingRegistry {

    private final List<MenuCommandBinding> commandBindings = new ArrayList<>();
    private final List<MenuItemBinding> itemBindings = new ArrayList<>();

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

    public List<MenuItemBinding> itemBindings() {
        return List.copyOf(itemBindings);
    }

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
