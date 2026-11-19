package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 跟踪模块的命令标签及其被覆盖的绑定，供模块重载或卸载时恢复。
 */
final class ModuleCommandBindings {

    private record Binding(PluginCommand command, Command previous) {}

    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    boolean register(CommandMap commandMap, Map<String, Command> known, PluginCommand command,
                     boolean protectExisting, Logger logger) {
        String namespace = command.getPlugin().getName().toLowerCase(Locale.ROOT);
        String name = command.getName();
        List<String> labels = new ArrayList<>();
        labels.add(name);
        labels.addAll(command.getAliases());

        Map<String, Command> previous = new LinkedHashMap<>();
        for (String label : labels) {
            String qualified = namespace + ":" + label;
            Command existing = known.get(qualified);
            if (existing != null) {
                logger.warning("命令 /" + qualified + " 已由" + describeSource(existing) + "注册，跳过 /" + name);
                return false;
            }
            previous.put(label, known.get(label));
            previous.put(qualified, null);
        }

        try {
            // 让 Bukkit 初始化命令的注册状态，再统一处理各服务端对同名主命令和别名的不同规则。
            commandMap.register(namespace, command);
            List<String> activeAliases = new ArrayList<>();
            for (String label : labels) {
                Command existing = previous.get(label);
                boolean keepExisting = existing != null && (protectExisting
                    || (existing instanceof PluginIdentifiableCommand owned && owned.getPlugin() == command.getPlugin()));
                if (keepExisting) {
                    if (known.get(label) == command) {
                        known.put(label, existing);
                    }
                } else {
                    if (known.get(label) != command) {
                        known.put(label, command);
                    }
                    if (!name.equals(label)) {
                        activeAliases.add(label);
                    }
                }
            }

            String activeLabel = known.get(name) == command ? name : namespace + ":" + name;
            if (!activeLabel.equals(command.getLabel()) || !activeAliases.equals(command.getAliases())) {
                command.unregister(commandMap);
                command.setLabel(activeLabel);
                command.setAliases(activeAliases);
                command.register(commandMap);
            }
        } catch (RuntimeException e) {
            previous.forEach((label, original) -> restore(known, label, command, original));
            command.unregister(commandMap);
            throw e;
        }

        previous.forEach((label, original) -> {
            if (known.get(label) == command) {
                bindings.put(label, new Binding(command, original));
            }
        });
        for (String label : labels) {
            Command existing = previous.get(label);
            if (existing != null) {
                String resolution = known.get(label) == command
                    ? "，当前无前缀命令由本模块提供"
                    : "，本模块使用 /" + namespace + ":" + label;
                logger.info("命令 /" + label + " 与" + describeSource(existing) + "的命令冲突" + resolution);
            }
        }
        return true;
    }

    boolean isEmpty() {
        return bindings.isEmpty();
    }

    boolean owns(String label, Command command) {
        Binding binding = bindings.get(label);
        return binding != null && binding.command() == command;
    }

    Command previous(String label, Command current) {
        Binding binding = bindings.get(label);
        return binding != null && binding.command() == current ? binding.previous() : null;
    }

    void unregister(Map<String, Command> known) {
        bindings.forEach((label, binding) -> restore(known, label, binding.command(), binding.previous()));
        bindings.clear();
    }

    private static void restore(Map<String, Command> known, String label, Command command, Command previous) {
        if (known.get(label) == command) {
            if (previous == null) {
                known.remove(label);
            } else {
                known.put(label, previous);
            }
        }
    }

    static String describeSource(Command command) {
        if (command instanceof PluginIdentifiableCommand owned) {
            Plugin owner = owned.getPlugin();
            if (owner != null) {
                return "插件 " + owner.getName();
            }
        }
        if (command instanceof FormattedCommandAlias) {
            return "服务器配置（commands.yml）";
        }
        String type = command.getClass().getName();
        if (type.startsWith("org.bukkit.craftbukkit.") && type.endsWith(".VanillaCommandWrapper")) {
            return "服务器内置（Minecraft 原版）";
        }
        if (type.startsWith("org.bukkit.command.defaults.") || type.startsWith("org.bukkit.craftbukkit.")
            || type.startsWith("org.spigotmc.") || type.startsWith("io.papermc.paper.")
            || type.startsWith("com.destroystokyo.paper.")) {
            return "服务器内置（" + command.getClass().getSimpleName() + "）";
        }
        try {
            return "插件 " + JavaPlugin.getProvidingPlugin(command.getClass()).getName() + " 提供实现";
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // 部分第三方命令没有暴露注册方，其实现也不由 Bukkit 插件类加载器加载。
        }
        return "来源未知（" + type + "）";
    }
}
