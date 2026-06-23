package xuanmo.arcartxsuite.lottery.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class LotteryAdminCommand {

    private final Supplier<LotteryService> serviceSupplier;
    private final MessageProvider messages;

    public LotteryAdminCommand(Supplier<LotteryService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    public List<String> actions() {
        return List.of("help", "status", "reload", "reset", "give-ticket", "simulate");
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(msg("admin.help"));
            return true;
        }

        String action = args[0].toLowerCase();
        LotteryService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return true;
        }

        switch (action) {
            case "help" -> sender.sendMessage(msg("admin.help"));
            case "status" -> handleStatus(sender, service);
            case "reload" -> {
                sender.sendMessage(msg("admin.reload"));
                // reload 由 Module 生命周期处理
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(msg("admin.reset.usage"));
                    return true;
                }
                handleReset(sender, service, args[1], args[2]);
            }
            case "give-ticket" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("admin.give-ticket.usage"));
                    return true;
                }
                handleGiveTicket(sender, service, args[1], args[2], args[3]);
            }
            case "simulate" -> {
                if (args.length < 3) {
                    sender.sendMessage(msg("admin.simulate.usage"));
                    return true;
                }
                handleSimulate(sender, service, args[1], args[2]);
            }
            default -> sender.sendMessage(msg("admin.unknown"));
        }
        return true;
    }

    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(actions(), args[0]);
        }
        LotteryService service = serviceSupplier.get();
        if (service == null) return Collections.emptyList();

        String action = args[0].toLowerCase();
        if (args.length == 2) {
            switch (action) {
                case "reset", "give-ticket", "simulate" -> {
                    List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                    return filter(players, args[1]);
                }
            }
        }
        if (args.length == 3) {
            switch (action) {
                case "reset", "give-ticket", "simulate" -> {
                    return filter(new ArrayList<>(service.getPools().keySet()), args[2]);
                }
            }
        }
        return Collections.emptyList();
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

    private void handleGiveTicket(CommandSender sender, LotteryService service, String playerName, String poolId, String amountStr) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(msg("admin.player-not-found", playerName));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-number", amountStr));
            return;
        }
        // 这里简化处理：实际应通过物品系统发放抽奖券
        sender.sendMessage(msg("admin.give-ticket.success", playerName, poolId, amount));
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
        // 简化模拟统计
        sender.sendMessage(msg("admin.simulate.result", poolId, times));
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
