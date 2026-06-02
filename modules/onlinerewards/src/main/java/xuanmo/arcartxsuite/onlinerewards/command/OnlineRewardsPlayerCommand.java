package xuanmo.arcartxsuite.onlinerewards.command;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsPlayerSnapshot;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsProgressSnapshot;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsTextFormats;

public final class OnlineRewardsPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final int PAGE_SIZE = 10;
    private static final List<String> ROOT_ACTIONS = List.of("open", "status", "signin", "top");

    private final Supplier<OnlineRewardsService> serviceProvider;
    private final Supplier<OnlineRewardsModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    public OnlineRewardsPlayerCommand(
        Supplier<OnlineRewardsService> serviceProvider,
        Supplier<OnlineRewardsModuleConfiguration> configurationProvider,
        MessageProvider messages
    ) {
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
            sender.sendMessage(msg("common.player-only"));
            return true;
        }
        if (!player.hasPermission("arcartxsuite.onlinerewards.use")) {
            player.sendMessage(msg("common.no-permission"));
            return true;
        }

        OnlineRewardsService service = serviceProvider.get();
        if (service == null) {
            player.sendMessage(msg("common.module-disabled"));
            return true;
        }

        if (args.length == 0) {
            if ("signin".equalsIgnoreCase(label)) {
                sendOperationResult(player, service.signIn(player));
            } else {
                sendOperationResult(player, service.openMenu(player));
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open" -> sendOperationResult(player, service.openMenu(player));
            case "status" -> sendStatus(player, service);
            case "signin" -> sendOperationResult(player, service.signIn(player));
            case "top" -> sendLeaderboard(player, service, label, args);
            default -> player.sendMessage(msg("player.usage", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return ROOT_ACTIONS.stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            return List.of("daily", "weekly", "monthly", "total").stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .toList();
        }
        if (args.length == 3 && "top".equalsIgnoreCase(args[0])) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }

    private void sendStatus(Player player, OnlineRewardsService service) {
        OnlineRewardsPlayerSnapshot snapshot = service.loadSnapshot(player.getUniqueId(), player.getName());
        OnlineRewardsProgressSnapshot progress = snapshot.progress();
        int progressPercent = Math.round(progress.progress() * 100.0F);

        player.sendMessage(msg("player.status.online",
            OnlineRewardsTextFormats.formatMinutes(snapshot.state().onlineMinutes()),
            OnlineRewardsTextFormats.formatMinutes(snapshot.state().weekMinutes())));
        player.sendMessage(msg("player.status.monthly",
            OnlineRewardsTextFormats.formatMinutes(snapshot.state().monthMinutes()),
            OnlineRewardsTextFormats.formatMinutes(snapshot.state().totalMinutes())));
        player.sendMessage(msg("player.status.stage",
            progress.completed() ? configurationProvider.get().doneMessage() : progress.title() + " (" + progressPercent + "%)"));
        player.sendMessage(msg("player.status.signin",
            snapshot.signedToday() ? messages.get("player.status.signed-yes") : messages.get("player.status.signed-no"),
            snapshot.state().signInStreak(),
            snapshot.state().signInTotal()));
    }

    private void sendLeaderboard(Player player, OnlineRewardsService service, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg("player.top.usage", label));
            return;
        }
        OnlineRewardsLeaderboardScope scope = OnlineRewardsLeaderboardScope.parse(args[1]);
        if (scope == null) {
            player.sendMessage(msg("player.top.invalid-scope", args[1]));
            return;
        }
        int page = args.length >= 3 ? parseInt(args[2], 1) : 1;
        List<OnlineRewardsLeaderboardEntry> entries = service.loadLeaderboard(scope, PAGE_SIZE, page);
        player.sendMessage(msg("player.top.header", scope.key(), Math.max(1, page)));
        if (entries.isEmpty()) {
            player.sendMessage(msg("player.top.empty"));
            return;
        }
        int startRank = (Math.max(1, page) - 1) * PAGE_SIZE + 1;
        for (int index = 0; index < entries.size(); index++) {
            OnlineRewardsLeaderboardEntry entry = entries.get(index);
            player.sendMessage(msg("player.top.entry",
                startRank + index,
                entry.playerName(),
                OnlineRewardsTextFormats.formatMinutes(entry.minutes()),
                entry.minutes()));
        }
    }

    private void sendOperationResult(Player player, xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsOperationResult result) {
        player.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
