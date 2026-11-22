package xuanmo.arcartxsuite.essentials.packet;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.essentials.service.EssentialsWork;

abstract class EssentialsPanel implements ClientPacketHandler {
    static final int PAGE_SIZE = 50;
    private final JavaPlugin plugin;
    private final PacketBridgeAPI bridge;
    private final String uiId;
    private final String packetId;
    private final String permission;
    private final String openAction;
    private final String initialPage;
    private final Set<String> pages;
    final EssentialsCommandDelegate commands;
    private final Map<UUID, View> views = new HashMap<>();
    private final EssentialsWork work;
    private volatile boolean closed;

    EssentialsPanel(JavaPlugin plugin, PacketBridgeAPI bridge, String uiId, String packetId,
                    String permission, String initialPage, Set<String> pages, EssentialsCommandDelegate commands, EssentialsWork work) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.uiId = uiId;
        this.packetId = packetId;
        this.permission = permission;
        this.openAction = permission.substring(permission.lastIndexOf('.') + 1);
        this.initialPage = initialPage;
        this.pages = pages;
        this.commands = commands;
        this.work = work;
    }

    public final void openMenu(Player player) {
        if(closed)return;
        if (!commands.actionEnabled(openAction) || !player.hasPermission(permission)) return;
        View view = new View();
        Page initial = new Page(initialPage, 0);
        view.requested = initial;
        UUID id = player.getUniqueId();
        views.put(id, view);
        if (!bridge.openUi(player, uiId)) {
            views.remove(id, view);
            return;
        }
        request(player, view, initial);
    }

    @Override
    public final boolean handleClientPacket(@NotNull Player player, @NotNull String id, @NotNull List<String> data) {
        if (!packetId.equalsIgnoreCase(id)) return false;
        if(closed)return true;
        View view = views.get(player.getUniqueId());
        if (view == null || !player.isOnline() || !commands.actionEnabled(openAction)
            || !player.hasPermission(permission) || !bridge.isUiOpen(player, uiId)) {
            return true;
        }
        String action = value(data, 0, "refresh").toLowerCase(Locale.ROOT);
        switch (action) {
            case "refresh", "navigate" -> {
                String page = value(data, 1, initialPage);
                if (pages.contains(page)) {
                    Page requested = new Page(page, 0);
                    if (canView(player, requested)) request(player, view, requested);
                    else player.sendMessage("§c你没有权限查看此页面。");
                }
            }
            case "filter_warps" -> {
                if (!view.requested.name().equals("warps") || !canView(player, view.requested)) return true;
                String search = value(data, 1, "").trim();
                if (search.length() > 64) return true;
                request(player, view, new Page("warps", 0, search, "favorites".equals(value(data, 2, ""))));
            }
            case "next", "previous" -> {
                Page page = view.requested;
                if (page != null && view.token != null && canView(player, page)) {
                    if (action.equals("previous") && page.index() > 0) {
                        request(player, view, page.withIndex(page.index() - 1));
                    } else if (action.equals("next") && view.hasNext && page.index() < Integer.MAX_VALUE / PAGE_SIZE - 1) {
                        request(player, view, page.withIndex(page.index() + 1));
                    }
                }
            }
            default -> {
                if (view.token == null || !view.token.equals(value(data, 1, ""))) return true;
                view.token = null;
                Page current = view.requested;
                if (!canView(player, current)) return true;
                execute(player, action, data);
                request(player, view, current);
            }
        }
        return true;
    }

    public final void clearPlayer(UUID id) {
        views.remove(id);
    }

    public final void shutdown() {
        closed=true;
        views.clear();
    }

    private void request(Player player, View view, Page page) {
        view.requested = page;
        view.token = null;
        if (!view.loading) load(player, view, page);
    }

    private void load(Player player, View view, Page page) {
        Map<String, Object> payload = new LinkedHashMap<>();
        CompletionStage<Map<String, Object>> query = snapshot(player, page, payload);
        if (query == null) {
            send(player, view, page, payload);
            return;
        }
        UUID id = player.getUniqueId();
        view.loading = true;
        query.whenComplete((data, failure) -> work.onMain(() -> {
            if (closed || views.get(id) != view) return;
            view.loading = false;
            Player online = Bukkit.getPlayer(id);
            if (online != player || !commands.actionEnabled(openAction) || !online.hasPermission(permission)
                    || !bridge.isUiOpen(online, uiId) || !canView(online, view.requested)) return;
            if (!page.equals(view.requested)) {
                load(online, view, view.requested);
                return;
            }
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Essentials 读取 " + page.name() + " 失败", failure);
                payload.put("loadError", "数据读取失败，请重新打开页面重试。");
            } else {
                payload.putAll(data);
            }
            send(online, view, page, payload);
        }));
    }

    private void send(Player player, View view, Page page, Map<String, Object> payload) {
        view.token = UUID.randomUUID().toString();
        view.hasNext = Boolean.TRUE.equals(payload.remove("hasNext"));
        payload.put("page", page.name());
        payload.put("packetId", packetId);
        payload.put("action_token", view.token);
        payload.put("pageNumber", String.valueOf(page.index() + 1));
        payload.put("hasPrevious", String.valueOf(page.index() > 0));
        payload.put("hasNext", String.valueOf(view.hasNext));
        if (payload.containsKey("loadError")) player.sendMessage("§c" + payload.get("loadError"));
        bridge.sendPacket(player, uiId, "init", payload);
    }

    abstract void execute(Player player, String action, List<String> data);

    abstract boolean canView(Player player, Page page);

    /** 主线程采集 Bukkit 状态，可返回异步查询补全页面数据。 */
    abstract CompletionStage<Map<String, Object>> snapshot(Player player, Page page, Map<String, Object> payload);

    final CompletionStage<Map<String, Object>> read(Callable<Map<String, Object>> query) {
        return work.read(query);
    }

    static String value(List<String> data, int index, String fallback) {
        if (index >= data.size() || data.get(index) == null || data.get(index).isBlank()) return fallback;
        return data.get(index);
    }

    static void pageItems(Map<String, Object> payload, String key, List<?> rows) {
        int count = Math.min(PAGE_SIZE, rows.size());
        payload.put(key, rows.subList(0, count));
        payload.put("hasNext", rows.size() > PAGE_SIZE);
    }

    static <T> List<T> pageSlice(List<T> items, Page page) {
        int start = Math.min(items.size(), page.offset());
        int count = Math.min(items.size() - start, PAGE_SIZE + 1);
        return items.subList(start, start + count);
    }

    boolean allowed(Player player, String action) {
        return commands.actionEnabled(action) && EssentialsCommandDelegate.hasActionPermission(player, action);
    }

    record Page(String name, int index, String query, boolean favoritesOnly) {
        Page(String name, int index) {
            this(name, index, "", false);
        }
        Page withIndex(int next) { return new Page(name, next, query, favoritesOnly); }
        int offset() { return index * PAGE_SIZE; }
    }

    private static final class View {
        private Page requested;
        private boolean loading;
        private boolean hasNext;
        private String token;
    }
}
