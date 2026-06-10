package xuanmo.arcartxsuite.eventpacket.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.eventpacket.service.EntityCleanupService;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;

public final class EventPacketAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "fire", "clearlag");

    private final Supplier<EventPacketDispatchService> serviceProvider;
    private final Supplier<EntityCleanupService> cleanupProvider;
    private final MessageProvider messages;

    public EventPacketAdminCommand(Supplier<EventPacketDispatchService> serviceProvider,
                                   Supplier<EntityCleanupService> cleanupProvider,
                                   MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.cleanupProvider = cleanupProvider;
        this.messages = messages;
    }

    @Override public String commandId() { return "eventpacket"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sender.sendMessage(msg("common.running"));
            case "reload" -> sender.sendMessage(msg("common.reload-hint", label));
            case "fire" -> handleFire(sender, args);
            case "clearlag" -> handleClearlag(sender);
            default -> sender.sendMessage(msg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 4 && "fire".equalsIgnoreCase(args[1])) {
            return null; // player names
        }
        return List.of();
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " eventpacket";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.fire", cmd));
        sender.sendMessage(msg("help.clearlag", cmd));
    }

    private void handleFire(CommandSender sender, String[] args) {
        EventPacketDispatchService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 4) {
            sender.sendMessage(msg("fire.usage"));
            return;
        }
        String signal = args[2];
        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[3])); return; }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 4; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                variables.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            }
        }
        svc.dispatchSignal(signal, target, variables);
        sender.sendMessage(variables.isEmpty()
            ? msg("fire.success", target.getName(), signal)
            : msg("fire.success-vars", target.getName(), signal, variables.size()));
    }

    private void handleClearlag(CommandSender sender) {
        EntityCleanupService svc = cleanupProvider.get();
        if (svc == null) {
            sender.sendMessage(msg("clearlag.service-down"));
            return;
        }
        int count = svc.executeCleanup();
        sender.sendMessage(msg("clearlag.success", count));
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
