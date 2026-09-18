package xuanmo.arcartxsuite.tab.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.tab.debug.TabSnapshotStore;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;
import xuanmo.arcartxsuite.tab.transport.TabRemoteEntry;

/**
 * Tab 管理命令处理器，通过宿主 {@link ModuleCommandHandler} 机制挂载到 {@code /axs tab}。
 *
 * <ul>
 *   <li>{@code /axs tab view <name>} / {@code view <player> <name>} 切换自己/指定玩家视图。</li>
 *   <li>{@code /axs tab page <defId> <arg>} / {@code page <player> <defId> <arg>} 翻页。</li>
 *   <li>{@code /axs tab refresh [player]} 刷新（无参 = 全局刷新）。</li>
 *   <li>{@code /axs tab fake <数量|off> [player]} fake 测试模式。</li>
 *   <li>{@code /axs tab debug <player> [definitionId]} 打印玩家排序 / 分组 / 状态快照。</li>
 *   <li>{@code /axs tab snapshot <save|load|unload|list|delete> [name]} 调试快照管理。</li>
 * </ul>
 */
public final class TabAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "view", "page", "refresh", "debug", "snapshot", "fake");
    private static final List<String> SNAPSHOT_ACTIONS = List.of("save", "load", "unload", "list", "delete");
    private static final List<String> FAKE_SUGGESTIONS = List.of("off", "1", "5", "15", "30", "45", "60", "90", "120");
    private static final List<String> PAGE_SUGGESTIONS = List.of("next", "prev", "0", "1", "2");
    private static final String DEBUG_PERMISSION = "axs.tab.debug";
    private static final int MAX_FAKE_COUNT = 500;

    private final Supplier<TabSyncService> serviceSupplier;
    private final Supplier<TabSnapshotStore> snapshotStoreSupplier;
    private final MessageProvider messages;

    public TabAdminCommand(
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

    @Override public String commandId() { return "tab"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        TabSyncService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(service, sender);
            case "view" -> handleView(service, sender, args);
            case "page" -> handlePage(service, sender, args);
            case "refresh" -> handleRefresh(service, sender, args);
            case "debug" -> handleDebug(service, sender, args);
            case "snapshot" -> handleSnapshot(service, sender, args);
            case "fake" -> handleFake(service, sender, args);
            default -> sender.sendMessage(fullMsg("admin.unknown-action", "/" + label + " tab"));
        }
        return true;
    }

    // ─── status / help ──────────────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " tab";
        sender.sendMessage(fullMsg("admin.help.title"));
        sender.sendMessage(fullMsg("admin.help.status", cmd));
        sender.sendMessage(fullMsg("admin.help.view", cmd));
        sender.sendMessage(fullMsg("admin.help.page", cmd));
        sender.sendMessage(fullMsg("admin.help.refresh", cmd));
        sender.sendMessage(fullMsg("admin.help.fake", cmd));
        sender.sendMessage(fullMsg("admin.help.debug", cmd));
        sender.sendMessage(fullMsg("admin.help.snapshot", cmd));
    }

    private void sendStatus(TabSyncService service, CommandSender sender) {
        sender.sendMessage(fullMsg("admin.status.title"));
        sender.sendMessage(fullMsg("admin.status.server-id", service.serverId()));
        sender.sendMessage(fullMsg("admin.status.definitions", service.definitionIds().size()));
        sender.sendMessage(fullMsg("admin.status.snapshots", service.installedSnapshotNodeIds().size()));
    }

    // ─── view / page / refresh（自己或指定玩家） ─────────────

    /**
     * {@code /axs tab view <name>} 切换自己视图；
     * {@code /axs tab view <player> <name>} 强制切换指定玩家视图。
     */
    private void handleView(TabSyncService service, CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.view.usage"));
            return;
        }
        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(fullMsg("common.only-player"));
                return;
            }
            applyView(service, sender, player, args[2].trim());
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(fullMsg("admin.debug.offline", args[2]));
            return;
        }
        applyView(service, sender, target, args[3].trim());
    }

    private void applyView(TabSyncService service, CommandSender sender, Player target, String view) {
        if (service.setViewerView(target, view)) {
            sender.sendMessage(fullMsg("admin.view.success", target.getName(), view));
        } else {
            sender.sendMessage(fullMsg("admin.view.no-change", target.getName()));
        }
    }

    /**
     * {@code /axs tab page <defId> <next|prev|N>} 给自己翻页；
     * {@code /axs tab page <player> <defId> <next|prev|N>} 给指定玩家翻页。
     */
    private void handlePage(TabSyncService service, CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.page.usage"));
            return;
        }
        Player target;
        String defId;
        String pageArg;
        if (args.length >= 5) {
            target = org.bukkit.Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(fullMsg("admin.debug.offline", args[2]));
                return;
            }
            defId = args[3].trim();
            pageArg = args[4].trim();
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(fullMsg("common.only-player"));
                return;
            }
            target = player;
            defId = args[2].trim();
            pageArg = args[3].trim();
        }
        applyPage(service, sender, target, defId, pageArg);
    }

    private void applyPage(TabSyncService service, CommandSender sender, Player target, String defId, String pageArg) {
        int targetPage;
        if ("next".equalsIgnoreCase(pageArg)) {
            targetPage = service.currentPage(target, defId) + 1;
        } else if ("prev".equalsIgnoreCase(pageArg)) {
            targetPage = Math.max(0, service.currentPage(target, defId) - 1);
        } else {
            try {
                targetPage = Math.max(0, Integer.parseInt(pageArg));
            } catch (NumberFormatException ex) {
                sender.sendMessage(fullMsg("player.page.invalid-page", pageArg));
                return;
            }
        }
        if (service.setViewerPage(target, defId, targetPage)) {
            sender.sendMessage(fullMsg("admin.page.success", target.getName(), targetPage));
        } else {
            sender.sendMessage(fullMsg("player.page.failed"));
        }
    }

    /**
     * {@code /axs tab refresh} 全局刷新；{@code /axs tab refresh <player>} 刷新指定玩家。
     */
    private void handleRefresh(TabSyncService service, CommandSender sender, String[] args) {
        if (args.length < 3) {
            service.requestGlobalRefresh("command");
            sender.sendMessage(fullMsg("admin.refresh.global"));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(fullMsg("admin.debug.offline", args[2]));
            return;
        }
        service.refreshViewer(target, "command");
        sender.sendMessage(fullMsg("admin.refresh.target", target.getName()));
    }

    // ─── debug / snapshot / fake ─────────────────────────────

    private boolean hasDebugPermission(CommandSender sender) {
        return sender.hasPermission(DEBUG_PERMISSION) || sender.isOp();
    }

    /**
     * {@code /axs tab debug <player> [definitionId]} 打印玩家在指定 definition 的排序 / 分组 / 状态快照。
     * 不带 definitionId 时输出全部 definition。
     */
    private void handleDebug(TabSyncService service, CommandSender sender, String[] args) {
        if (!hasDebugPermission(sender)) {
            sender.sendMessage(fullMsg("common.no-permission", DEBUG_PERMISSION));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.debug.usage"));
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(fullMsg("admin.debug.offline", args[2]));
            return;
        }
        List<String> definitionIds = args.length >= 4
            ? List.of(args[3])
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
     * {@code /axs tab snapshot <save|load|unload|list|delete> [name]}：调试快照管理。
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
        if (!hasDebugPermission(sender)) {
            sender.sendMessage(fullMsg("common.no-permission", DEBUG_PERMISSION));
            return;
        }
        TabSnapshotStore store = snapshotStoreSupplier == null ? null : snapshotStoreSupplier.get();
        if (store == null) {
            sender.sendMessage(fullMsg("admin.snapshot.not-initialized"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snapshot.usage"));
            return;
        }
        String op = args[2].toLowerCase(Locale.ROOT);
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
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.snapshot.save.usage"));
            return;
        }
        String name = args[3];
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
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.snapshot.load.usage"));
            return;
        }
        String name = args[3];
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
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.snapshot.unload.usage"));
            return;
        }
        String name = args[3];
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
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.snapshot.delete.usage"));
            return;
        }
        String name = args[3];
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

    /**
     * {@code /axs tab fake <数量|off>} 给自己开启 fake 测试模式；
     * {@code /axs tab fake <player> <数量|off>} 给指定玩家开启。
     * 开启后服务端向目标玩家推送 count 条伪造条目（按 pack 渲染本人 + 序号后缀），
     * 用于观察 UI 在不同在线人数下的列数 / 背景 / 信息栏布局，仅影响该玩家。
     */
    private void handleFake(TabSyncService service, CommandSender sender, String[] args) {
        if (!hasDebugPermission(sender)) {
            sender.sendMessage(fullMsg("common.no-permission", DEBUG_PERMISSION));
            return;
        }
        if (args.length < 3) {
            if (sender instanceof Player player) {
                Integer current = service.fakeEntryCount(player);
                sender.sendMessage(current == null
                    ? fullMsg("admin.fake.status-off")
                    : fullMsg("admin.fake.status-on", current));
            }
            sender.sendMessage(fullMsg("admin.fake.usage", MAX_FAKE_COUNT));
            return;
        }
        Player target;
        String arg;
        if (args.length >= 4) {
            target = org.bukkit.Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(fullMsg("admin.debug.offline", args[2]));
                return;
            }
            arg = args[3].trim();
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(fullMsg("common.only-player"));
                return;
            }
            target = player;
            arg = args[2].trim();
        }
        if ("off".equalsIgnoreCase(arg) || "stop".equalsIgnoreCase(arg) || "false".equalsIgnoreCase(arg)) {
            if (service.fakeEntryCount(target) == null) {
                sender.sendMessage(fullMsg("admin.fake.not-active"));
                return;
            }
            service.setFakeEntryCount(target, -1);
            sender.sendMessage(fullMsg("admin.fake.disabled", target.getName()));
            return;
        }
        int count;
        try {
            count = Integer.parseInt(arg);
        } catch (NumberFormatException ex) {
            sender.sendMessage(fullMsg("admin.fake.invalid", arg, MAX_FAKE_COUNT));
            return;
        }
        if (count < 0 || count > MAX_FAKE_COUNT) {
            sender.sendMessage(fullMsg("admin.fake.invalid", arg, MAX_FAKE_COUNT));
            return;
        }
        if (!service.setFakeEntryCount(target, count)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return;
        }
        sender.sendMessage(fullMsg("admin.fake.enabled", count, target.getName()));
    }

    // ─── Tab 补全 ────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        TabSyncService service = serviceSupplier.get();
        if (args.length == 3) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "snapshot" -> { return filter(SNAPSHOT_ACTIONS, args[2]); }
                case "fake" -> {
                    List<String> opts = new ArrayList<>(FAKE_SUGGESTIONS);
                    org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    return filter(opts, args[2]);
                }
                case "page" -> {
                    List<String> opts = new ArrayList<>();
                    if (service != null) opts.addAll(service.definitionIds());
                    org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    return filter(opts, args[2]);
                }
                case "view", "refresh", "debug" -> { return null; } // 玩家名
                default -> { return List.of(); }
            }
        }
        if (args.length == 4) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "snapshot" -> {
                    String op = args[2].toLowerCase(Locale.ROOT);
                    if ("load".equals(op) || "delete".equals(op)) {
                        TabSnapshotStore store = snapshotStoreSupplier == null ? null : snapshotStoreSupplier.get();
                        if (store != null) {
                            try {
                                return filter(store.list(), args[3]);
                            } catch (java.io.IOException ignored) {
                                return List.of();
                            }
                        }
                    }
                    if ("unload".equals(op)) {
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
                        return filter(opts, args[3]);
                    }
                    return List.of();
                }
                case "fake" -> { return filter(FAKE_SUGGESTIONS, args[3]); }
                case "page" -> {
                    // 自己形态 page <defId> <arg> 或指定形态 page <player> <defId>
                    List<String> opts = new ArrayList<>(PAGE_SUGGESTIONS);
                    if (service != null) opts.addAll(service.definitionIds());
                    return filter(opts, args[3]);
                }
                default -> { return List.of(); }
            }
        }
        if (args.length == 5 && "page".equalsIgnoreCase(args[1])) {
            return filter(PAGE_SUGGESTIONS, args[4]);
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
