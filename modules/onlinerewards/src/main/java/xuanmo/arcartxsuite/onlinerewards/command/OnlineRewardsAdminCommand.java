package xuanmo.arcartxsuite.onlinerewards.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsOperationResult;
import xuanmo.arcartxsuite.onlinerewards.service.OnlineRewardsService;

/**
 * 在线奖励管理命令处理器。
 * <p>
 * 属于 {@code onlinerewards} 模块，提供 {@code /axs onlinerewards <action>} 管理子命令，
 * 包括查看状态、重载、调整在线时长、补签卡、离线储蓄等操作。
 */
public final class OnlineRewardsAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "add", "remove", "set", "card", "savings");

    private final Supplier<OnlineRewardsService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造管理命令处理器。
     *
     * @param serviceProvider 在线奖励服务提供者
     * @param messages        消息提供者
     */
    public OnlineRewardsAdminCommand(Supplier<OnlineRewardsService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "onlinerewards"; }
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 处理管理命令，根据子动作分发到对应处理逻辑。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> showHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "add", "remove", "set" -> handleTimeAdjust(sender, action, args);
            case "card" -> handleCard(sender, args);
            case "savings" -> handleSavings(sender, args);
            default -> showHelp(sender, label);
        }
        return true;
    }

    /**
     * 提供管理命令的 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   参数数组
     * @return 补全候选列表，返回 {@code null} 表示由 Bukkit 补全玩家名
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            String a = args[1].toLowerCase(Locale.ROOT);
            if (List.of("add", "remove", "set").contains(a)) return filter(List.of("30m", "1h", "2h", "12h", "1d"), args[2]);
            if ("card".equals(a)) return filter(List.of("add", "remove", "set"), args[2]);
            if ("savings".equals(a)) return filter(List.of("add", "set", "reset"), args[2]);
        }
        if (args.length == 4) {
            String a = args[1].toLowerCase(Locale.ROOT);
            if (List.of("add", "remove", "set").contains(a)) return null; // player names
            if ("card".equals(a)) return filter(List.of("1", "5", "10"), args[3]);
            if ("savings".equals(a)) return filter(List.of("30m", "1h", "2h"), args[3]);
        }
        if (args.length == 5 && ("card".equalsIgnoreCase(args[1]) || "savings".equalsIgnoreCase(args[1]))) {
            return null; // player names
        }
        return List.of();
    }

    private void showHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " onlinerewards";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.add", cmd));
        sender.sendMessage(msg("help.remove", cmd));
        sender.sendMessage(msg("help.set", cmd));
        sender.sendMessage(msg("help.card", cmd));
        sender.sendMessage(msg("help.savings", cmd));
    }

    private void sendStatus(CommandSender sender) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.cached", svc.cachedPlayerCount()));
        sender.sendMessage(msg("admin.status.cross-server",
            svc.crossServerActive() ? ChatColor.GREEN + "已启用" : ChatColor.YELLOW + "未启用"));
    }

    private void handleTimeAdjust(CommandSender sender, String operation, String[] args) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 4) {
            sender.sendMessage(msg("admin.time.usage", operation));
            return;
        }
        int minutes = parseDurationMinutes(args[2]);
        if (minutes <= 0 && !"set".equals(operation)) {
            sender.sendMessage(msg("admin.time.invalid", args[2]));
            return;
        }
        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[3])); return; }
        OnlineRewardsOperationResult result = svc.adjustOnlineTime(target, operation, minutes);
        sender.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleCard(CommandSender sender, String[] args) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 5) {
            sender.sendMessage(msg("admin.card.usage"));
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        int amount;
        try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.card.invalid-amount", args[3])); return;
        }
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[4])); return; }
        OnlineRewardsOperationResult result = svc.adjustMakeupCards(target, operation, amount);
        sender.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleSavings(CommandSender sender, String[] args) {
        OnlineRewardsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 5) {
            sender.sendMessage(msg("admin.savings.usage"));
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        int minutes = parseDurationMinutes(args[3]);
        if (minutes <= 0 && !"reset".equals(operation) && !"set".equals(operation)) {
            sender.sendMessage(msg("admin.savings.invalid", args[3]));
            return;
        }
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) { sender.sendMessage(msg("common.player-offline", args[4])); return; }
        OnlineRewardsOperationResult result = svc.adjustOfflineSavings(target, operation, minutes);
        sender.sendMessage(messages.get("prefix") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static int parseDurationMinutes(String input) {
        if (input == null || input.isBlank()) return 0;
        String lower = input.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("d")) return Integer.parseInt(lower.replace("d", "")) * 1440;
            if (lower.endsWith("h")) return Integer.parseInt(lower.replace("h", "")) * 60;
            if (lower.endsWith("m")) return Integer.parseInt(lower.replace("m", ""));
            return Integer.parseInt(lower);
        } catch (NumberFormatException e) { return 0; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
