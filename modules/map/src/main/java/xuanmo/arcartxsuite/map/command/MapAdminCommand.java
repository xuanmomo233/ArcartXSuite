package xuanmo.arcartxsuite.map.command;

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
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.service.MapService;

/**
 * 地图模块管理命令处理器，提供 status / open / list / anchors 等管理子命令。
 * <p>
 * 关联模块：map。通过宿主 {@code ModuleCommandHandler} 机制注册，
 * 供管理员查看模块运行状态、为玩家打开地图及列出已配置的世界与锚点。
 */
public final class MapAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "open", "list", "anchors", "unlock", "refresh");

    private final Supplier<MapService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造管理命令处理器。
     *
     * @param serviceProvider 地图服务提供者（允许服务未就绪时返回 {@code null}）
     * @param messages        消息提供者，用于获取本地化提示文本
     */
    public MapAdminCommand(Supplier<MapService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "map"; }
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 处理管理命令，根据子动作分发到对应处理逻辑。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数数组
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "open" -> handleOpen(sender, args);
            case "list" -> handleList(sender);
            case "anchors" -> handleAnchors(sender, args);
            case "unlock" -> handleUnlock(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    /**
     * 提供管理命令的 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表，返回 {@code null} 表示交由 Bukkit 补全玩家名
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        MapService svc = serviceProvider.get();
        if (svc == null) return List.of();
        if (args.length == 3) {
            if ("open".equalsIgnoreCase(args[1])) return null; // player names
            if ("unlock".equalsIgnoreCase(args[1])) return null; // player names
            if ("anchors".equalsIgnoreCase(args[1])) return filter(svc.configuredWorldIds(), args[2]);
            if ("refresh".equalsIgnoreCase(args[1])) return filter(svc.configuredWorldIds(), args[2]);
        }
        if (args.length == 4 && "open".equalsIgnoreCase(args[1])) {
            return filter(svc.configuredWorldIds(), args[3]);
        }
        if (args.length == 4 && "unlock".equalsIgnoreCase(args[1])) {
            return filter(svc.configuredAnchorIds(), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " map";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.list", cmd));
        sender.sendMessage(fullMsg("help.anchors", cmd));
        sender.sendMessage(fullMsg("help.unlock", cmd));
        sender.sendMessage(fullMsg("help.refresh", cmd));
    }

    /** /axs map refresh <worldId> —— 手动刷该世界预加载版本号，客户端下次同步自动重拉地形。 */
    private void handleRefresh(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("refresh.usage")); return; }
        MapOperationResult result = svc.refreshPreloadVersion(args[2]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "")
            + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    /** /axs map unlock <player> <anchorId> —— 免费强制解锁（跳付费/距离门）。 */
    private void handleUnlock(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 4) { sender.sendMessage(fullMsg("unlock.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        MapOperationResult result = svc.grantAnchorUnlock(target, args[3]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "")
            + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void sendStatus(CommandSender sender) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.worlds", svc.configuredWorldCount()));
        sender.sendMessage(fullMsg("status.anchors", svc.configuredAnchorCount()));
        sender.sendMessage(fullMsg("status.tracking", svc.trackingPlayerCount()));
        sender.sendMessage(fullMsg("status.waypoint", svc.waypointRuntimeReady()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        String worldId = args.length >= 4 ? args[3] : "";
        MapOperationResult result = svc.openMenuFor(target, worldId);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleList(CommandSender sender) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        List<String> worlds = svc.configuredWorldIds();
        if (worlds.isEmpty()) { sender.sendMessage(fullMsg("list.empty")); return; }
        sender.sendMessage(fullMsg("list.title", worlds.size()));
        for (String w : worlds) {
            sender.sendMessage(fullMsg("list.item", w));
        }
    }

    private void handleAnchors(CommandSender sender, String[] args) {
        MapService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        // 按锚点所属世界过滤（锚点 id 不一定包含世界名，子串匹配会漏报/误报）
        String worldFilter = args.length >= 3 ? args[2] : null;
        List<String> anchors = worldFilter == null
            ? svc.configuredAnchorIds()
            : svc.configuredAnchorIds(worldFilter);
        if (anchors.isEmpty()) { sender.sendMessage(fullMsg("anchors.empty")); return; }
        sender.sendMessage(fullMsg("anchors.title", worldFilter != null ? " (世界: " + worldFilter + ")" : ""));
        for (String a : anchors) {
            sender.sendMessage(fullMsg("anchors.item", a));
        }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
