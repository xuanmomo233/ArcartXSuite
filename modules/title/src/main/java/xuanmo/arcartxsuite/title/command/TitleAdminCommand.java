package xuanmo.arcartxsuite.title.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.title.TitleDurationParser;
import xuanmo.arcartxsuite.title.TitleDurationParser.TitleDurationSpec;
import xuanmo.arcartxsuite.title.service.TitleOperationResult;
import xuanmo.arcartxsuite.title.service.TitleService;

public final class TitleAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "give", "revoke", "open");

    private final Supplier<TitleService> serviceProvider;
    private final MessageProvider messages;

    public TitleAdminCommand(Supplier<TitleService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    private String prefix() {
        return messages.get("prefix");
    }

    @Override public String commandId() { return "title"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(msg("common.reload-hint", label));
            case "give" -> handleGive(sender, args);
            case "revoke" -> handleRevoke(sender, args);
            case "open" -> handleOpen(sender, args);
            default -> sender.sendMessage(msg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3 && List.of("give", "revoke", "open").contains(args[1].toLowerCase(Locale.ROOT))) {
            return null; // player names
        }
        if (args.length == 4 && List.of("give", "revoke").contains(args[1].toLowerCase(Locale.ROOT))) {
            TitleService svc = serviceProvider.get();
            if (svc == null) return List.of();
            return filter(new ArrayList<>(svc.titleIds()), args[3]);
        }
        if (args.length == 5 && "give".equalsIgnoreCase(args[1])) {
            return filter(List.of("permanent", "7d", "30d", "12h"), args[4]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " title";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.give", cmd));
        sender.sendMessage(msg("help.revoke", cmd));
        sender.sendMessage(msg("help.open", cmd));
    }

    private void sendStatus(CommandSender sender) {
        TitleService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        sender.sendMessage(msg("status.title"));
        sender.sendMessage(msg("status.cached", svc.cachedPlayerCount()));
        sender.sendMessage(msg("status.ap", svc.attributePlusHooked()));
        sender.sendMessage(msg("status.mythiclib", svc.mythicLibHooked()));
        sender.sendMessage(msg("status.crane", svc.craneAttributeHooked()));
        sender.sendMessage(msg("status.symphony", svc.symphonyHooked()));
    }

    private void handleGive(CommandSender sender, String[] args) {
        TitleService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 5) {
            sender.sendMessage(msg("give.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        UUID uuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        Optional<TitleDurationSpec> spec = TitleDurationParser.parse(args[4]);
        if (spec.isEmpty()) {
            sender.sendMessage(msg("give.invalid-duration", args[4]));
            return;
        }
        TitleOperationResult result = svc.giveTitle(uuid, args[3], spec.get(), sender.getName());
        sender.sendMessage(prefix() + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleRevoke(CommandSender sender, String[] args) {
        TitleService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 4) {
            sender.sendMessage(msg("revoke.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        UUID uuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        TitleOperationResult result = svc.revokeTitle(uuid, args[3]);
        sender.sendMessage(prefix() + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleOpen(CommandSender sender, String[] args) {
        TitleService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 3) {
            sender.sendMessage(msg("open.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[2])); return; }
        svc.openMenu(target);
        sender.sendMessage(msg("open.success", target.getName()));
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
