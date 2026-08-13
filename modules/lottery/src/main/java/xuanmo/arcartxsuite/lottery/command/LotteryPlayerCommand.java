package xuanmo.arcartxsuite.lottery.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.lottery.LotteryService;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.packet.LotteryPacketHandler;

/**
 * Lottery 模块的玩家命令处理器，实现 {@link TabExecutor}。
 * <p>
 * 支持打开抽奖 UI（open）、查看奖池详情（info）、查看历史记录（history）、
 * 领取待发放奖励（claim）等操作。通过 {@code Supplier} 延迟获取服务与桥接实例。
 */
public class LotteryPlayerCommand implements TabExecutor {

    private final Supplier<LotteryService> serviceSupplier;
    private final MessageProvider messages;
    private final Supplier<LotteryPacketHandler> packetHandlerSupplier;
    private final Supplier<PacketBridgeAPI> packetBridgeSupplier;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceSupplier      Lottery 服务延迟供应器
     * @param messages             消息提供者
     * @param packetHandlerSupplier 客户端包处理器延迟供应器
     * @param packetBridgeSupplier  Packet 桥接延迟供应器
     */
    public LotteryPlayerCommand(Supplier<LotteryService> serviceSupplier, MessageProvider messages,
                                Supplier<LotteryPacketHandler> packetHandlerSupplier,
                                Supplier<PacketBridgeAPI> packetBridgeSupplier) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
        this.packetHandlerSupplier = packetHandlerSupplier;
        this.packetBridgeSupplier = packetBridgeSupplier;
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

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        if ("help".equalsIgnoreCase(args[0])) {
            showHelp(sender);
            return true;
        }

        LotteryService service = serviceSupplier.get();
        if (service == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "claim" -> service.claimPending(player);
            case "open" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.open.usage"));
                    return true;
                }
                handleOpen(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.info.usage"));
                    return true;
                }
                handleInfo(player, args[1]);
            }
            case "history" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.history.usage"));
                    return true;
                }
                handleHistory(player, args[1]);
            }
            default -> showHelp(sender);
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(fullMsg("player.help.title"));
        sender.sendMessage(fullMsg("player.help.open"));
        sender.sendMessage(fullMsg("player.help.info"));
        sender.sendMessage(fullMsg("player.help.history"));
        sender.sendMessage(fullMsg("player.help.claim"));
    }

    private void handleOpen(Player player, String poolId) {
        PoolDefinition pool = serviceSupplier.get().getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        if (!pool.enabled()) {
            player.sendMessage(fullMsg("player.open.disabled", poolId));
            return;
        }
        PacketBridgeAPI bridge = packetBridgeSupplier.get();
        if (bridge == null || !bridge.isAvailable()) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        LotteryPacketHandler handler = packetHandlerSupplier.get();
        if (handler == null) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        if (!handler.openPoolUi(player, poolId)) {
            player.sendMessage(fullMsg("player.open.failed", poolId));
        }
    }

    private void handleInfo(Player player, String poolId) {
        PoolDefinition pool = serviceSupplier.get().getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        if (!pool.enabled()) {
            player.sendMessage(fullMsg("player.open.disabled", poolId));
            return;
        }
        PacketBridgeAPI bridge = packetBridgeSupplier.get();
        if (bridge == null || !bridge.isAvailable()) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        LotteryPacketHandler handler = packetHandlerSupplier.get();
        if (handler == null) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        if (!handler.openPoolDetails(player, poolId)) {
            player.sendMessage(fullMsg("player.open.failed", poolId));
        }
    }

    private void handleHistory(Player player, String poolId) {
        PoolDefinition pool = serviceSupplier.get().getPool(poolId);
        if (pool == null) {
            player.sendMessage(fullMsg("common.pool-not-found", poolId));
            return;
        }
        if (!pool.enabled()) {
            player.sendMessage(fullMsg("player.open.disabled", poolId));
            return;
        }
        PacketBridgeAPI bridge = packetBridgeSupplier.get();
        if (bridge == null || !bridge.isAvailable()) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        LotteryPacketHandler handler = packetHandlerSupplier.get();
        if (handler == null) {
            player.sendMessage(fullMsg("player.open.ui-unavailable"));
            return;
        }
        if (!handler.openPoolHistory(player, poolId)) {
            player.sendMessage(fullMsg("player.open.failed", poolId));
        }
    }

    /**
     * 提供玩家命令 Tab 补全建议。
     * <p>
     * 子命令补全不依赖 service，确保即使服务未就绪也能补全子命令。
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
        // 子命令补全不依赖 service，确保即使服务未就绪也能补全
        if (args.length == 1) {
            return filter(List.of("help", "open", "info", "history", "claim"), args[0]);
        }
        // 第 2 参数（奖池 ID）补全需要 service
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("open".equals(sub) || "info".equals(sub) || "history".equals(sub)) {
                LotteryService service = serviceSupplier.get();
                if (service == null) return Collections.emptyList();
                List<String> ids = new ArrayList<>(service.getPools().keySet());
                return filter(ids, args[1]);
            }
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
