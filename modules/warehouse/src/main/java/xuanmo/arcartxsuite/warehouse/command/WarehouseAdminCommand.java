package xuanmo.arcartxsuite.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;

/**
 * 仓库模块的管理员命令处理器。
 * <p>
 * 实现 {@link ModuleCommandHandler}，在 {@code /axs warehouse ...} 下注册管理员子命令，
 * 支持 help / status / reload / open / info / view / delete / bank / export 等动作。
 * 所有动作均要求 {@code arcartxsuite.warehouse.admin} 权限。
 * 关联模块：warehouse。
 */
public final class WarehouseAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "open", "info", "view", "delete", "bank", "export", "material-transfers", "material-evidence", "material-confirm");

    private final Supplier<WarehouseService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造管理员命令处理器。
     *
     * @param serviceProvider 仓库服务提供者，延迟获取 {@link WarehouseService} 实例
     * @param messages        消息提供者，用于读取本地化提示文本
     */
    public WarehouseAdminCommand(Supplier<WarehouseService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /** @return 子命令 id，固定为 {@code "warehouse"} */
    @Override public String commandId() { return "warehouse"; }
    /** @return 支持的子命令动作列表 */
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 执行管理员子命令，根据 {@code args[1]} 分发到对应动作处理器。
     *
     * @param sender 命令发送者
     * @param label  根命令标签
     * @param args   完整参数数组（args[0] 为模块 id，args[1] 为动作）
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.hasPermission("arcartxsuite.warehouse.admin")) {
            sender.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "open" -> handleOpen(sender, args);
            case "info" -> handleInfo(sender, args);
            case "view" -> handleView(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "bank" -> handleBank(sender, args);
            case "export" -> handleExport(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    /**
     * 提供管理员子命令的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   完整参数数组
     * @return 补全列表，{@code null} 表示交由 Bukkit 补全玩家名
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3 && List.of("open", "info", "view", "delete", "bank", "export", "material-transfers", "material-evidence", "material-confirm").contains(args[1].toLowerCase(Locale.ROOT))) {
            return null; // player names
        }
        WarehouseService svc = serviceProvider.get();
        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("bank".equalsIgnoreCase(action) && svc != null) return filter(new ArrayList<>(svc.currencyIds()), args[3]);
            if ("delete".equalsIgnoreCase(action) || "view".equalsIgnoreCase(action) || "export".equalsIgnoreCase(action)) {
                org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
                if (target != null && svc != null) return filter(svc.personalWarehouseIds(target.getUniqueId()), args[3]);
                return List.of();
            }
        }
        if (args.length == 5) {
            if ("bank".equalsIgnoreCase(args[1])) return filter(List.of("set", "add", "take"), args[4]);
            if ("delete".equalsIgnoreCase(args[1])) return filter(List.of("confirm"), args[4]);
            if ("material-confirm".equalsIgnoreCase(args[1])) return filter(List.of("confirm"), args[4]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " warehouse";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.info", cmd));
        sender.sendMessage(fullMsg("help.view", cmd));
        sender.sendMessage(fullMsg("help.delete", cmd));
        sender.sendMessage(fullMsg("help.bank", cmd));
        sender.sendMessage(fullMsg("help.export", cmd));
        sender.sendMessage(fullMsg("help.material-transfers", cmd));
        sender.sendMessage(fullMsg("help.material-evidence", cmd));
        sender.sendMessage(fullMsg("help.material-confirm", cmd));
    }

    private void sendStatus(CommandSender sender) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.cached", svc.cachedPlayerCount()));
        sender.sendMessage(fullMsg("status.currency", String.join(", ", svc.currencyIds())));
        sender.sendMessage(fullMsg("status.cross-server",
            svc.crossServerActive() ? messages.get("status.cross-server-enabled") : messages.get("status.cross-server-disabled")));
        sender.sendMessage(fullMsg("status.shared-locks", svc.activeSharedEditLockCount()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.open.offline", args[2])); return; }
        WarehouseService.ActionResult result = svc.openMenu(target);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleInfo(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.info.usage")); return; }
        org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.player-not-found", args[2])); return; }
        UUID uuid = target.getUniqueId();
        String name = target.getName() == null ? args[2] : target.getName();
        String prefix = messages != null ? messages.get("prefix") : "";
        svc.describePlayer(uuid, name,
            lines -> lines.forEach(line -> sender.sendMessage(prefix + line)),
            error -> sender.sendMessage(prefix + ChatColor.RED + error)
        );
    }

    private void handleView(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.view.usage")); return; }
        org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.player-not-found", args[2])); return; }
        String warehouseId = args.length >= 4 ? args[3] : "";
        String prefix = messages != null ? messages.get("prefix") : "";
        // 玩家执行：直接以只读 UI 形式打开目标仓库（数据走数据库，目标离线也可查看）
        if (sender instanceof Player viewer) {
            WarehouseService.ActionResult result = svc.openPreview(viewer, target.getUniqueId(), warehouseId);
            sender.sendMessage(prefix + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            return;
        }
        // 控制台执行：退化为文本概览
        svc.describePersonalWarehouse(target.getUniqueId(), target.getName() == null ? args[2] : target.getName(),
            warehouseId.isBlank() ? svc.firstPersonalWarehouseId() : warehouseId,
            lines -> lines.forEach(line -> sender.sendMessage(prefix + line)),
            error -> sender.sendMessage(prefix + ChatColor.RED + error));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.delete.usage")); return; }
        org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.player-not-found", args[2])); return; }
        if (args.length < 5 || !"confirm".equalsIgnoreCase(args[4])) {
            sender.sendMessage(fullMsg("admin.delete.confirm", args[2], args[3]));
            return;
        }
        WarehouseService.ActionResult result = svc.adminDeletePersonalWarehouse(target.getUniqueId(), args[3]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private org.bukkit.OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    /**
     * 导出槽位物品原始序列化数据到 exports/warehouse 目录（损坏物品人工恢复用）。
     */
    private void handleExport(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 5) {
            sender.sendMessage(fullMsg("admin.export.usage"));
            return;
        }
        org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.player-not-found", args[2])); return; }
        int slot;
        try {
            slot = Integer.parseInt(args[4].trim());
        } catch (NumberFormatException exception) {
            sender.sendMessage(fullMsg("admin.export.invalid-slot", args[4]));
            return;
        }
        if (slot < 0) {
            sender.sendMessage(fullMsg("admin.export.invalid-slot", args[4]));
            return;
        }
        String prefix = messages != null ? messages.get("prefix") : "";
        svc.exportSlotItem(target.getUniqueId(), args[3], slot,
            text -> sender.sendMessage(prefix + ChatColor.GREEN + text),
            error -> sender.sendMessage(prefix + ChatColor.RED + error));
    }

    private void handleBank(CommandSender sender, String[] args) {
        WarehouseService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 6) {
            sender.sendMessage(fullMsg("admin.bank.usage"));
            return;
        }
        org.bukkit.OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("admin.player-not-found", args[2])); return; }
        UUID uuid = target.getUniqueId();
        svc.adminAdjustWallet(uuid, args[3], args[4], args[5], result ->
            sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message())
        );
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
