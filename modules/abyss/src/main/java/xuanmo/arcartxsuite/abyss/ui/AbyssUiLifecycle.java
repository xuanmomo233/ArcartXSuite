package xuanmo.arcartxsuite.abyss.ui;

import java.util.*;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

/** Mount before initializing a client screen. Queue updates until its first packet is delivered. */
final class AbyssUiLifecycle {
    private record Update(String handler, Object payload) { }
    private static final class View {
        final String nonce = UUID.randomUUID().toString();
        boolean ready;
        boolean mountConfirmed;
        String initialHandler;
        Object initialPayload;
        final Map<String, Update> queued = new LinkedHashMap<>();
        final Map<String, Update> latest = new LinkedHashMap<>();
        boolean retryScheduled;
        int updateFailures;
    }
    private final PacketBridgeAPI bridge;
    private final BiConsumer<Runnable, Long> defer;
    private final Map<UUID, Map<String, View>> views = new HashMap<>();
    private final Map<UUID, String> foreground = new HashMap<>();

    AbyssUiLifecycle(PacketBridgeAPI bridge, BiConsumer<Runnable, Long> defer) {
        this.bridge = bridge; this.defer = defer;
    }

    boolean open(Player player, String ui, String handler, Object payload, boolean hud) {
        UUID id = player.getUniqueId();
        if (!hud) {
            String previous = foreground.get(id);
            if (previous != null && !previous.equals(ui)) close(player, previous);
            foreground.put(id, ui);
        }
        View view = new View();
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> initial = new LinkedHashMap<>();
            map.forEach((key, value) -> initial.put(String.valueOf(key), value));
            initial.put("view_nonce", view.nonce);
            payload = initial;
        }
        Object initialPayload = payload;
        view.initialHandler = handler;
        view.initialPayload = initialPayload;
        views.computeIfAbsent(id, key -> new HashMap<>()).put(ui, view);
        if (bridge == null || !bridge.openUiUnsafe(player, ui)) {
            closed(player, ui); return false;
        }
        // Older customised UIs may not send the mount handshake.
        defer.accept(() -> {
            if (!view.mountConfirmed) initialize(player, ui, handler, initialPayload, view, 1);
        }, 10L);
        return true;
    }

    boolean clientReady(Player player, String ui) {
        View view = current(player, ui);
        if (view == null || view.mountConfirmed || !player.isOnline()) return false;
        view.mountConfirmed = true;
        view.ready = false;
        view.queued.putAll(view.latest);
        defer.accept(() -> initialize(player, ui, view.initialHandler, view.initialPayload, view, 1), 2L);
        return true;
    }

    void closeForeground(Player player) {
        String ui = foreground.get(player.getUniqueId());
        if (ui != null) close(player, ui);
    }

    private void initialize(Player player, String ui, String handler, Object payload, View view, int attempt) {
        if (!player.isOnline() || current(player, ui) != view) return;
        if (!bridge.sendPacket(player, ui, handler, payload)) {
            if (attempt < 3) {
                defer.accept(() -> initialize(player, ui, handler, payload, view, attempt + 1), 2L);
            } else {
                close(player, ui);
                player.sendMessage("§c副本界面加载失败，请重新打开。");
            }
            return;
        }
        view.ready = true;
        flush(player,ui,view);
    }

    boolean send(Player player, String ui, String handler, Object payload) {
        View view = current(player, ui);
        if (view == null || bridge == null || !player.isOnline()) return false;
        Update update=new Update(handler,payload);
        view.latest.put(handler,update);view.queued.put(handler,update);
        return !view.ready || flush(player,ui,view);
    }

    private boolean flush(Player player,String ui,View view) {
        if(view.retryScheduled)return true;
        boolean failed=false;
        for(Update update:List.copyOf(view.queued.values())) {
            if(bridge.sendPacket(player,ui,update.handler(),update.payload()))view.queued.remove(update.handler(),update);
            else failed=true;
        }
        if(!failed){view.updateFailures=0;return true;}
        if(++view.updateFailures>=3) {
            close(player,ui);player.sendMessage("§c界面更新失败，请重新打开。");return false;
        }
        view.retryScheduled=true;
        defer.accept(()->{
            view.retryScheduled=false;
            if(player.isOnline() && current(player,ui)==view)flush(player,ui,view);
        },2L);
        return false;
    }

    void close(Player player, String ui) {
        if (current(player, ui) == null) return;
        closed(player, ui);
        if (bridge != null) bridge.closeUiUnsafe(player, ui);
    }

    void closed(Player player, String ui) {
        UUID id = player.getUniqueId();
        var playerViews = views.get(id);
        if (playerViews != null) {
            playerViews.remove(ui);
            if (playerViews.isEmpty()) views.remove(id);
        }
        foreground.remove(id, ui);
    }

    void closed(Player player, String ui, String nonce) {
        View view = current(player, ui);
        if (view != null && view.nonce.equals(nonce)) closed(player, ui);
    }

    boolean isOpen(Player player, String ui) { return current(player, ui) != null; }

    void clear(Player player) { views.remove(player.getUniqueId()); foreground.remove(player.getUniqueId()); }
    private View current(Player player, String ui) {
        var playerViews = views.get(player.getUniqueId());
        return playerViews != null ? playerViews.get(ui) : null;
    }
}
