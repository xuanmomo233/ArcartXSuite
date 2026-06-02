package xuanmo.arcartxsuite.prop.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.prop.service.PropService;

public final class PropAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "set");

    private final Supplier<PropService> serviceProvider;
    private final MessageProvider messages;

    public PropAdminCommand(Supplier<PropService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "prop"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "set" -> handleSet(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        PropService svc = serviceProvider.get();
        if (args.length == 3 && "set".equalsIgnoreCase(args[1]) && svc != null) {
            return filter(svc.propIds(), args[2]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " prop";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.set", cmd));
    }

    private void sendStatus(CommandSender sender) {
        PropService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.props", svc.propCount()));
        sender.sendMessage(fullMsg("status.keys", svc.registeredKeyCount()));
        sender.sendMessage(fullMsg("status.ap", svc.attributePlusHooked()));
        sender.sendMessage(fullMsg("status.mythiclib", svc.mythicLibEnabled(), svc.mythicLibHooked()));
    }

    private void handleSet(CommandSender sender, String[] args) {
        PropService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("set.only-player"));
            return;
        }
        if (args.length < 3) { sender.sendMessage(fullMsg("set.usage")); return; }
        PropService.PropOperationResult result = svc.applyPropToMainHand(player, args[2]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
