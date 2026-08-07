package xuanmo.arcartxsuite.menu.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.service.MenuService;

/**
 * Menu 模块的玩家命令处理器，实现 {@link org.bukkit.command.TabExecutor}。
 * <p>
 * 支持 {@code open <菜单ID>} 打开菜单和 {@code list} 列出可用菜单，
 * 同时支持通过配置绑定的命令别名直接打开对应菜单。
 * 通过 {@link java.util.function.Supplier} 延迟获取 {@link MenuService}，
 * 以兼容模块热重载期间服务实例可能为 null 的情况。
 */
public final class MenuPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "list");

    private final Supplier<MenuService> serviceSupplier;
    private final MessageProvider messages;

    public MenuPlayerCommand(Supplier<MenuService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    /**
     * 处理玩家命令，支持通过绑定的命令别名直接打开菜单，或使用 open/list 子命令。
     *
     * @param sender  命令发送者，必须是玩家
     * @param command 命令对象
     * @param label   命令标签
     * @param args    命令参数数组
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("common.player-only"));
            return true;
        }
        if (!player.hasPermission("arcartxsuite.menu.use")) {
            player.sendMessage(msg("common.no-permission"));
            return true;
        }
        MenuService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(msg("common.module-disabled"));
            return true;
        }

        MenuDefinition boundMenu = service.resolveCommandMenu(label);
        if (boundMenu != null && (args.length == 0 || "open".equalsIgnoreCase(args[0]))) {
            service.openMenu(player, boundMenu.id());
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(msg("player.usage", label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                if (args.length < 2) {
                    player.sendMessage(msg("player.usage", label));
                    return true;
                }
                service.openMenu(player, args[1]);
            }
            case "list" -> sendList(player);
            default -> player.sendMessage(msg("player.usage", label));
        }
        return true;
    }

    /**
     * 提供命令 Tab 补全，根据参数位置返回子命令名称或可用菜单 ID。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param label   命令标签
     * @param args    当前已输入的参数数组
     * @return 补全候选列表，可能为空
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MenuService service = serviceSupplier.get();
        if (args.length == 1) {
            return filter(ROOT_ACTIONS, args[0]);
        }
        if (args.length == 2 && "open".equalsIgnoreCase(args[0]) && service != null) {
            List<String> ids = new ArrayList<>();
            for (MenuDefinition definition : service.menus()) {
                ids.add(definition.id());
            }
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private void sendList(Player player) {
        MenuService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(msg("common.module-disabled"));
            return;
        }
        List<MenuDefinition> menus = new ArrayList<>(service.menus());
        if (menus.isEmpty()) {
            player.sendMessage(msg("player.list-empty"));
            return;
        }
        player.sendMessage(msg("player.list-header", menus.size()));
        for (MenuDefinition definition : menus) {
            player.sendMessage(messages.get("player.list-entry",
                definition.id(),
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
}
