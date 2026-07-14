package xuanmo.arcartxsuite.fishing.command;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.fishing.model.FishingPlayerData;
import xuanmo.arcartxsuite.fishing.service.FishingService;

public final class FishingAdminCommand implements ModuleCommandHandler {

    private final java.util.function.Supplier<FishingService> serviceSupplier;
    private final java.util.function.BiFunction<String, Object[], String> msg;

    public FishingAdminCommand(@NotNull java.util.function.Supplier<FishingService> serviceSupplier,
                               @NotNull java.util.function.BiFunction<String, Object[], String> msg) {
        this.serviceSupplier = serviceSupplier;
        this.msg = msg;
    }

    @Override public String commandId() { return "fishing"; }

    @Override public List<String> actions() {
        return List.of("help", "stats", "givexp", "reset", "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        FishingService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg.apply("command_service_unavailable", new Object[]{}));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "help" -> showHelp(sender);
            case "stats" -> handleStats(sender, args, service);
            case "givexp" -> handleGiveXp(sender, args, service);
            case "reset" -> handleReset(sender, args, service);
            default -> showHelp(sender);
        }
        return true;
    }

    private void handleStats(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(msg.apply("command_player_required", new Object[]{""}));
            return;
        }

        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{""}));
            return;
        }

        FishingPlayerData data = service.getPlayerData(target.getUniqueId());
        int collectionCount = service.getCollectionCount(target.getUniqueId());
        int totalTypes = service.getTotalFishTypes();

        sender.sendMessage(msg.apply("command_stats_header", new Object[]{target.getName()}));
        sender.sendMessage(msg.apply("command_stats_level", new Object[]{String.valueOf(data.level())}));
        sender.sendMessage(msg.apply("command_stats_xp", new Object[]{String.valueOf(data.totalXp())}));
        sender.sendMessage(msg.apply("command_stats_total_caught", new Object[]{String.valueOf(data.totalCaught())}));
        sender.sendMessage(msg.apply("command_stats_perfect", new Object[]{String.valueOf(data.perfectCatches())}));
        sender.sendMessage(msg.apply("command_stats_collection", new Object[]{collectionCount + "/" + totalTypes}));
    }

    private void handleGiveXp(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        if (args.length < 3) {
            sender.sendMessage(msg.apply("command_usage_givexp", new Object[]{}));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{args[1]}));
            return;
        }
        try {
            int amount = Integer.parseInt(args[2]);
            service.giveXp(target.getUniqueId(), amount);
            sender.sendMessage(msg.apply("command_give_xp", new Object[]{target.getName(), String.valueOf(amount)}));
        } catch (NumberFormatException e) {
            sender.sendMessage(msg.apply("command_invalid_xp", new Object[]{}));
        }
    }

    private void handleReset(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(msg.apply("command_usage_reset", new Object[]{}));
            return;
        }

        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{""}));
            return;
        }

        service.resetPlayerData(target.getUniqueId());
        sender.sendMessage(msg.apply("command_reset", new Object[]{target.getName()}));
    }

    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(msg.apply("command_help_title", new Object[]{}));
        sender.sendMessage(msg.apply("command_help_stats", new Object[]{}));
        sender.sendMessage(msg.apply("command_help_givexp", new Object[]{}));
        sender.sendMessage(msg.apply("command_help_reset", new Object[]{}));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return actions().stream()
                .filter(a -> a.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("reset"))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .toList();
        }
        return List.of();
    }
}
