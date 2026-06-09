package xuanmo.arcartxsuite.afkreward.command;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;

public final class AfkRewardPlayerCommand implements TabExecutor {

    private static final String PERMISSION = "arcartxsuite.afkreward.use";
    private static final List<String> ACTIONS = List.of("toggle", "status", "start", "end", "list", "top");

    private final Supplier<AfkRewardService> serviceSupplier;
    private final MessageProvider messages;

    public AfkRewardPlayerCommand(Supplier<AfkRewardService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("common.player-only"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(msg("common.no-permission"));
            return true;
        }

        AfkRewardService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(msg("common.service-down"));
            return true;
        }

        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "toggle" -> {
                boolean enabled = service.toggleHud(player.getUniqueId());
                player.sendMessage(enabled ? msg("player.toggle.on") : msg("player.toggle.off"));
            }
            case "status" -> sendStatus(player, service);
            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage(msg("player.manual.usage-start", label));
                    return true;
                }
                service.startManualAfk(player, args[1]);
            }
            case "end" -> service.endManualAfk(player, false);
            case "list" -> sendAfkList(player, service);
            case "top" -> sendLeaderboard(player, service);
            default -> player.sendMessage(msg("player.usage", label));
        }
        return true;
    }

    private void sendStatus(Player player, AfkRewardService service) {
        player.sendMessage(msg("player.status.title"));
        AfkRewardService.PlayerAfkState state = service.getState(player.getUniqueId());
        PlayerStats stats = service.getStatsSnapshot(player.getUniqueId());
        if (state == null || state.areaName == null) {
            player.sendMessage(msg("player.status.none"));
            return;
        }
        String modeLabel = state.mode == AfkRewardService.AfkMode.MANUAL ? "原地挂机" : "区域挂机";
        player.sendMessage(msg("player.status.mode", modeLabel));
        player.sendMessage(msg("player.status.area", state.areaName));
        player.sendMessage(msg("player.status.time", AfkRewardService.formatTime(state.seconds)));
        player.sendMessage(msg("player.status.total", AfkRewardService.formatTime(stats != null ? stats.totalSeconds() : 0)));
        player.sendMessage(msg("player.status.today", stats != null ? stats.todayCount() : 0));
        player.sendMessage(msg("player.status.total-rewards", stats != null ? stats.totalCount() : 0));
        int roundSec = service.getRewardRoundMinutes() * 60;
        int remain = Math.max(0, roundSec - (state.seconds - state.lastRewardSeconds));
        player.sendMessage(msg("player.status.next", remain + "秒"));
        player.sendMessage(msg("player.status.players", service.getPlayersInArea(state.areaName)));
    }

    private void sendAfkList(Player player, AfkRewardService service) {
        player.sendMessage(msg("player.manual.list-title"));
        var list = service.getOnlineAfkPlayers();
        if (list.isEmpty()) {
            player.sendMessage(msg("player.manual.list-empty"));
            return;
        }
        for (var entry : list) {
            player.sendMessage(msg("player.manual.list-body",
                entry.playerName(), entry.areaName(), entry.mode(), AfkRewardService.formatTime(entry.seconds())));
        }
    }

    private void sendLeaderboard(Player player, AfkRewardService service) {
        player.sendMessage(msg("player.manual.top-title"));
        var board = service.getLeaderboard();
        if (board.isEmpty()) {
            player.sendMessage(msg("player.manual.top-empty"));
            return;
        }
        int i = 1;
        for (var entry : board) {
            player.sendMessage(msg("player.manual.top-body",
                String.valueOf(i++), entry.playerName(),
                AfkRewardService.formatTime(entry.totalSeconds()), String.valueOf(entry.totalCount())));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        AfkRewardService service = serviceSupplier.get();
        if (args.length == 1) {
            return ACTIONS.stream()
                .filter(a -> a.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0]) && service != null) {
            return service.areas().values().stream()
                .filter(a -> a.enabled() && a.hasTeleport() && a.manualEnabled())
                .map(AfkArea::name)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return List.of();
    }
}
