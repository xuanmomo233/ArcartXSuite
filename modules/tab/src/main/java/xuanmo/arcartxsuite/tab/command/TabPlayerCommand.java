package xuanmo.arcartxsuite.tab.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.tab.debug.TabSnapshotStore;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;
import xuanmo.arcartxsuite.tab.transport.TabRemoteEntry;

/**
 * Tab 玩家命令：切换视图 / 翻页 / 查看状态。
 *
 * <ul>
 *   <li>{@code /axstab view <name>} 切换 view。仅刷新本玩家。</li>
 *   <li>{@code /axstab page <definitionId> <next|prev|N>} 翻页。</li>
 *   <li>{@code /axstab refresh} 强制刷新本玩家。</li>
 *   </ul>
 */
public final class TabPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("view", "page", "refresh", "debug", "snapshot");
    private static final List<String> SNAPSHOT_ACTIONS = List.of("save", "load", "unload", "list", "delete");
    private static final String DEBUG_PERMISSION = "axstab.debug";

    private final Supplier<TabSyncService> serviceSupplier;
    private final Supplier<TabSnapshotStore> snapshotStoreSupplier;
    private final MessageProvider messages;

    public TabPlayerCommand(
        Supplier<TabSyncService> serviceSupplier,
        Supplier<TabSnapshotStore> snapshotStoreSupplier,
        MessageProvider messages
    ) {
        this.serviceSupplier = serviceSupplier;
        this.snapshotStoreSupplier = snapshotStoreSupplier;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        TabSyncService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return true;
        }
        // debug / snapshot 子命令允许控制台调用
        if (args.length > 0 && "debug".equalsIgnoreCase(args[0])) {
            handleDebug(service, sender, args);
            return true;
        }
        if (args.length > 0 && "snapshot".equalsIgnoreCase(args[0])) {
            handleSnapshot(service, sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "view" -> handleView(service, player, args);
            case "page" -> handlePage(service, player, args);
            case "refresh" -> {
                service.refreshViewer(player, "command");
                player.sendMessage(fullMsg("player.refresh.requested"));
            }
            default -> sendUsage(player, label);
        }
        return true;
    }

    /**
     * {@code /axstab debug <player> [definitionId]} 打印玩家在指定 definition 的排序 / 分组 / 状态快照。
     * 不带 definitionId 时输出全部 definition。
     */
    private void handleDebug(TabSyncService service, CommandSender sender, String[] args) {
        if (!sender.hasPermission(DEBUG_PERMISSION) && !sender.isOp()) {
            sender.sendMessage(fullMsg("common.no-permission", DEBUG_PERMISSION));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(fullMsg("admin.debug.usage"));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(fullMsg("admin.debug.offline", args[1]));
            return;
        }
        List<String> definitionIds = args.length >= 3
            ? List.of(args[2])
            : service.definitionIds();
        if (definitionIds.isEmpty()) {
            sender.sendMessage(fullMsg("admin.debug.empty"));
            return;
        }
        for (String defId : definitionIds) {
            sender.sendMessage(fullMsg("admin.debug.header", target.getName(), defId));
            service.debugSnapshot(target, defId).forEach((k, v) ->
                sender.sendMessage((messages != null ? messages.get("prefix") : "") + ChatColor.GRAY + k + ": " + ChatColor.WHITE + v));
        }
    }

    /**
     * {@code /axstab snapshot <save|load|unload|list|delete> [name]}：调试快照管理。
     *
     * <ul>
     *   <li>{@code save <name>}：把当前在线玩家 + 跨服快照落盘到 {@code data/tab/snapshots/<name>.json}</li>
     *   <li>{@code load <name>}：把存档注入为 {@code snapshot:<name>:<原 nodeId>} 虚拟节点，本服 viewer 立即可见</li>
     *   <li>{@code unload <name>}：卸载该 name 对应的全部虚拟节点</li>
     *   <li>{@code list}：列出当前已保存的快照名 + 已注入的虚拟节点</li>
     *   <li>{@code delete <name>}：删除存档文件（不影响已注入的虚拟节点）</li>
     * </ul>
     */
    private void handleSnapshot(TabSyncService service, CommandSender sender, String[] args) {
        if (!sender.hasPermission(DEBUG_PERMISSION) && !sender.isOp()) {
            sender.sendMessage(fullMsg("common.no-permission", DEBUG_PERMISSION));
            return;
        }
        TabSnapshotStore store = snapshotStoreSupplier == null ? null : snapshotStoreSupplier.get();
        if (store == null) {
            sender.sendMessage(fullMsg("admin.snapshot.not-initialized"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(fullMsg("admin.snapshot.usage"));
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "list" -> snapshotList(service, store, sender);
            case "save" -> snapshotSave(service, store, sender, args);
            case "load" -> snapshotLoad(service, store, sender, args);
            case "unload" -> snapshotUnload(service, sender, args);
            case "delete" -> snapshotDelete(store, sender, args);
            default -> sender.sendMessage(fullMsg("admin.snapshot.unknown", op));
        }
    }

    private void snapshotList(TabSyncService service, TabSnapshotStore store, CommandSender sender) {
        try {
            List<String> names = store.list();
            sender.sendMessage(fullMsg("admin.snapshot.list.saved", names.size()));
            if (names.isEmpty()) {
                sender.sendMessage(fullMsg("admin.snapshot.list.none"));
            } else {
                for (String n : names) {
                    sender.sendMessage(fullMsg("admin.snapshot.list.item", n));
                }
            }
        } catch (java.io.IOException ex) {
            sender.sendMessage(fullMsg("admin.snapshot.list.failed", ex.getMessage()));
            return;
        }
        List<String> installed = service.installedSnapshotNodeIds();
        sender.sendMessage(fullMsg("admin.snapshot.list.installed", installed.size()));
        if (installed.isEmpty()) {
            sender.sendMessage(fullMsg("admin.snapshot.list.none"));
        } else {
            for (String id : installed) {
                sender.sendMessage(fullMsg("admin.snapshot.list.item", id));
            }
        }
    }

    private void snapshotSave(TabSyncService service, TabSnapshotStore store, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snapshot.save.usage"));
            return;
        }
        String name = args[2];
        if (!TabSnapshotStore.isValidName(name)) {
            sender.sendMessage(fullMsg("admin.snapshot.save.invalid-name", name));
            return;
        }
        try {
            java.nio.file.Path path = store.save(
                name,
                service.serverId(),
                service.snapshotLocalEntries(),
                service.snapshotRemoteEntries()
            );
            sender.sendMessage(fullMsg("admin.snapshot.save.success", path));
        } catch (java.io.IOException ex) {
            sender.sendMessage(fullMsg("admin.snapshot.save.failed", ex.getMessage()));
        }
    }

    private void snapshotLoad(TabSyncService service, TabSnapshotStore store, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snapshot.load.usage"));
            return;
        }
        String name = args[2];
        if (!TabSnapshotStore.isValidName(name)) {
            sender.sendMessage(fullMsg("admin.snapshot.load.invalid-name", name));
            return;
        }
        TabSnapshotStore.Loaded loaded;
        try {
            loaded = store.load(name);
        } catch (java.io.IOException ex) {
            sender.sendMessage(fullMsg("admin.snapshot.load.failed", ex.getMessage()));
            return;
        }
        // 本服快照注入为 snapshot:<name>:local
        if (!loaded.localEntries().isEmpty()) {
            service.installSnapshotPayload("snapshot:" + name + ":local", loaded.localEntries());
        }
        // 远程节点逐个注入为 snapshot:<name>:<原 nodeId>
        for (java.util.Map.Entry<String, java.util.Map<String, List<TabRemoteEntry>>> e : loaded.remoteSnapshots().entrySet()) {
            service.installSnapshotPayload("snapshot:" + name + ":" + e.getKey(), e.getValue());
        }
        sender.sendMessage(fullMsg("admin.snapshot.load.success", name, loaded.localEntries().size(), loaded.remoteSnapshots().size(), loaded.savedAt()));
    }

    private void snapshotUnload(TabSyncService service, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snapshot.unload.usage"));
            return;
        }
        String name = args[2];
        int removed = 0;
        if ("all".equalsIgnoreCase(name)) {
            for (String id : new ArrayList<>(service.installedSnapshotNodeIds())) {
                if (service.uninstallSnapshotPayload(id)) {
                    removed++;
                }
            }
        } else {
            String prefix = "snapshot:" + name + ":";
            for (String id : new ArrayList<>(service.installedSnapshotNodeIds())) {
                if (id.startsWith(prefix) && service.uninstallSnapshotPayload(id)) {
                    removed++;
                }
            }
        }
        sender.sendMessage(fullMsg("admin.snapshot.unload.success", removed));
    }

    private void snapshotDelete(TabSnapshotStore store, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snapshot.delete.usage"));
            return;
        }
        String name = args[2];
        if (!TabSnapshotStore.isValidName(name)) {
            sender.sendMessage(fullMsg("admin.snapshot.delete.invalid-name", name));
            return;
        }
        try {
            boolean removed = store.delete(name);
            sender.sendMessage(removed ? fullMsg("admin.snapshot.delete.success", name) : fullMsg("admin.snapshot.delete.not-found", name));
        } catch (java.io.IOException ex) {
            sender.sendMessage(fullMsg("admin.snapshot.delete.failed", ex.getMessage()));
        }
    }

    private void handleView(TabSyncService service, Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(fullMsg("player.view.current", service.currentView(player)));
            player.sendMessage(fullMsg("player.view.usage"));
            return;
        }
        String view = args[1].trim();
        if (service.setViewerView(player, view)) {
            player.sendMessage(fullMsg("player.view.success", view));
        } else {
            player.sendMessage(fullMsg("player.view.no-change"));
        }
    }

    private void handlePage(TabSyncService service, Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(fullMsg("player.page.usage"));
            return;
        }
        String defId = args[1].trim();
        String pageArg = args[2].trim();
        int target;
        if ("next".equalsIgnoreCase(pageArg)) {
            target = service.currentPage(player, defId) + 1;
        } else if ("prev".equalsIgnoreCase(pageArg)) {
            target = Math.max(0, service.currentPage(player, defId) - 1);
        } else {
            try {
                target = Math.max(0, Integer.parseInt(pageArg));
            } catch (NumberFormatException ex) {
                player.sendMessage(fullMsg("player.page.invalid-page", pageArg));
                return;
            }
        }
        if (service.setViewerPage(player, defId, target)) {
            player.sendMessage(fullMsg("player.page.success", target));
        } else {
            player.sendMessage(fullMsg("player.page.failed"));
        }
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(fullMsg("player.usage.title"));
        player.sendMessage(fullMsg("player.usage.view", label));
        player.sendMessage(fullMsg("player.usage.page", label));
        player.sendMessage(fullMsg("player.usage.refresh", label));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT_ACTIONS, args[0]);
        }
        if (args.length == 3 && "page".equalsIgnoreCase(args[0])) {
            return filter(List.of("next", "prev", "0", "1", "2"), args[2]);
        }
        if (args.length == 2 && "snapshot".equalsIgnoreCase(args[0])) {
            return filter(SNAPSHOT_ACTIONS, args[1]);
        }
        if (args.length == 3 && "snapshot".equalsIgnoreCase(args[0])) {
            String op = args[1].toLowerCase(Locale.ROOT);
            if ("load".equals(op) || "delete".equals(op)) {
                TabSnapshotStore store = snapshotStoreSupplier == null ? null : snapshotStoreSupplier.get();
                if (store != null) {
                    try {
                        return filter(store.list(), args[2]);
                    } catch (java.io.IOException ignored) {
                        return List.of();
                    }
                }
            }
            if ("unload".equals(op)) {
                TabSyncService service = serviceSupplier.get();
                List<String> opts = new ArrayList<>();
                opts.add("all");
                if (service != null) {
                    for (String id : service.installedSnapshotNodeIds()) {
                        // snapshot:<name>:<...>，提取 <name>
                        String[] parts = id.split(":", 3);
                        if (parts.length >= 2 && !opts.contains(parts[1])) {
                            opts.add(parts[1]);
                        }
                    }
                }
                return filter(opts, args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
