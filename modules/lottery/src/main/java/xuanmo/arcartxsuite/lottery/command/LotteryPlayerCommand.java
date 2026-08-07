package xuanmo.arcartxsuite.lottery.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.lottery.LotteryService;

/**
 * Lottery 模块的玩家命令处理器，实现 {@link TabExecutor}。
 * <p>
 * 支持抽奖（单抽/十连）、领取待发放奖励、查看奖池状态与历史记录等操作。
 * 通过 {@code Supplier<LotteryService>} 延迟获取服务实例。
 */
public class LotteryPlayerCommand implements TabExecutor {

    private final Supplier<LotteryService> serviceSupplier;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceSupplier Lottery 服务延迟供应器
     * @param messages         消息提供者
     */
    public LotteryPlayerCommand(Supplier<LotteryService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令分发。
     *
     * @param sender  命令发送者，必须为玩家
     * @param command Bukkit 命令对象
     * @param label   命令标签
     * @param args    命令参数，{@code args[0]} 为子操作名
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }

        LotteryService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(fullMsg("player.help"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "claim" -> service.claimPending(player);
            case "pull" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.pull.usage"));
                    return true;
                }
                String poolId = args[1];
                int count = 1;
                if (args.length > 2) {
                    String mode = args[2].toLowerCase();
                    if ("ten".equals(mode) || "10".equals(mode)) count = 10;
                }
                handlePull(player, service, poolId, count);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.info.usage"));
                    return true;
                }
                handleInfo(player, service, args[1]);
            }
            case "history" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.history.usage"));
                    return true;
                }
                handleHistory(player, service, args[1]);
            }
            default -> player.sendMessage(fullMsg("player.help"));
        }
        return true;
    }

    private void handlePull(Player player, LotteryService service, String poolId, int count) {
        var pool = service.getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        try {
            if (pool.type() == xuanmo.arcartxsuite.lottery.config.PoolType.GACHA) {
                var result = service.pullGacha(player, poolId, count);
                player.sendMessage(fullMsg("pull.result", ChatColor.translateAlternateColorCodes('&', pool.displayName()), formatItems(result.items())));
            } else {
                for (int i = 0; i < count; i++) {
                    var result = service.openCase(player, poolId);
                    if (result != null && result.item() != null) {
                        player.sendMessage(fullMsg("pull.result", ChatColor.translateAlternateColorCodes('&', pool.displayName()), result.item().name()));
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(fullMsg("common.error", e.getMessage()));
        }
    }

    private void handleInfo(Player player, LotteryService service, String poolId) {
        var pool = service.getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        if (pool.type() == xuanmo.arcartxsuite.lottery.config.PoolType.GACHA) {
            var state = service.getGachaState(player, poolId);
            player.sendMessage(fullMsg("player.info.gacha", ChatColor.translateAlternateColorCodes('&', pool.displayName()), state.pity5star(), state.pity4star()));
        } else {
            var state = service.getCaseState(player, poolId);
            player.sendMessage(fullMsg("player.info.case", ChatColor.translateAlternateColorCodes('&', pool.displayName()), state.openCount()));
        }
    }

    private void handleHistory(Player player, LotteryService service, String poolId) {
        var pool = service.getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        if (pool.type() == xuanmo.arcartxsuite.lottery.config.PoolType.GACHA) {
            var history = service.getGachaHistory(player.getUniqueId(), poolId, 10);
            player.sendMessage(fullMsg("player.history.title", ChatColor.translateAlternateColorCodes('&', pool.displayName())));
            for (var entry : history) {
                player.sendMessage(fullMsg("player.history.entry", entry.itemsJson(), entry.pullTime()));
            }
        } else {
            var history = service.getCaseHistory(player.getUniqueId(), poolId, 10);
            player.sendMessage(fullMsg("player.history.title", ChatColor.translateAlternateColorCodes('&', pool.displayName())));
            for (var entry : history) {
                player.sendMessage(fullMsg("player.history.entry", entry.itemId(), entry.openTime()));
            }
        }
    }

    private String formatItems(List<xuanmo.arcartxsuite.lottery.model.PoolItem> items) {
        if (items == null || items.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i) != null ? items.get(i).name() : "?");
        }
        return sb.toString();
    }

    /**
     * 提供玩家命令 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param command Bukkit 命令对象
     * @param alias   命令别名
     * @param args    当前已输入的参数
     * @return 补全候选列表，无匹配时返回空列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        LotteryService service = serviceSupplier.get();
        if (service == null) return Collections.emptyList();

        if (args.length == 1) {
            return List.of("claim", "pull", "info", "history");
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>(service.getPools().keySet());
            return filter(ids, args[1]);
        }
        if (args.length == 3 && "pull".equalsIgnoreCase(args[0])) {
            return List.of("single", "ten");
        }
        return Collections.emptyList();
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
