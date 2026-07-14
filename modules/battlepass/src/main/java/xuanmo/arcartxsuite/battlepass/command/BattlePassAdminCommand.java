package xuanmo.arcartxsuite.battlepass.command;

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
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

public final class BattlePassAdminCommand implements ModuleCommandHandler {

    private final Supplier<BattlePassService> serviceProvider;
    private final java.util.function.BiFunction<String, Object[], String> messageResolver;

    public BattlePassAdminCommand(Supplier<BattlePassService> serviceProvider, java.util.function.BiFunction<String, Object[], String> messageResolver) {
        this.serviceProvider = serviceProvider;
        this.messageResolver = messageResolver;
    }

    @Override
    public String commandId() {
        return "battlepass";
    }

    @Override
    public List<String> actions() {
        return List.of("help", "status", "reload", "reset", "season", "unlock");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        String cmd = "/" + label + " battlepass";
        switch (action) {
            case "help" -> sendHelp(sender, cmd);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(msg("common.reload-hint", label));
            case "reset" -> handleReset(sender, args);
            case "unlock" -> handleUnlock(sender, args);
            case "season" -> sender.sendMessage(msg("admin.season-unimplemented"));
            default -> sender.sendMessage(msg("common.unknown", cmd));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(actions(), args[1]);
        }
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("reset".equals(action) || "unlock".equals(action)) {
                return null; // Bukkit 补全在线玩家名
            }
        }
        if (args.length == 4 && "unlock".equals(args[1].toLowerCase(Locale.ROOT))) {
            return filter(List.of("premium", "deluxe"), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String cmd) {
        sender.sendMessage(msg("admin.help.title", "1.0.0"));
        sender.sendMessage(msg("admin.help.status", cmd));
        sender.sendMessage(msg("admin.help.reload", cmd));
        sender.sendMessage(msg("admin.help.season", cmd));
        sender.sendMessage(msg("admin.help.reset", cmd));
        sender.sendMessage(msg("admin.help.unlock", cmd));
    }

    private void sendStatus(CommandSender sender) {
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("admin.service-unavailable-status"));
            return;
        }
        var config = service.configuration();
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.season", config.season().seasonId()));
        try {
            int count = service.repository().countActivePlayers(config.season().seasonId());
            sender.sendMessage(msg("admin.status.players", String.valueOf(count)));
        } catch (Exception e) {
            sender.sendMessage(msg("admin.status.players", "N/A"));
        }
        sender.sendMessage(msg("admin.status.cross-server", config.crossServer().enabled() ? msg("common.enabled") : msg("common.disabled")));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("admin.reset-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        service.resetPlayerProgress(target.getUniqueId());
        sender.sendMessage(msg("admin.reset-success", target.getName()));
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.unlock-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        BattlePassPlayerProgress.PassTier tier;
        try {
            tier = BattlePassPlayerProgress.PassTier.valueOf(args[3].toUpperCase(Locale.ROOT));
            if (tier == BattlePassPlayerProgress.PassTier.FREE) {
                sender.sendMessage(msg("admin.unlock.invalid-tier"));
                return;
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(msg("admin.unlock.invalid-tier"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        if (service.unlockTier(target, tier)) {
            sender.sendMessage(msg("admin.unlock-success", target.getName(), ChatColor.translateAlternateColorCodes('&', tier.name())));
        } else {
            sender.sendMessage(msg("admin.unlock-already", target.getName(), ChatColor.translateAlternateColorCodes('&', tier.name())));
        }
    }

    private String msg(String key, Object... args) {
        return messageResolver.apply(key, args);
    }

    private static List<String> filter(List<String> candidates, String input) {
        if (input == null || input.isEmpty()) return candidates;
        String normalized = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
            .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }
}
