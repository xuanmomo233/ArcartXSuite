package xuanmo.arcartxsuite.map.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.service.MapService;

/**
 * 地图模块玩家命令处理器，提供 open / hud / cleartrack 等玩家子命令。
 * <p>
 * 关联模块：map。以 Bukkit {@code TabExecutor} 形式注册到 {@code /map} 命令，
 * 供玩家打开地图界面、切换小地图 HUD 显示及清除当前导航追踪。
 */
public final class MapPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("help", "open", "hud", "cleartrack", "claim", "unlock");

    private final Supplier<MapService> serviceProvider;
    private final Supplier<MapModuleConfiguration> configurationProvider;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider       地图服务提供者（允许服务未就绪时返回 {@code null}）
     * @param configurationProvider 模块配置提供者（用于 Tab 补全世界列表）
     * @param messages              消息提供者，用于获取本地化提示文本
     */
    public MapPlayerCommand(
        Supplier<MapService> serviceProvider,
        Supplier<MapModuleConfiguration> configurationProvider,
        MessageProvider messages
    ) {
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令，根据子动作（open / hud / cleartrack）分发到对应服务方法。
     *
     * @param sender  命令发送者，必须是玩家
     * @param command 命令对象
     * @param label   命令标签
     * @param args    命令参数数组
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (!player.hasPermission("arcartxsuite.map.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        MapService svc = serviceProvider.get();
        if (svc == null) {
            player.sendMessage(fullMsg("common.module-down"));
            return true;
        }

        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            String worldId = args.length >= 2 ? args[1] : "";
            sendResult(player, svc.openMenuFor(player, worldId));
            return true;
        }

        if ("hud".equalsIgnoreCase(args[0])) {
            String mode = args.length >= 2 ? args[1] : "toggle";
            sendResult(player, svc.setHud(player, mode));
            return true;
        }

        if ("cleartrack".equalsIgnoreCase(args[0])) {
            sendResult(player, svc.clearTrackCommand(player));
            return true;
        }

        if ("unlock".equalsIgnoreCase(args[0])) {
            // 手动解锁锚点：走完整流程（permission + unlock.radius 距离门 + 费用）
            if (args.length >= 2) {
                svc.unlockAnchorAsync(player, args[1]);
            } else {
                player.sendMessage(fullMsg("player.unlock-usage", label));
            }
            return true;
        }

        if ("claim".equalsIgnoreCase(args[0])) {
            // 领取分享点位：聊天卡片点击的兜底命令（卡片失效后可手动输入分享码）
            if (args.length >= 2) {
                svc.claimShare(player, args[1]);
            } else {
                player.sendMessage(fullMsg("player.claim-usage", label));
            }
            return true;
        }

        player.sendMessage(fullMsg("player.usage", label));
        return true;
    }

    /**
     * 提供玩家命令的 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param alias  命令别名
     * @param args   当前参数数组
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
        if (args.length == 2 && "hud".equalsIgnoreCase(args[0])) {
            return List.of("on", "off", "toggle").stream().filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        MapModuleConfiguration cfg = configurationProvider.get();
        if (args.length == 2 && "open".equalsIgnoreCase(args[0]) && cfg != null) {
            return new ArrayList<>(cfg.worlds().keySet()).stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && "unlock".equalsIgnoreCase(args[0]) && cfg != null) {
            return new ArrayList<>(cfg.anchors().keySet()).stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }

    private void showHelp(Player player) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.open"));
        player.sendMessage(fullMsg("player.help.hud"));
        player.sendMessage(fullMsg("player.help.cleartrack"));
        player.sendMessage(fullMsg("player.help.unlock"));
        player.sendMessage(fullMsg("player.help.claim"));
    }

    private void sendResult(Player player, MapOperationResult result) {
        if (result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }
}
