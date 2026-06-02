package xuanmo.arcartxsuite.title.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.title.config.TitleModuleConfiguration;
import xuanmo.arcartxsuite.title.service.TitleService;

public final class TitlePlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "equip", "unequip", "hide", "unhide");

    private final Supplier<TitleService> serviceProvider;
    private final Supplier<TitleModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    public TitlePlayerCommand(Supplier<TitleService> serviceProvider, Supplier<TitleModuleConfiguration> configurationProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("player.only-player"));
            return true;
        }
        if (!sender.hasPermission("arcartxsuite.title.use")) {
            sender.sendMessage(msg("player.no-permission"));
            return true;
        }
        TitleService titleSvc = serviceProvider.get();
        if (titleSvc == null) {
            sender.sendMessage(msg("player.module-disabled"));
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            titleSvc.openMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "equip" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.equip-usage", label));
                    return true;
                }
                titleSvc.equipTitle(player, args[1]);
                return true;
            }
            case "unequip" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.unequip-usage", label));
                    return true;
                }
                if ("all".equalsIgnoreCase(args[1])) {
                    titleSvc.unequipAll(player);
                } else {
                    titleSvc.unequipGroup(player, args[1]);
                }
                return true;
            }
            case "hide" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.hide-usage", label));
                    return true;
                }
                titleSvc.hideTitle(player, args[1], true);
                return true;
            }
            case "unhide" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("player.unhide-usage", label));
                    return true;
                }
                titleSvc.unhideTitle(player, args[1], true);
                return true;
            }
            default -> {
                sender.sendMessage(msg("player.usage", label));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filter(ROOT_ACTIONS, args[0]);
        }
        TitleModuleConfiguration cfg = configurationProvider.get();
        if (args.length == 2 && cfg != null) {
            String action = args[0].toLowerCase();
            if ("equip".equals(action) || "hide".equals(action) || "unhide".equals(action)) {
                return filter(new ArrayList<>(cfg.titles().keySet()), args[1]);
            }
            if ("unequip".equals(action)) {
                List<String> groups = new ArrayList<>(cfg.groups().keySet());
                groups.add("all");
                return filter(groups, args[1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> candidates, String input) {
        String normalized = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
