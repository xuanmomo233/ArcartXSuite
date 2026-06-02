package xuanmo.arcartxsuite.map.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.StorageDialect;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

public final class JdbcMapRepository extends AbstractModuleRepository implements MapRepository {

    private final StorageConfiguration configuration;

    public JdbcMapRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-Map", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("AXS_map_unlocks", "AXS_map_waypoints");
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    @Override
    public Set<String> loadUnlockedAnchors(UUID playerUuid) throws SQLException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT anchor_id FROM AXS_map_unlocks WHERE uuid = ? ORDER BY unlock_time ASC, anchor_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("anchor_id"));
                }
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public void unlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(unlockUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, anchorId);
            statement.setLong(3, unlockTime);
            statement.executeUpdate();
        }
    }

    @Override
    public List<MapWaypoint> loadWaypoints(UUID playerUuid) throws SQLException {
        List<MapWaypoint> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT waypoint_id, name, world, x, y, z, created_at, updated_at "
                     + "FROM AXS_map_waypoints WHERE uuid = ? ORDER BY updated_at DESC, waypoint_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(
                        new MapWaypoint(
                            resultSet.getString("waypoint_id"),
                            resultSet.getString("name"),
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getLong("created_at"),
                            resultSet.getLong("updated_at")
                        )
                    );
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void upsertWaypoint(UUID playerUuid, MapWaypoint waypoint) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(waypointUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypoint.waypointId());
            statement.setString(3, waypoint.name());
            statement.setString(4, waypoint.world());
            statement.setDouble(5, waypoint.x());
            statement.setDouble(6, waypoint.y());
            statement.setDouble(7, waypoint.z());
            statement.setLong(8, waypoint.createdAt());
            statement.setLong(9, waypoint.updatedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deleteWaypoint(UUID playerUuid, String waypointId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM AXS_map_waypoints WHERE uuid = ? AND waypoint_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypointId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public void clearPlayer(UUID playerUuid) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteUnlocks = connection.prepareStatement("DELETE FROM AXS_map_unlocks WHERE uuid = ?");
                 PreparedStatement deleteWaypoints = connection.prepareStatement("DELETE FROM AXS_map_waypoints WHERE uuid = ?")) {
                deleteUnlocks.setString(1, playerUuid.toString());
                deleteUnlocks.executeUpdate();
                deleteWaypoints.setString(1, playerUuid.toString());
                deleteWaypoints.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == StorageDialect.SQLITE) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_unlocks (
                        uuid TEXT NOT NULL,
                        anchor_id TEXT NOT NULL,
                        unlock_time INTEGER NOT NULL,
                        PRIMARY KEY (uuid, anchor_id)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_waypoints (
                        uuid TEXT NOT NULL,
                        waypoint_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, waypoint_id)
                    );
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_unlocks (
                        uuid VARCHAR(36) NOT NULL,
                        anchor_id VARCHAR(128) NOT NULL,
                        unlock_time BIGINT NOT NULL,
                        PRIMARY KEY (uuid, anchor_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_waypoints (
                        uuid VARCHAR(36) NOT NULL,
                        waypoint_id VARCHAR(128) NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, waypoint_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private String unlockUpsertSql() {
        if (configuration.dialect() == StorageDialect.SQLITE) {
            return """
                INSERT INTO AXS_map_unlocks (uuid, anchor_id, unlock_time)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, anchor_id) DO UPDATE SET
                    unlock_time = excluded.unlock_time
                """;
        }
        return """
            INSERT INTO AXS_map_unlocks (uuid, anchor_id, unlock_time)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlock_time = VALUES(unlock_time)
            """;
    }

    private String waypointUpsertSql() {
        if (configuration.dialect() == StorageDialect.SQLITE) {
            return """
                INSERT INTO AXS_map_waypoints (uuid, waypoint_id, name, world, x, y, z, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, waypoint_id) DO UPDATE SET
                    name = excluded.name,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO AXS_map_waypoints (uuid, waypoint_id, name, world, x, y, z, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                world = VALUES(world),
                x = VALUES(x),
                y = VALUES(y),
                z = VALUES(z),
                updated_at = VALUES(updated_at)
            """;
    }

    private Connection connection() throws SQLException {
        return getConnection();
    }
}
