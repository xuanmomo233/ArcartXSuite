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
        // arcartxsuite.map.use 全入口门控：数据包路径与 /map 命令、全局按键
        // 同一权限。无权限静默吞掉（不回包不提示，防伪造包探测/刷消息）。
        if (!player.hasPermission("arcartxsuite.map.use")) {
            return true;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        boolean tokenized = switch (action) {
            case "unlock_anchor", "teleport_anchor", "create_waypoint", "delete_waypoint",
                 "update_waypoint", "create_pin", "delete_pin", "update_pin",
                 "cross_world_navigate", "teleport_to_world" -> true;
            default -> false;
        };
        if (tokenized && !target.beginActionToken(player, action, data)) return true;
        try {
            if (target.dispatchAsyncAction(player, action, data, tokenized)) return true;
        } catch (RuntimeException | Error failure) {
            if (tokenized) target.finishActionToken(player, false);
            throw failure;
        }
        boolean committed = false;
        try {
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
            case "select_pin" -> {
                if (hasValue(data, 1)) {
                    target.selectPin(player, data.get(1));
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
            case "track_pin" -> {
                if (hasValue(data, 1)) {
                    target.trackPin(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_external", "external_navigate" -> {
                // 外部目标"导航"：转发给来源模块/插件执行，地图不自建信标
                if (hasValue(data, 1)) {
                    target.trackExternalTarget(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "external_nav_cancel" -> {
                // 外部目标"取消"：转发给来源停止自身导航，目标标记保留
                if (hasValue(data, 1)) {
                    target.externalCancel(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "teleport_to_world" -> {
                if (hasValue(data, 1)) {
                    committed = target.teleportToWorld(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "cross_world_navigate" -> {
                if (hasValue(data, 1)) {
                    committed = target.crossWorldNavigate(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "untrack" -> {
                // 单目标取消：untrack <type> <targetId>
                if (hasValue(data, 1) && hasValue(data, 2)) {
                    target.untrackTarget(player, data.get(1), data.get(2));
                } else {
                    target.refresh(player);
                }
            }
            case "clear_track" -> target.clearTrack(player);
            case "hud_ready" -> target.hudReady(player);
            case "deselect" -> target.deselect(player);


            case "share_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.shareWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "share_pin" -> {
                if (hasValue(data, 1)) {
                    target.sharePin(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            // 聊天卡片点击领取分享点位：不经 actionToken（点击者未必开着地图菜单，
            // token 是空的）；分享码本身即凭据，服务端校验存在性+有效期+数量上限
            case "claim_share" -> {
                if (hasValue(data, 1)) {
                    target.claimShare(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "open_menu" -> target.openMenu(player, hasValue(data, 1) ? data.get(1) : "");
            default -> target.refresh(player);
        }
        } finally {
            // in-flight 槽位必须无条件释放：分发过程抛异常（如玩家恰好在
            // sendPacket 瞬间掉线）否则会把玩家永久锁死在"动作进行中"
            if (tokenized) {
                target.finishActionToken(player, committed);
            }
        }
        return true;
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(safe(value));
        } catch (NumberFormatException e) {
            // 返回 NaN：调用方用 Double.isFinite 判定，解析失败按非法输入丢弃，
            // 不能返回 0——否则 "abc" 会在坐标 0,0 建出 pin
            return Double.NaN;
        }
    }

    /**
     * 动作执行目标接口，定义数据包处理器可调用的全部操作。
     * <p>
     * 由 {@code MapService} 实现，将 UI 事件映射到具体的业务逻辑。
     */
    public interface ActionTarget {
        default boolean dispatchAsyncAction(Player player, String action, List<String> data, boolean tokenized) { return false; }
        boolean allowClientPacket(Player player, String action);

        void refresh(Player player);

        void openMenu(Player player, String worldId);

        void openWorld(Player player, String worldId);

        void selectAnchor(Player player, String anchorId);

        void selectWaypoint(Player player, String waypointId);

        void selectExternalTarget(Player player, String targetId);

        void selectPin(Player player, String pinId);


        boolean teleportAnchor(Player player, String anchorId);

        void trackAnchor(Player player, String anchorId);

        void trackWaypoint(Player player, String waypointId);

        void trackPin(Player player, String pinId);

        void trackExternalTarget(Player player, String targetId);

        void externalCancel(Player player, String targetId);

        void untrackTarget(Player player, String type, String targetId);

        boolean teleportToWorld(Player player, String worldId);

        boolean crossWorldNavigate(Player player, String targetDesc);

        void deselect(Player player);

        void clearTrack(Player player);

        void hudReady(Player player);


        void shareWaypoint(Player player, String waypointId);

        void sharePin(Player player, String pinId);

        void claimShare(Player player, String shareCode);

        boolean beginActionToken(Player player, String action, List<String> data);

        void finishActionToken(Player player, boolean committed);
    }
}
