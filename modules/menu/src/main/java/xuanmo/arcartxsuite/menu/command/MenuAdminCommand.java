package xuanmo.arcartxsuite.menu.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.service.MenuService;

/**
 * Menu 模块的管理命令处理器，提供 help、status、reload、list、open 等子命令。
 * <p>
 * 由 {@link xuanmo.arcartxsuite.menu.MenuModule} 通过 {@link xuanmo.arcartxsuite.api.ModuleCommandHandler}
 * 接口委托调用，面向管理员进行菜单状态查看、配置重载和强制打开菜单等操作。
 */
public final class MenuAdminCommand {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "list", "open");

    private final MenuService service;
    private final MessageProvider messages;

    public MenuAdminCommand(MenuService service, MessageProvider messages) {
        this.service = service;
        this.messages = messages;
    }

    /**
     * 返回该命令支持的所有子命令动作列表。
     *
     * @return 不可变的子命令名称列表
     */
    public List<String> actions() {
        return ACTIONS;
    }

    /**
     * 处理管理命令，根据子命令分发到对应的帮助、状态、重载、列表或打开逻辑。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数数组
     * @return {@code true} 表示命令已处理
     */
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> showHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> {
                if (!sender.hasPermission("axs.menu.reload")) {
                    sender.sendMessage(msg("common.no-permission"));
                    return true;
                }
                sender.sendMessage(msg("admin.reload-success"));
            }
            case "list" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg("common.player-only"));
                    return true;
                }
                sendList(player);
            }
            case "open" -> handleOpen(sender, args);
            default -> showHelp(sender, label);
        }
        return true;
    }

    /**
     * 提供命令 Tab 补全，根据参数位置返回子命令名称、菜单 ID 或在线玩家名。
     *
     * @param sender 命令发送者
     * @param args   当前已输入的参数数组
     * @return 补全候选列表，可能为空
     */
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        if (args.length == 3 && "open".equalsIgnoreCase(args[1])) {
            List<String> ids = new ArrayList<>();
            for (MenuDefinition definition : service.menus()) {
                ids.add(definition.id());
            }
            return filter(ids, args[2]);
        }
        if (args.length == 4 && "open".equalsIgnoreCase(args[1])) {
            return filterOnline(args[3]);
        }
        return List.of();
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("axs.menu.open.other")) {
            sender.sendMessage(msg("common.no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("admin.usage"));
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(msg("common.player-only"));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(msg("common.player-only"));
            return;
        }
        service.openMenu(target, args[2]);
        if (!target.equals(sender)) {
            sender.sendMessage(msg("admin.open-other", target.getName(), args[2]));
        }
    }

    private void showHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " menu";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.reload", cmd));
        sender.sendMessage(msg("help.list", cmd));
        sender.sendMessage(msg("help.open", cmd));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("admin.status-title"));
        sender.sendMessage(msg("admin.status-menus", service.menus().size()));
        sender.sendMessage(msg("admin.status-panel-ui", service.configuration().client().panelUiId()));
        sender.sendMessage(msg("admin.status-esc-ui", service.configuration().client().escUiId()));
        sender.sendMessage(msg("admin.status-bindings",
            service.bindingRegistry().commandBindingCount(),
            service.bindingRegistry().itemBindings().size()
        ));
    }

    private void sendList(Player player) {
        List<MenuDefinition> menus = new ArrayList<>(service.menus());
        if (menus.isEmpty()) {
            player.sendMessage(msg("player.list-empty"));
            return;
        }
        player.sendMessage(msg("player.list-header", menus.size()));
        for (MenuDefinition definition : menus) {
            player.sendMessage(messages.get("admin.list-entry",
                definition.id(),
                definition.layout().configKey(),
                ChatColor.translateAlternateColorCodes('&', definition.title())
            ));
        }
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    private static List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static List<String> filterOnline(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(names, prefix);
    }
}
