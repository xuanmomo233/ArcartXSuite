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

/**
 * 挂机奖励玩家命令处理器。
 * <p>
 * 关联模块：afkreward。提供 {@code toggle}、{@code status}、{@code start}、{@code end}、
 * {@code list}、{@code top} 等子命令，玩家可通过命令查看挂机状态、手动开始/结束原地挂机、
 * 查看在线挂机列表与排行榜。
 */
public final class AfkRewardPlayerCommand implements TabExecutor {

    private static final String PERMISSION = "arcartxsuite.afkreward.use";
    private static final List<String> ACTIONS = List.of("help", "toggle", "status", "start", "end", "list", "top");

    private final Supplier<AfkRewardService> serviceSupplier;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceSupplier 挂机奖励服务提供者（服务可能尚未就绪，故使用 Supplier 延迟获取）
     * @param messages        消息提供者，用于读取本地化提示文本
     */
    public AfkRewardPlayerCommand(Supplier<AfkRewardService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令，根据子命令分发到对应逻辑。
     *
     * @param sender  命令发送者（仅玩家可用）
     * @param command 命令对象
     * @param label   命令标签
     * @param args    参数数组（{@code args[0]} 为子命令）
     * @return {@code true} 表示已处理
     */
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

        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
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

    private void showHelp(Player player) {
        player.sendMessage(msg("player.help.title"));
        player.sendMessage(msg("player.help.toggle"));
        player.sendMessage(msg("player.help.status"));
        player.sendMessage(msg("player.help.start"));
        player.sendMessage(msg("player.help.end"));
        player.sendMessage(msg("player.help.list"));
        player.sendMessage(msg("player.help.top"));
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

    /**
     * 提供子命令与区域名补全。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param alias   命令别名
     * @param args    当前参数数组
     * @return 补全候选列表；无补全时返回空列表
     */
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
