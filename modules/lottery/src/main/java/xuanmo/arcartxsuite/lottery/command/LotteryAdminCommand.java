package xuanmo.arcartxsuite.lottery.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.lottery.LotteryService;

/**
 * Lottery 模块的管理员命令处理器。
 * <p>
 * 提供状态查看、配置重载、状态重置、发放积分、模拟抽奖等管理操作。
 * 通过 {@code Supplier<LotteryService>} 延迟获取服务实例，避免模块未就绪时的空指针。
 */
public class LotteryAdminCommand {

    private final Supplier<LotteryService> serviceSupplier;
    private final MessageProvider messages;
    private final Runnable reloadAction;

    public LotteryAdminCommand(Supplier<LotteryService> serviceSupplier, MessageProvider messages) {
        this(serviceSupplier, messages, () -> {});
    }

    public LotteryAdminCommand(Supplier<LotteryService> serviceSupplier,
                               MessageProvider messages, Runnable reloadAction) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
        this.reloadAction = reloadAction;
    }

    /**
     * 返回该命令支持的全部子操作列表。
     *
     * @return 不可变操作名称列表
     */
    public List<String> actions() {
        return List.of("help", "status", "reset", "give-points", "simulate");
    }

    /**
     * 处理管理员命令分发。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数，{@code args[0]} 为子操作名
     * @return {@code true} 表示命令已处理
     */
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        LotteryService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return true;
        }

        switch (action) {
            case "help" -> showHelp(sender);
            case "status" -> handleStatus(sender, service);
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(msg("admin.reset.usage"));
                    return true;
                }
                handleReset(sender, service, args[1], args[2]);
            }
            case "give-points" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("admin.give-points.usage"));
                    return true;
                }
                handleGivePoints(sender, service, args[1], args[2], args[3]);
            }
            case "simulate" -> {
                if (args.length < 3) {
                    sender.sendMessage(msg("admin.simulate.usage"));
                    return true;
                }
                handleSimulate(sender, service, args[1], args[2]);
            }
            default -> showHelp(sender);
        }
        return true;
    }

    /**
     * 提供命令 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   当前已输入的参数
     * @return 补全候选列表，无匹配时返回空列表
     */
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(actions(), args[0]);
        }
        LotteryService service = serviceSupplier.get();
        if (service == null) return Collections.emptyList();

        String action = args[0].toLowerCase();
        if (args.length == 2) {
            switch (action) {
                case "reset" -> {
                    List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                    return filter(players, args[1]);
                }
                case "simulate" -> {
                    return filter(new ArrayList<>(service.getPools().keySet()), args[1]);
                }
            }
        }
        if (args.length == 3) {
            switch (action) {
                case "reset" -> {
                    return filter(new ArrayList<>(service.getPools().keySet()), args[2]);
                }
            }
        }
        return Collections.emptyList();
    }

    private void showHelp(CommandSender sender) {
        String cmd = "/axs lottery";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.reset", cmd));
        sender.sendMessage(msg("help.give-points", cmd));
        sender.sendMessage(msg("help.simulate", cmd));
    }

    private void handleStatus(CommandSender sender, LotteryService service) {
        var pools = service.getPools();
        sender.sendMessage(msg("admin.status.title"));
        for (var entry : pools.entrySet()) {
            sender.sendMessage(msg("admin.status.pool", entry.getKey(), entry.getValue().type().name(), ChatColor.translateAlternateColorCodes('&', entry.getValue().displayName())));
        }
    }

    private void handleReset(CommandSender sender, LotteryService service, String playerName, String poolId) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(msg("admin.player-not-found", playerName));
            return;
        }
        var pool = service.getPool(poolId);
        if (pool == null) {
            sender.sendMessage(msg("common.pool-not-found", poolId));
            return;
        }
        if (pool.type() == xuanmo.arcartxsuite.lottery.config.PoolType.GACHA) {
            service.resetGachaState(target.getUniqueId(), poolId);
        } else {
            service.resetCaseState(target.getUniqueId(), poolId);
        }
        sender.sendMessage(msg("admin.reset.success", playerName, poolId));
    }

    private void handleSimulate(CommandSender sender, LotteryService service, String poolId, String timesStr) {
        var pool = service.getPool(poolId);
        if (pool == null) {
            sender.sendMessage(msg("common.pool-not-found", poolId));
            return;
        }
        int times;
        try {
            times = Integer.parseInt(timesStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-number", timesStr));
            return;
        }
        if (times <= 0 || times > 100000) {
            sender.sendMessage(msg("admin.simulate.range"));
            return;
        }
        Map<String, Integer> stats = service.simulate(poolId, times);
        sender.sendMessage(msg("admin.simulate.title", poolId, times));
        for (var entry : stats.entrySet()) {
            double rate = (double) entry.getValue() / times * 100;
            sender.sendMessage(msg("admin.simulate.entry", entry.getKey(), entry.getValue(),
                String.format("%.2f", rate)));
        }
    }

    private void handleGivePoints(CommandSender sender, LotteryService service,
                                  String playerName, String pointId, String amountStr) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(msg("admin.player-not-found", playerName));
            return;
        }
        try {
            long amount = Long.parseLong(amountStr);
            if (amount <= 0 || !service.givePoints(target.getUniqueId(), pointId, amount)) {
                sender.sendMessage(msg("admin.invalid-number", amountStr));
                return;
            }
            sender.sendMessage(msg("admin.give-points.success", playerName, amount, pointId));
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-number", amountStr));
        }
    }

    private String msg(String key, Object... args) {
        if (messages == null) return key;
        String prefix = messages.get("prefix");
        return prefix + messages.get(key, args);
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) result.add(s);
        }
        return result;
    }
}
