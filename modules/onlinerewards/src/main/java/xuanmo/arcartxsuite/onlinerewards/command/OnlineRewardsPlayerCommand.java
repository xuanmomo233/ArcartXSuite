package xuanmo.arcartxsuite.onlinerewards.command;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsPlayerSnapshot;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsProgressSnapshot;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsTextFormats;

/**
 * 在线奖励玩家命令处理器。
 * <p>
 * 属于 {@code onlinerewards} 模块，提供 {@code /onlinerewards|/signin <action>} 玩家子命令，
 * 包括打开菜单、查看状态、签到、排行榜、签到历史等。
 */
public final class OnlineRewardsPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final int PAGE_SIZE = 10;
    private static final List<String> ROOT_ACTIONS = List.of("help", "open", "status", "signin", "top", "history");

    private final Supplier<OnlineRewardsService> serviceProvider;
    private final Supplier<OnlineRewardsModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider      在线奖励服务提供者
     * @param configurationProvider 模块配置提供者
     * @param messages             消息提供者
     */
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

    /**
     * 处理玩家命令，根据子动作分发到打开菜单、查看状态、签到、排行榜或历史。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param label   命令标签
     * @param args    参数数组
     * @return {@code true} 表示已处理
     */
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
                sendSignInResult(player, service);
            } else {
                sendOperationResult(player, service.openMenu(player));
            }
            return true;
        }

        if ("help".equalsIgnoreCase(args[0])) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open" -> sendOperationResult(player, service.openMenu(player));
            case "status" -> sendStatus(player, service);
            case "signin" -> sendSignInResult(player, service);
            case "top" -> sendLeaderboard(player, service, label, args);
            case "history" -> sendHistory(player, service, label, args);
            default -> player.sendMessage(msg("player.usage", label));
        }
        return true;
    }

    /**
     * 提供玩家命令的 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param alias  命令别名
     * @param args   参数数组
     * @return 补全候选列表
     */
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
        if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
            return List.of("1", "2", "3", "6", "12").stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }

    private void sendSignInResult(Player player, OnlineRewardsService service) {
        var id = player.getUniqueId();
        service.signIn(player).whenComplete((result, failure) -> service.runOnMain(() -> {
            if (!player.isOnline() || org.bukkit.Bukkit.getPlayer(id) != player) return;
            if (failure == null) sendOperationResult(player, result);
            else player.sendMessage(ChatColor.RED + "签到结果未确认，请查看签到记录后再试。");
        }));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(msg("player.help.title"));
        sender.sendMessage(msg("player.help.open"));
        sender.sendMessage(msg("player.help.status"));
        sender.sendMessage(msg("player.help.signin"));
        sender.sendMessage(msg("player.help.top"));
        sender.sendMessage(msg("player.help.history"));
    }

    private void sendStatus(Player player, OnlineRewardsService service) {
        sendReadResult(player, service, service.queryStatus(player.getUniqueId(), player.getName()),
            snapshot -> renderStatus(player, snapshot));
    }

    private void renderStatus(Player player, OnlineRewardsPlayerSnapshot snapshot) {
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
        int page = args.length >= 3 ? Math.max(1, parseInt(args[2], 1)) : 1;
        sendReadResult(player, service, service.loadLeaderboard(scope, PAGE_SIZE, page),
            entries -> renderLeaderboard(player, scope, page, entries));
    }

    private void renderLeaderboard(Player player, OnlineRewardsLeaderboardScope scope, int page,
                                   List<OnlineRewardsLeaderboardEntry> entries) {
        player.sendMessage(msg("player.top.header", scope.key(), page));
        if (entries.isEmpty()) {
            player.sendMessage(msg("player.top.empty"));
            return;
        }
        long startRank = (page - 1L) * PAGE_SIZE + 1;
        for (int index = 0; index < entries.size(); index++) {
            OnlineRewardsLeaderboardEntry entry = entries.get(index);
            player.sendMessage(msg("player.top.entry",
                startRank + index,
                entry.playerName(),
                OnlineRewardsTextFormats.formatMinutes(entry.minutes()),
                entry.minutes()));
        }
    }

    private void sendHistory(Player player, OnlineRewardsService service, String label, String[] args) {
        int months = args.length >= 2 ? Math.max(1, Math.min(24, parseInt(args[1], 6))) : 6;
        sendReadResult(player, service, service.querySignInHistory(player.getUniqueId(), months),
            history -> renderHistory(player, months, history));
    }

    private void renderHistory(Player player, int months, List<OnlineRewardsService.SignInMonth> historyMonths) {
        if (historyMonths.isEmpty()) {
            player.sendMessage(msg("player.history.empty"));
            return;
        }
        player.sendMessage(msg("player.history.header", months));
        for (var entry : historyMonths) {
            player.sendMessage(msg("player.history.entry", entry.month().toString(), entry.signedDays(), entry.month().lengthOfMonth()));
        }
    }

    private <T> void sendReadResult(Player player, OnlineRewardsService service, CompletionStage<T> pending, Consumer<T> render) {
        var id = player.getUniqueId();
        var submitted = AuthAccess.snapshot(id);
        pending.whenComplete((value, failure) -> service.runOnMain(() -> {
            if (!player.isOnline() || Bukkit.getPlayer(id) != player || !player.hasPermission("arcartxsuite.onlinerewards.use")) return;
            var current = AuthAccess.snapshot(id);
            if (!current.admitted() || current.generation() != submitted.generation()
                    || !Objects.equals(current.connectionId(), submitted.connectionId())) return;
            if (failure != null) player.sendMessage(messages.get("prefix") + ChatColor.RED + "在线奖励数据暂不可用，请稍后再试。");
            else render.accept(value);
        }));
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
