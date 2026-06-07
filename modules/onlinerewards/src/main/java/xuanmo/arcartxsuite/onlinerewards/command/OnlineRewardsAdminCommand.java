package xuanmo.arcartxsuite.onlinerewards.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsOperationResult;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;

public final class OnlineRewardsAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "add", "remove", "set", "card");

    private final Supplier<OnlineRewardsService> serviceProvider;
    private final MessageProvider messages;

    public OnlineRewardsAdminCommand(Supplier<OnlineRewardsService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "onlinerewards"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(msg("admin.reload-hint", label));
            case "add", "remove", "set" -> handleTimeAdjust(sender, action, args);
            case "card" -> handleCard(sender, args);
            default -> sender.sendMessage(msg("admin.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            String a = args[1].toLowerCase(Locale.ROOT);
            if (List.of("add", "remove", "set").contains(a)) return filter(List.of("30m", "1h", "2h", "12h", "1d"), args[2]);
            if ("card".equals(a)) return filter(List.of("add", "remove", "set"), args[2]);
        }
        if (args.length == 4) {
            String a = args[1].toLowerCase(Locale.ROOT);
            if (List.of("add", "remove", "set").contains(a)) return null; // player names
            if ("card".equals(a)) return filter(List.of("1", "5", "10"), args[3]);
        }
        if (args.length == 5 && "card".equalsIgnoreCase(args[1])) {
            return null; // player names
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " onlinerewards";
        sender.sendMessage(msg("admin.help.title"));
        sender.sendMessage(msg("admin.help.status", cmd));
        sender.sendMessage(msg("admin.help.time", cmd));
        sender.sendMessage(msg("admin.help.card", cmd));
    }

    private void sendStatus(CommandSender sender) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.cached", svc.cachedPlayerCount()));
        sender.sendMessage(msg("admin.status.cross-server",
            svc.crossServerActive() ? ChatColor.GREEN + "已启用" : ChatColor.YELLOW + "未启用"));
    }

    private void handleTimeAdjust(CommandSender sender, String operation, String[] args) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 4) {
            sender.sendMessage(msg("admin.time.usage", operation));
            return;
        }
        int minutes = parseDurationMinutes(args[2]);
        if (minutes <= 0 && !"set".equals(operation)) {
            sender.sendMessage(msg("admin.time.invalid", args[2]));
            return;
        }
        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[3])); return; }
        OnlineRewardsOperationResult result = svc.adjustOnlineTime(target, operation, minutes);
        sender.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleCard(CommandSender sender, String[] args) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 5) {
            sender.sendMessage(msg("admin.card.usage"));
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        int amount;
        try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.card.invalid-amount", args[3])); return;
        }
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[4])); return; }
        OnlineRewardsOperationResult result = svc.adjustMakeupCards(target, operation, amount);
        sender.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static int parseDurationMinutes(String input) {
        if (input == null || input.isBlank()) return 0;
        String lower = input.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("d")) return Integer.parseInt(lower.replace("d", "")) * 1440;
            if (lower.endsWith("h")) return Integer.parseInt(lower.replace("h", "")) * 60;
            if (lower.endsWith("m")) return Integer.parseInt(lower.replace("m", ""));
            return Integer.parseInt(lower);
        } catch (NumberFormatException e) { return 0; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
