package xuanmo.arcartxsuite.module;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.security.ClientPacketGuard;

/**
 * 客户端包路由器：管理客户端包处理器和初始化处理器的注册与路由。
 * <p>
 * 从 {@link ModuleRegistry} 拆分而来，职责单一（SRP）。
 * 线程安全：使用 {@link CopyOnWriteArrayList} 保证并发读写安全。
 */
final class ClientPacketRouter {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-PacketRouter", null);

    /** 客户端包处理器注册表（按优先级排序） */
    private final CopyOnWriteArrayList<PrioritizedPacketHandler> packetHandlers = new CopyOnWriteArrayList<>();
    /** 客户端初始化处理器注册表 */
    private final CopyOnWriteArrayList<PrioritizedInitializedHandler> initializedHandlers = new CopyOnWriteArrayList<>();
    private final ClientPacketGuard packetGuard;

    ClientPacketRouter(ClientPacketGuard packetGuard) {
        this.packetGuard = packetGuard;
    }

    void registerPacketHandler(String moduleId, ClientPacketHandler handler, int priority) {
        registerPacketHandler(moduleId, handler, priority, null, null);
    }

    void registerPacketHandler(
        String moduleId,
        ClientPacketHandler handler,
        int priority,
        String packetId,
        String guardModule
    ) {
        packetHandlers.add(new PrioritizedPacketHandler(moduleId, handler, priority, packetId, guardModule));
        packetHandlers.sort(Comparator.comparingInt(PrioritizedPacketHandler::priority));
    }

    void registerInitializedHandler(String moduleId, ClientInitializedHandler handler) {
        initializedHandlers.add(new PrioritizedInitializedHandler(moduleId, handler));
    }

    void removeInitializedHandlers(String moduleId) {
        initializedHandlers.removeIf(ih -> ih.moduleId().equals(moduleId));
    }

    void removePacketHandlers(String moduleId) {
        packetHandlers.removeIf(ph -> ph.moduleId().equals(moduleId));
    }

    /**
     * 路由客户端自定义包到已注册的处理器。
     * 按优先级遍历，第一个消费的处理器短路返回 true。
     *
     * @return true 表示包已被某个处理器消费
     */
    boolean routeClientPacket(Player player, String packetId, List<String> data) {
        String action = data == null || data.isEmpty() || data.get(0) == null
            ? "refresh"
            : data.get(0).trim().toLowerCase(Locale.ROOT);
        for (PrioritizedPacketHandler ph : packetHandlers) {
            try {
                if (packetGuard != null
                    && (ph.packetId() == null || ph.packetId().equalsIgnoreCase(packetId))) {
                    String guardModule = ph.guardModule() == null ? ph.moduleId() : ph.guardModule();
                    if (!packetGuard.allow(player, guardModule, action, false)) {
                        return true;
                    }
                }
                if (ph.handler().handleClientPacket(player, packetId, data)) {
                    return true;
                }
            } catch (Exception exception) {
                LOGGER.warning("模块 " + ph.moduleId() + " 处理客户端包异常: " + exception.getMessage());
            }
        }
        return false;
    }

    void routeClientInitialized(Player player) {
        for (PrioritizedInitializedHandler ih : initializedHandlers) {
            try {
                ih.handler().onClientInitialized(player);
            } catch (Exception exception) {
                LOGGER.warning("模块 " + ih.moduleId() + " 客户端初始化处理器异常: " + exception.getMessage());
            }
        }
    }

    void clearAll() {
        packetHandlers.clear();
        initializedHandlers.clear();
    }

    /** 带优先级的客户端包处理器包装。 */
    record PrioritizedPacketHandler(
        String moduleId,
        ClientPacketHandler handler,
        int priority,
        String packetId,
        String guardModule
    ) {
    }

    record PrioritizedInitializedHandler(String moduleId, ClientInitializedHandler handler) {
    }
}
