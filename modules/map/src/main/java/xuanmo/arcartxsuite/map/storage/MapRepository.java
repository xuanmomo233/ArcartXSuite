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

    List<MapWaypoint> loadWaypoints(UUID playerUuid) throws SQLException;

    void upsertWaypoint(UUID playerUuid, MapWaypoint waypoint) throws SQLException;

    boolean deleteWaypoint(UUID playerUuid, String waypointId) throws SQLException;

    void clearPlayer(UUID playerUuid) throws SQLException;

    void close();
}
