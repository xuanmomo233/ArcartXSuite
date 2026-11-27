package xuanmo.arcartxsuite.map.crossserver;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapTeleportSession;
import xuanmo.arcartxsuite.map.service.MapTerrainPushService.ChunkRequest;

public interface MapCrossServer {
    void start();
    void shutdown();
    String nodeId();
    int remoteNodeCount();
    List<RemoteWorldView> remoteWorlds();
    RemoteWorldView remoteWorld(String compositeId);
    RemoteAnchorView remoteAnchor(String compositeId);
    void relayTerrainRequest(Player player, String compositeWorldId, List<ChunkRequest> chunks);
    MapOperationResult requestTeleport(MapTeleportSession session, String nodeId, String worldId,
                                      String anchorId, BooleanSupplier authorized);
    void onPlayerAdmitted(MapTeleportSession session);
    void onPlayerQuit(UUID playerUuid);
    void cancelTeleportRequests(UUID playerUuid);
    void cancelTerrainRelays(UUID playerUuid);

    /**
     * 远程世界视图（跨服目录的只读投影）。
     *
     * @param nodeId           来源节点 id
     * @param serverName       节点显示名
     * @param connectName      代理 Connect 目标名
     * @param worldId          目标节点上的原始世界 id
     * @param compositeId      复合世界 id（{@code nodeId/worldId}）
     * @param displayName      显示名
     * @param dimensionKey     客户端维度桶 key（{@code axsremote.<node>.<dim>}）
     * @param centerX          世界中心 X
     * @param centerZ          世界中心 Z
     * @param preloadVersion   远程预加载版本（0 = 不提供远程地形）
     * @param preloadChunkList 远程预加载覆盖块坐标（下发客户端用）
     * @param preloadChunkSet  远程预加载覆盖块坐标集合（中继校验用，packed long）
     * @param allowArrive      是否允许跨服落地
     * @param anchors          远程锚点视图列表（已按 sortOrder 排序）
     */
    public record RemoteWorldView(
        String nodeId,
        String serverName,
        String connectName,
        String worldId,
        String compositeId,
        String displayName,
        String dimensionKey,
        double centerX,
        double centerZ,
        int preloadVersion,
        List<int[]> preloadChunkList,
        Set<Long> preloadChunkSet,
        boolean allowArrive,
        List<RemoteAnchorView> anchors
    ) {
    }

    /**
     * 远程锚点视图。
     *
     * @param nodeId            来源节点 id
     * @param serverName        节点显示名
     * @param compositeWorldId  所在世界的复合 id
     * @param worldId           原始世界 id
     * @param anchorId          原始锚点 id
     * @param compositeId       复合锚点 id（{@code nodeId/anchorId}）
     * @param displayName       显示名
     * @param description       描述
     * @param x                 X 坐标
     * @param y                 Y 坐标
     * @param z                 Z 坐标
     * @param sortOrder         排序权重
     * @param teleportCost      传送费用展示文本
     * @param allowArrive       所在世界是否允许跨服落地
     */
    public record RemoteAnchorView(
        String nodeId,
        String serverName,
        String compositeWorldId,
        String worldId,
        String anchorId,
        String compositeId,
        String displayName,
        String description,
        double x,
        double y,
        double z,
        int sortOrder,
        String teleportCost,
        boolean allowArrive
    ) {
    }
}
