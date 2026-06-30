package xuanmo.arcartxsuite.menu.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.service.MenuService;

public final class MenuAdminCommand {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "list", "open");

    private final MenuService service;
    private final MessageProvider messages;

    public MenuAdminCommand(MenuService service, MessageProvider messages) {
        this.service = service;
        this.messages = messages;
    }

    public List<String> actions() {
        return ACTIONS;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(sender, label, args[0]);
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
            default -> {
                sender.sendMessage(msg("admin.usage"));
                sendHelp(sender, label, args[0]);
            }
        }
        return true;
    }

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

    private void sendHelp(CommandSender sender, String label, String alias) {
        sender.sendMessage(msg("admin.usage"));
        sender.sendMessage(msg("admin.help-header"));
        sender.sendMessage(msg("admin.help-status"));
        sender.sendMessage(msg("admin.help-reload"));
        sender.sendMessage(msg("admin.help-list"));
        sender.sendMessage(msg("admin.help-open"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("admin.status-title"));
        sender.sendMessage(msg("admin.status-menus", service.menus().size()));
        sender.sendMessage(msg("admin.status-bindings",
            service.bindingRegistry().commandBindingCount(),
            service.bindingRegistry().itemBindings().size()
        ));
    }

    private void sendList(Player player) {
        for (MenuDefinition definition : service.menus()) {
            player.sendMessage(messages.get("player.list-entry",
                definition.id(),
                definition.layout().configKey(),
                definition.title()
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
