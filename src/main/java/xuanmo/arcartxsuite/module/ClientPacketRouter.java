package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.security.ClientPacketGuard;

/**
 * 客户端包路由器：管理客户端包处理器和初始化处理器的注册与路由。
 * <p>
 * 从 {@link ModuleRegistry} 拆分而来，职责单一（SRP）。
 * 注册变动时发布只读索引，网络入口可并发查询。
 */
final class ClientPacketRouter {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-PacketRouter", null);
    private static final String AUTH_PACKET_ID = "AXS_auth_v1";

    private final List<PrioritizedPacketHandler> packetHandlers = new ArrayList<>();
    private volatile Map<String, PacketRoute> routes = Map.of();
    /** 客户端初始化处理器注册表 */
    private final CopyOnWriteArrayList<PrioritizedInitializedHandler> initializedHandlers = new CopyOnWriteArrayList<>();
    private final ClientPacketGuard packetGuard;

    ClientPacketRouter(ClientPacketGuard packetGuard) {
        this.packetGuard = packetGuard;
    }

    synchronized void registerPacketHandler(String moduleId, PacketHandlerSpec spec) {
        if (spec.handler() == null || spec.packetIds().isEmpty()) return;
        packetHandlers.add(new PrioritizedPacketHandler(moduleId, spec));
        packetHandlers.sort(Comparator.comparingInt(handler -> handler.spec().priority()));
        rebuildRoutes();
    }

    void registerInitializedHandler(String moduleId, ClientInitializedHandler handler) {
        initializedHandlers.add(new PrioritizedInitializedHandler(moduleId, handler));
    }

    void removeInitializedHandlers(String moduleId) {
        initializedHandlers.removeIf(ih -> ih.moduleId().equals(moduleId));
    }

    synchronized void removePacketHandlers(String moduleId) {
        if (packetHandlers.removeIf(ph -> ph.moduleId().equals(moduleId))) rebuildRoutes();
    }

    private void rebuildRoutes() {
        Map<String, List<PrioritizedPacketHandler>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var handler : packetHandlers) {
            for (String id : handler.spec().packetIds()) {
                if (isAuthPacket(id) && !handler.moduleId().equals("loginview")) continue;
                groups.computeIfAbsent(id, ignored -> new ArrayList<>()).add(handler);
            }
        }
        Map<String, PacketRoute> next = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        groups.forEach((id, handlers) -> {
            var previous = routes.get(id);
            next.put(id, previous != null && sameHandlers(previous.handlers(), handlers)
                ? previous : new PacketRoute(new Object(), List.copyOf(handlers)));
        });
        routes = Collections.unmodifiableMap(next);
    }

    private static boolean sameHandlers(List<PrioritizedPacketHandler> previous, List<PrioritizedPacketHandler> current) {
        if (previous.size() != current.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            if (previous.get(i) != current.get(i)) return false;
        }
        return true;
    }

    Object routeToken(String packetId) {
        var route = routes.get(packetId);
        return route == null ? null : route.token();
    }

    static boolean isAuthPacket(String packetId) {
        return AUTH_PACKET_ID.equalsIgnoreCase(packetId);
    }

    /**
     * 路由客户端自定义包到已注册的处理器。
     * 按优先级遍历，第一个消费的处理器短路返回 true。
     *
     * @return true 表示包已被某个处理器消费
     */
    boolean routeClientPacket(Player player, String packetId, List<String> data) {
        return routeClientPacket(player, packetId, data, null, routes.get(packetId));
    }

    void routeQueuedPacket(Player player, String packetId, List<String> data, AuthSnapshot queuedSession,
                           Object token) {
        var route = routes.get(packetId);
        if (route != null && route.token() == token && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) {
            routeClientPacket(player, packetId, data, queuedSession, route);
        }
    }

    private boolean routeClientPacket(Player player, String packetId, List<String> data, AuthSnapshot queuedSession,
                                      PacketRoute route) {
        boolean authPacket = isAuthPacket(packetId);
        if (!authPacket && !admitted(player, queuedSession)) return true;
        if (route == null) return authPacket;

        String action = switch (packetId.toLowerCase(Locale.ROOT)) {
            case "axs_announcer_click" -> "click";
            case "axs_tooltip_data" -> "inspect";
            default -> data.isEmpty() ? "refresh" : data.get(0).trim().toLowerCase(Locale.ROOT);
        };
        for (PrioritizedPacketHandler handler : route.handlers()) {
            if (dispatch(handler, player, packetId, action, data)) return true;
        }
        return true;
    }

    private boolean admitted(Player player, AuthSnapshot queuedSession) {
        if (queuedSession == null) return AuthAccess.admitted(player.getUniqueId());
        AuthSnapshot current = AuthAccess.snapshot(player.getUniqueId());
        return current.admitted() && queuedSession.generation() == current.generation()
            && Objects.equals(queuedSession.connectionId(), current.connectionId());
    }

    private boolean dispatch(PrioritizedPacketHandler handler, Player player, String packetId, String action,
                             List<String> data) {
        try {
            if (packetGuard != null) {
                String guardModule = handler.spec().guardModule() == null ? handler.moduleId() : handler.spec().guardModule();
                if (!packetGuard.allow(player, guardModule, packetId, action, packetGuard.isDebugLogging())) {
                    return true;
                }
            }
            return handler.spec().handler().handleClientPacket(player, packetId, data);
        } catch (Exception exception) {
            LOGGER.warning("模块 " + handler.moduleId() + " 处理客户端包异常: " + exception.getMessage());
            return false;
        }
    }

    void routeClientInitialized(Player player) {
        for (PrioritizedInitializedHandler ih : initializedHandlers) {
            if (!AuthAccess.admitted(player.getUniqueId()) && !ih.moduleId().equals("loginview")) continue;
            try {
                ih.handler().onClientInitialized(player);
            } catch (Exception exception) {
                LOGGER.warning("模块 " + ih.moduleId() + " 客户端初始化处理器异常: " + exception.getMessage());
            }
        }
    }

    synchronized void clearAll() {
        packetHandlers.clear();
        routes = Map.of();
        initializedHandlers.clear();
    }

    /** 带优先级的客户端包处理器包装。 */
    record PrioritizedPacketHandler(String moduleId, PacketHandlerSpec spec) {}
    private record PacketRoute(Object token, List<PrioritizedPacketHandler> handlers) {}

    record PrioritizedInitializedHandler(String moduleId, ClientInitializedHandler handler) {
    }
}
