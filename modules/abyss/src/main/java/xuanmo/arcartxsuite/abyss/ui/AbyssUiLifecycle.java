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
        final Map<String, Update> queued = new LinkedHashMap<>();
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
        views.computeIfAbsent(id, key -> new HashMap<>()).put(ui, view);
        if (bridge == null || !bridge.openUiUnsafe(player, ui)) {
            closed(player, ui); return false;
        }
        defer.accept(() -> {
            if (!player.isOnline() || current(player, ui) != view) return;
            bridge.sendPacket(player, ui, handler, initialPayload);
            view.ready = true;
            for (Update update : List.copyOf(view.queued.values()))
                bridge.sendPacket(player, ui, update.handler(), update.payload());
            view.queued.clear();
        }, 2L);
        return true;
    }

    boolean send(Player player, String ui, String handler, Object payload) {
        View view = current(player, ui);
        if (view == null || bridge == null || !player.isOnline()) return false;
        if (!view.ready) {
            view.queued.put(handler, new Update(handler, payload));
            return true;
        }
        return bridge.sendPacket(player, ui, handler, payload);
    }

    void close(Player player, String ui) {
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
