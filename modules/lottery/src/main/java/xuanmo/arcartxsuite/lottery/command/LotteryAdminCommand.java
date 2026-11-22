package xuanmo.arcartxsuite.lottery.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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

    public LotteryAdminCommand(Supplier<LotteryService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    /**
     * 返回该命令支持的全部子操作列表。
     *
     * @return 不可变操作名称列表
     */
    public List<String> actions() {
        return List.of("help", "status", "reset", "give-points", "simulate", "gacha-review", "gacha-resolve");
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
        // args[0] 为模块 id（lottery），args[1] 开始为子命令（与 ModuleCommandHandler 接口约定一致）
        if (args.length < 2) {
            showHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase();
        LotteryService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return true;
        }

        switch (action) {
            case "help" -> showHelp(sender);
            case "status" -> handleStatus(sender, service);
            case "reset" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("admin.reset.usage"));
                    return true;
                }
                handleReset(sender, service, args[2], args[3]);
            }
            case "give-points" -> {
                if (args.length < 5) {
                    sender.sendMessage(msg("admin.give-points.usage"));
                    return true;
                }
                handleGivePoints(sender, service, args[2], args[3], args[4]);
            }
            case "simulate" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("admin.simulate.usage"));
                    return true;
                }
                handleSimulate(sender, service, args[2], args[3]);
            }
            case "gacha-review" -> inspectGachaOperation(sender, service, args);
            case "gacha-resolve" -> resolveGachaOperation(sender, service, args);
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
        // args[0] 为模块 id（lottery），args[1] 开始为子命令
        // args.length == 1 时返回 null，让 ArcartXSuiteCommand fallback 到 actions() 补全
        if (args.length < 2) {
            return null;
        }
        LotteryService service = serviceSupplier.get();
        if (service == null) return Collections.emptyList();

        String action = args[1].toLowerCase();
        if (args.length == 2) {
            return filter(actions(), args[1]);
        }
        if (args.length == 3) {
            switch (action) {
                case "reset", "give-points" -> {
                    List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                    return filter(players, args[2]);
                }
                case "simulate" -> {
                    return filter(new ArrayList<>(service.getPools().keySet()), args[2]);
                }
            }
        }
        if (args.length == 4) {
            switch (action) {
                case "reset" -> {
                    return filter(new ArrayList<>(service.getPools().keySet()), args[3]);
                }
                case "give-points" -> {
                    // 补全所有奖池中定义的积分点 ID
                    java.util.Set<String> pointIds = new java.util.LinkedHashSet<>();
                    for (var pool : service.getPools().values()) {
                        pointIds.addAll(pool.points().keySet());
                    }
                    return filter(new ArrayList<>(pointIds), args[3]);
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
        sender.sendMessage(msg("help.gacha-review", cmd));
        if (sender instanceof ConsoleCommandSender) sender.sendMessage(msg("help.gacha-resolve", cmd));
    }

    private void inspectGachaOperation(CommandSender sender, LotteryService service, String[] args) {
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage(msg("help.gacha-review", "/axs lottery"));
            return;
        }
        try {
            Player player = Bukkit.getPlayerExact(args[2]);
            UUID playerId = player == null ? UUID.fromString(args[2]) : player.getUniqueId();
            UUID requested = args.length == 4 ? UUID.fromString(args[3]) : null;
            var operation = requested == null ? service.getGachaOperation(playerId)
                : service.getGachaOperation(playerId, requested);
            UUID operationId = operation == null ? requested : operation.id();
            var refunds = operationId == null ? java.util.List.<xuanmo.arcartxsuite.lottery.storage.LotteryRepository.RefundClaim>of()
                : service.getRefundClaims(playerId, operationId);
            if (operation == null && refunds.isEmpty()) {
                sender.sendMessage(msg("admin.gacha-review.empty", playerId));
                return;
            }
            if (operation != null) {
                sender.sendMessage(msg("admin.gacha-review.entry", playerId, operation.id(), operation.poolId(), operation.status()));
                operation.details().lines().forEach(line -> {
                    int snapshot = line.indexOf(" 物品快照=");
                    if (snapshot >= 0) line = line.substring(0, snapshot) + "（完整物品快照保存在凭证表）";
                    sender.sendMessage(line.length() > 500 ? line.substring(0, 500) + "…" : line);
                });
            }
            if (!refunds.isEmpty()) {
                sender.sendMessage("§e操作 " + operationId + " 的退料记录");
                for (var refund : refunds) {
                    sender.sendMessage("§7#" + refund.id() + " " + refund.itemMetadata()
                        + (refund.transferred() ? " 已转入待发奖励" : " 待领取")
                        + (refund.transferId() == null ? "" : " 转交凭证 " + refund.transferId()));
                }
            }
        } catch (IllegalArgumentException failure) {
            sender.sendMessage(msg("help.gacha-review", "/axs lottery"));
        } catch (java.sql.SQLException failure) {
            service.getLogger().log(java.util.logging.Level.WARNING, "无法读取抽卡核对记录", failure);
            sender.sendMessage(msg("common.error"));
        }
    }

    private void resolveGachaOperation(CommandSender sender, LotteryService service, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(msg("admin.gacha-review.console-only"));
            return;
        }
        if (args.length < 6) {
            sender.sendMessage(msg("help.gacha-resolve", "/axs lottery"));
            sender.sendMessage(msg("admin.gacha-review.resolve-scope"));
            return;
        }
        try {
            UUID playerId = UUID.fromString(args[2]);
            UUID operation = UUID.fromString(args[3]);
            String phase = args[4].toUpperCase(Locale.ROOT);
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));
            if (reason.isBlank() || reason.length() > 500) {
                sender.sendMessage(msg("help.gacha-resolve", "/axs lottery"));
                return;
            }
            if (service.resolveGachaOperation(playerId, operation, phase, reason)) {
                service.getLogger().info("管理员关闭已核对的抽卡凭证: player=" + playerId + " operation=" + operation);
                sender.sendMessage(msg("admin.gacha-review.resolved", operation));
            } else {
                sender.sendMessage(msg("admin.gacha-review.changed", operation));
            }
        } catch (IllegalArgumentException failure) {
            sender.sendMessage(msg("help.gacha-resolve", "/axs lottery"));
        } catch (java.sql.SQLException failure) {
            service.getLogger().log(java.util.logging.Level.WARNING, "无法关闭抽卡核对记录", failure);
            sender.sendMessage(msg("common.error"));
        }
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
            if (!service.resetGachaState(target.getUniqueId(), poolId)) {
                sender.sendMessage(msg("common.error"));
                return;
            }
        } else if (!service.resetCaseState(target.getUniqueId(), poolId)) {
            sender.sendMessage(msg("common.error"));
            return;
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
            if (amount <= 0) {
                sender.sendMessage(msg("admin.invalid-number", amountStr));
                return;
            }
            service.givePoints(target.getUniqueId(), pointId, amount).whenComplete((saved, failure) ->
                service.reply(() -> {
                    if (sender instanceof Player player && (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                        || !player.hasPermission("arcartxsuite.admin"))) return;
                    if (failure == null && Boolean.TRUE.equals(saved))
                        sender.sendMessage(msg("admin.give-points.success", playerName, amount, pointId));
                    else sender.sendMessage(msg("common.error"));
                }));
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
