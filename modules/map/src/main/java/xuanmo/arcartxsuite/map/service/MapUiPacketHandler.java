package xuanmo.arcartxsuite.map.service;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;

/**
 * 地图 UI 数据包处理器，解析客户端发来的操作指令并分发到 {@code ActionTarget}。
 * <p>
 * 关联模块：map。将客户端 UI 事件（如选中锚点、解锁、传送、追踪、创建 / 删除路径点等）
 * 转换为对 {@code MapService} 的方法调用，并对写操作执行 action token 防重放校验。
 */
public final class MapUiPacketHandler {

    private final ActionTarget target;
    private final String packetId;

    /**
     * 构造 UI 数据包处理器。
     *
     * @param target   动作执行目标（通常为 {@code MapService}）
     * @param packetId 数据包标识，用于过滤匹配的数据包
     */
    public MapUiPacketHandler(ActionTarget target, String packetId) {
        this.target = target;
        this.packetId = packetId;
    }

    /**
     * 处理客户端发来的地图数据包，解析动作类型并分发到对应处理方法。
     *
     * @param player   发送数据包的玩家
     * @param packetId 数据包标识
     * @param data     数据包内容列表
     * @return {@code true} 表示数据包标识匹配且已处理
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !this.packetId.equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        boolean tokenized = switch (action) {
            case "unlock_anchor", "teleport_anchor", "create_waypoint", "delete_waypoint" -> true;
            default -> false;
        };
        if (tokenized && !target.beginActionToken(player, action, data)) return true;
        boolean committed = false;
        switch (action) {
            case "refresh" -> target.refresh(player);
            case "open_world" -> {
                if (hasValue(data, 1)) {
                    target.openWorld(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_anchor" -> {
                if (hasValue(data, 1)) {
                    target.selectAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.selectWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_external" -> {
                if (hasValue(data, 1)) {
                    target.selectExternalTarget(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "unlock_anchor" -> {
                if (hasValue(data, 1)) {
                    committed = target.unlockAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "teleport_anchor" -> {
                if (hasValue(data, 1)) {
                    committed = target.teleportAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_anchor" -> {
                if (hasValue(data, 1)) {
                    target.trackAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.trackWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_external" -> {
                if (hasValue(data, 1)) {
                    target.trackExternalTarget(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "teleport_to_world" -> {
                if (hasValue(data, 1)) {
                    target.teleportToWorld(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "cross_world_navigate" -> {
                if (hasValue(data, 1)) {
                    target.crossWorldNavigate(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "clear_track" -> target.clearTrack(player);
            case "create_waypoint" -> committed = target.createWaypoint(player);
            case "delete_waypoint" -> {
                if (hasValue(data, 1)) {
                    committed = target.deleteWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "open_menu" -> target.openMenu(player, hasValue(data, 1) ? data.get(1) : "");
            default -> target.refresh(player);
        }
        if (tokenized) target.finishActionToken(player, committed);
        return true;
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 动作执行目标接口，定义数据包处理器可调用的全部操作。
     * <p>
     * 由 {@code MapService} 实现，将 UI 事件映射到具体的业务逻辑。
     */
    public interface ActionTarget {
        boolean allowClientPacket(Player player, String action);

        void refresh(Player player);

        void openMenu(Player player, String worldId);

        void openWorld(Player player, String worldId);

        void selectAnchor(Player player, String anchorId);

        void selectWaypoint(Player player, String waypointId);

        void selectExternalTarget(Player player, String targetId);

        boolean unlockAnchor(Player player, String anchorId);

        boolean teleportAnchor(Player player, String anchorId);

        void trackAnchor(Player player, String anchorId);

        void trackWaypoint(Player player, String waypointId);

        void trackExternalTarget(Player player, String targetId);

        void teleportToWorld(Player player, String worldId);

        void crossWorldNavigate(Player player, String targetDesc);

        void clearTrack(Player player);

        boolean createWaypoint(Player player);

        boolean deleteWaypoint(Player player, String waypointId);

        boolean beginActionToken(Player player, String action, List<String> data);

        void finishActionToken(Player player, boolean committed);
    }
}
