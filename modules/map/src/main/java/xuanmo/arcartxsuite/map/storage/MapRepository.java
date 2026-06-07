package xuanmo.arcartxsuite.map.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

public interface MapRepository {

    void initialize() throws SQLException;

    Set<String> loadUnlockedAnchors(UUID playerUuid) throws SQLException;

    void unlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException;

    /**
     * 原子插入锚点解锁记录，仅在新解锁时返回 true。
     */
    boolean tryUnlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException;

    void removeUnlock(UUID playerUuid, String anchorId) throws SQLException;

    /**
     * 在世界路径点未达上限时创建路径点，返回 false 表示已达上限。
     */
    boolean createWaypointIfUnderWorldLimit(UUID playerUuid, MapWaypoint waypoint, String worldId, int maxCountInWorld) throws SQLException;

    List<MapWaypoint> loadWaypoints(UUID playerUuid) throws SQLException;

    void upsertWaypoint(UUID playerUuid, MapWaypoint waypoint) throws SQLException;

    boolean deleteWaypoint(UUID playerUuid, String waypointId) throws SQLException;

    void clearPlayer(UUID playerUuid) throws SQLException;

    void close();
}
