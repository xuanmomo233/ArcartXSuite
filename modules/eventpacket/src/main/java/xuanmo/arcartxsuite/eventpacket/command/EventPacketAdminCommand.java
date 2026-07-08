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
import xuanmo.arcartxsuite.eventpacket.config.EventPacketRule;
import xuanmo.arcartxsuite.eventpacket.config.PluginConfiguration;
import xuanmo.arcartxsuite.eventpacket.service.EntityCleanupService;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;

public final class EventPacketAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "fire", "clearlag", "list", "run");

    private final Supplier<EventPacketDispatchService> serviceProvider;
    private final Supplier<EntityCleanupService> cleanupProvider;
    private final Supplier<PluginConfiguration> configurationProvider;
    private final MessageProvider messages;

    public EventPacketAdminCommand(Supplier<EventPacketDispatchService> serviceProvider,
                                   Supplier<EntityCleanupService> cleanupProvider,
                                   Supplier<PluginConfiguration> configurationProvider,
                                   MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.cleanupProvider = cleanupProvider;
        this.configurationProvider = configurationProvider;
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
            case "list" -> handleList(sender);
            case "run" -> handleRun(sender, args);
            default -> sender.sendMessage(msg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3 && "run".equalsIgnoreCase(args[1])) {
            return null; // player names
        }
        if (args.length == 4 && "fire".equalsIgnoreCase(args[1])) {
            return null; // player names
        }
        if (args.length == 4 && "run".equalsIgnoreCase(args[1])) {
            PluginConfiguration configuration = configurationProvider.get();
            if (configuration != null) {
                return filter(
                    configuration.clientPacketRules().stream().map(EventPacketRule::signal).toList(),
                    args[3]
                );
            }
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
        sender.sendMessage(msg("help.list", cmd));
        sender.sendMessage(msg("help.run", cmd));
    }

    private void handleList(CommandSender sender) {
        PluginConfiguration configuration = configurationProvider.get();
        if (configuration == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        List<EventPacketRule> rules = configuration.clientPacketRules();
        if (rules.isEmpty()) {
            sender.sendMessage(msg("list.empty"));
            return;
        }
        sender.sendMessage(msg("list.title", rules.size()));
        for (EventPacketRule rule : rules) {
            String cooldown = rule.cooldownMillis() > 0L ? rule.cooldownMillis() + "ms" : "none";
            String permission = rule.hasPermissionFilter() ? rule.permission() : "none";
            String args = rule.allowArgs() ? "yes" : "no";
            sender.sendMessage(msg("list.entry", rule.signal(), rule.actions().size(), cooldown, permission, args));
        }
    }

    private void handleRun(CommandSender sender, String[] args) {
        EventPacketDispatchService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(msg("run.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg("common.player-offline", args[2]));
            return;
        }
        PluginConfiguration configuration = configurationProvider.get();
        if (configuration == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        EventPacketRule rule = configuration.findClientPacketRule(configuration.clientPacketId(), args[3]);
        if (rule == null) {
            sender.sendMessage(msg("run.not-found", args[3]));
            return;
        }
        List<String> extraArgs = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            extraArgs.add(args[i]);
        }
        svc.dispatchClientPacket(configuration.clientPacketId(), rule.signal(), target, extraArgs, true);
        sender.sendMessage(msg("run.success", target.getName(), rule.signal()));
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
