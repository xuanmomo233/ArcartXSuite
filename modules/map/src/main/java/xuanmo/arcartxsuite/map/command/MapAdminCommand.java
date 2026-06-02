package xuanmo.arcartxsuite.map.command;

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
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.service.MapService;

public final class MapAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "open", "list", "anchors");

    private final Supplier<MapService> serviceProvider;
    private final MessageProvider messages;

    public MapAdminCommand(Supplier<MapService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "map"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "open" -> handleOpen(sender, args);
            case "list" -> handleList(sender);
            case "anchors" -> handleAnchors(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        MapService svc = serviceProvider.get();
        if (svc == null) return List.of();
        if (args.length == 3) {
            if ("open".equalsIgnoreCase(args[1])) return null; // player names
            if ("anchors".equalsIgnoreCase(args[1])) return filter(svc.configuredWorldIds(), args[2]);
        }
        if (args.length == 4 && "open".equalsIgnoreCase(args[1])) {
            return filter(svc.configuredWorldIds(), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " map";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.list", cmd));
        sender.sendMessage(fullMsg("help.anchors", cmd));
    }

    private void sendStatus(CommandSender sender) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.worlds", svc.configuredWorldCount()));
        sender.sendMessage(fullMsg("status.anchors", svc.configuredAnchorCount()));
        sender.sendMessage(fullMsg("status.tracking", svc.trackingPlayerCount()));
        sender.sendMessage(fullMsg("status.waypoint", svc.waypointRuntimeReady()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        String worldId = args.length >= 4 ? args[3] : "";
        MapOperationResult result = svc.openMenuFor(target, worldId);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleList(CommandSender sender) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        List<String> worlds = svc.configuredWorldIds();
        if (worlds.isEmpty()) { sender.sendMessage(fullMsg("list.empty")); return; }
        sender.sendMessage(fullMsg("list.title", worlds.size()));
        for (String w : worlds) {
            sender.sendMessage(fullMsg("list.item", w));
        }
    }

    private void handleAnchors(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        List<String> anchors = svc.configuredAnchorIds();
        if (anchors.isEmpty()) { sender.sendMessage(fullMsg("anchors.empty")); return; }
        String worldFilter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        sender.sendMessage(fullMsg("anchors.title", worldFilter != null ? " (世界: " + worldFilter + ")" : ""));
        for (String a : anchors) {
            if (worldFilter == null || a.toLowerCase(Locale.ROOT).contains(worldFilter)) {
                sender.sendMessage(fullMsg("anchors.item", a));
            }
        }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
