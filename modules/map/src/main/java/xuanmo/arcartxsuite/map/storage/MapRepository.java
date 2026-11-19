package xuanmo.arcartxsuite.map.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

public interface MapRepository {

    void initialize() throws SQLException;

    Set<String> loadUnlockedAnchors(UUID playerUuid) throws SQLException;

    void unlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException;

    /**
     * 原子插入锚点解锁记录，仅在新解锁时返回 true。
     */
    boolean tryUnlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException;

    boolean isAnchorUnlockConfirmed(UUID playerUuid, String anchorId) throws SQLException;

    record AnchorReservation(UUID player, String anchor, UUID id, String source, long createdAt) { }

    boolean reserveAnchorUnlock(UUID playerUuid, String anchorId, long time, UUID reservation, String source) throws SQLException;

    Optional<AnchorReservation> findAnchorReservation(UUID reservation) throws SQLException;

    Optional<UUID> pendingAnchorReservation(UUID playerUuid, String anchorId) throws SQLException;

    boolean confirmAnchorUnlock(UUID playerUuid, String anchorId, long time, UUID reservation) throws SQLException;

    boolean cancelAnchorUnlock(UUID playerUuid, String anchorId, UUID reservation) throws SQLException;

    /**
     * 在世界路径点未达上限时创建路径点，返回 false 表示已达上限。
     */
    boolean createWaypointIfUnderWorldLimit(UUID playerUuid, MapWaypoint waypoint, String worldId, int maxCountInWorld) throws SQLException;

    List<MapWaypoint> loadWaypoints(UUID playerUuid) throws SQLException;

    void upsertWaypoint(UUID playerUuid, MapWaypoint waypoint) throws SQLException;

    boolean deleteWaypoint(UUID playerUuid, String waypointId) throws SQLException;

    /**
     * 在世界标记点未达上限时创建标记点，返回 false 表示已达上限。
     */
    boolean createPinIfUnderWorldLimit(UUID playerUuid, MapPin pin, String worldId, int maxCountInWorld) throws SQLException;

    List<MapPin> loadPins(UUID playerUuid) throws SQLException;

    void upsertPin(UUID playerUuid, MapPin pin) throws SQLException;

    boolean deletePin(UUID playerUuid, String pinId) throws SQLException;

    void clearPlayer(UUID playerUuid) throws SQLException;

    /**
     * 加载玩家地图界面设置（雷达/HUD 外观等本地偏好的服务端留存），
     * 键值与客户端 {@code options.properties} 一致。
     *
     * @param playerUuid 玩家 UUID
     * @return 键→值映射；无记录返回空 Map
     */
    java.util.Map<String, String> loadPlayerSettings(UUID playerUuid) throws SQLException;

    /**
     * 覆盖写入玩家单个设置项（upsert 语义）。
     *
     * @param playerUuid 玩家 UUID
     * @param key        设置键（白名单之外的键由业务层拦截）
     * @param value      设置值（字符串形态，客户端自行解析）
     */
    void savePlayerSetting(UUID playerUuid, String key, String value) throws SQLException;

    /**
     * 关闭仓库，释放连接池资源。
     */
    void shutdown();
}
