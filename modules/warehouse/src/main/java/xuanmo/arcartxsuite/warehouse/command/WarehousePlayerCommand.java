package xuanmo.arcartxsuite.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;

/**
 * 仓库模块的玩家命令处理器。
 * <p>
 * 实现 {@link org.bukkit.command.TabExecutor}，绑定到玩家可执行的仓库命令，
 * 支持 open（打开仓库）、showcase（展示柜）、preview（管理员预览他人仓库）、
 * spreview（静默预览）、transfer（确认/拒绝共享仓库转让）等子命令。
 * 玩家需持有 {@code arcartxsuite.warehouse.use} 权限；preview 额外要求 admin 权限。
 * 关联模块：warehouse。
 */
public final class WarehousePlayerCommand implements org.bukkit.command.TabExecutor {

    private final Supplier<WarehouseService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider 仓库服务提供者，延迟获取 {@link WarehouseService} 实例
     * @param messages        消息提供者，用于读取本地化提示文本
     */
    public WarehousePlayerCommand(Supplier<WarehouseService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 执行玩家子命令，根据 {@code args[0]} 分发到 open / showcase / preview / spreview / transfer。
     *
     * @param sender  命令发送者，必须是玩家
     * @param command 命令对象
     * @param label   命令标签
     * @param args    参数数组（args[0] 为子命令名）
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (!player.hasPermission("arcartxsuite.warehouse.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        WarehouseService service = serviceProvider.get();
        if (service == null) {
            player.sendMessage(fullMsg("player.module-down"));
            return true;
        }
        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
            return true;
        }
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "open";
        switch (subCommand) {
            case "transfer" -> {
                if (args.length < 3 || !("confirm".equalsIgnoreCase(args[1]) || "reject".equalsIgnoreCase(args[1]))) {
                    player.sendMessage(fullMsg("player.transfer.usage", label));
                    break;
                }
                WarehouseService.ActionResult result = "confirm".equalsIgnoreCase(args[1])
                    ? service.confirmPendingTransfer(player, args[2])
                    : service.rejectPendingTransfer(player, args[2]);
                player.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            case "showcase" -> {
                WarehouseService.ActionResult showcaseResult = service.showcase(player);
                if (!showcaseResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + showcaseResult.message());
                }
            }
            case "preview" -> {
                if (!player.hasPermission("arcartxsuite.warehouse.admin")) {
                    player.sendMessage(fullMsg("player.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.preview.usage", label));
                    return true;
                }
                UUID targetUuid = null;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ignored) {
                }
                if (targetUuid == null) {
                    Player online = Bukkit.getPlayer(args[1]);
                    if (online != null) {
                        targetUuid = online.getUniqueId();
                    } else {
                        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                        if (offline.hasPlayedBefore()) {
                            targetUuid = offline.getUniqueId();
                        }
                    }
                }
                if (targetUuid == null) {
                    player.sendMessage(fullMsg("player.preview.not-found", args[1]));
                    return true;
                }
                String warehouseId = args.length > 2 ? args[2] : "";
                WarehouseService.ActionResult previewResult = service.openPreview(player, targetUuid, warehouseId);
                if (!previewResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + previewResult.message());
                }
            }
            case "spreview" -> {
                if (args.length < 2) {
                    return true;
                }
                UUID targetUuid = null;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ignored) {
                }
                if (targetUuid == null) {
                    Player online = Bukkit.getPlayer(args[1]);
                    if (online != null) {
                        targetUuid = online.getUniqueId();
                    } else {
                        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                        if (offline.hasPlayedBefore()) {
                            targetUuid = offline.getUniqueId();
                        }
                    }
                }
                if (targetUuid == null) {
                    return true;
                }
                String warehouseId = args.length > 2 ? args[2] : "";
                WarehouseService.ActionResult previewResult = service.openPreview(player, targetUuid, warehouseId);
                if (!previewResult.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + previewResult.message());
                }
            }
            case "open" -> {
                WarehouseService.ActionResult result = service.openMenu(player);
                if (!result.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + result.message());
                }
            }
            default -> {
                WarehouseService.ActionResult result = service.openMenu(player);
                if (!result.success()) {
                    player.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.RED + result.message());
                }
            }
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.open"));
        player.sendMessage(fullMsg("player.help.showcase"));
        player.sendMessage(fullMsg("player.help.preview"));
        player.sendMessage(fullMsg("player.help.transfer"));
    }

    /**
     * 提供玩家子命令的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param alias  命令别名
     * @param args   参数数组
     * @return 补全列表
     */
    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String cmd : List.of("help", "open", "showcase", "preview", "transfer")) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase().startsWith(prefix)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return List.of();
    }
}
