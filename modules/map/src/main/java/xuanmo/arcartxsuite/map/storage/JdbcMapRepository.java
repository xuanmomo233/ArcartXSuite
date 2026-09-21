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
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

/**
 * 地图模块 JDBC 存储仓库，基于 SQLite / MySQL 实现锚点解锁与路径点的持久化。
 * <p>
 * 关联模块：map。继承 {@code AbstractModuleRepository} 获取连接池与迁移能力，
 * 实现 {@code MapRepository} 接口提供数据访问方法。支持玩家数据清除与数据库迁移。
 */
public final class JdbcMapRepository extends AbstractModuleRepository implements MapRepository {

    public JdbcMapRepository(DataSource dataSource, StorageDescriptor descriptor,
                             File dataFolder, Logger logger) {
        super("AXS-Map", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("AXS_map_unlocks", "AXS_map_waypoints", "AXS_map_pins");
    }

    @Override
    protected List<String> allTables() {
        return List.of("AXS_map_unlocks", "AXS_map_waypoints", "AXS_map_pins");
    }

    @Override
    protected String playerUuidColumn() {
        return "uuid";
    }

    /**
     * 加载玩家已解锁的锚点 ID 集合。
     *
     * @param playerUuid 玩家 UUID
     * @return 已解锁锚点 ID 集合
     * @throws SQLException 数据库查询失败时抛出
     */
    @Override
    public Set<String> loadUnlockedAnchors(UUID playerUuid) throws SQLException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try (Connection connection = getConnection();
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

    /**
     * 记录玩家解锁锚点（忽略重复插入）。
     *
     * @param playerUuid 玩家 UUID
     * @param anchorId   锚点 ID
     * @param unlockTime 解锁时间戳
     * @throws SQLException 数据库写入失败时抛出
     */
    @Override
    public void unlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException {
        tryUnlockAnchor(playerUuid, anchorId, unlockTime);
    }

    /**
     * 尝试解锁锚点，若已解锁则不重复插入。
     *
     * @param playerUuid 玩家 UUID
     * @param anchorId   锚点 ID
     * @param unlockTime 解锁时间戳
     * @return {@code true} 表示本次插入成功（此前未解锁）
     * @throws SQLException 数据库写入失败时抛出
     */
    @Override
    public boolean tryUnlockAnchor(UUID playerUuid, String anchorId, long unlockTime) throws SQLException {
        String sql = !isMysql()
            ? "INSERT OR IGNORE INTO AXS_map_unlocks (uuid, anchor_id, unlock_time) VALUES (?, ?, ?)"
            : "INSERT IGNORE INTO AXS_map_unlocks (uuid, anchor_id, unlock_time) VALUES (?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, anchorId);
            statement.setLong(3, unlockTime);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * 移除玩家的指定锚点解锁记录。
     *
     * @param playerUuid 玩家 UUID
     * @param anchorId   锚点 ID
     * @throws SQLException 数据库删除失败时抛出
     */
    @Override
    public void removeUnlock(UUID playerUuid, String anchorId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM AXS_map_unlocks WHERE uuid = ? AND anchor_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, anchorId);
            statement.executeUpdate();
        }
    }

    /**
     * 在世界路径点数量限制内创建新路径点（事务操作）。
     *
     * @param playerUuid     玩家 UUID
     * @param waypoint       路径点数据
     * @param worldId        世界 ID
     * @param maxCountInWorld 该世界允许的最大路径点数量
     * @return {@code true} 表示创建成功，{@code false} 表示已达上限
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public boolean createWaypointIfUnderWorldLimit(
        UUID playerUuid,
        MapWaypoint waypoint,
        String worldId,
        int maxCountInWorld
    ) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement countStatement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM AXS_map_waypoints WHERE uuid = ? AND world = ?"
                )) {
                    countStatement.setString(1, playerUuid.toString());
                    countStatement.setString(2, worldId);
                    try (ResultSet resultSet = countStatement.executeQuery()) {
                        count = resultSet.next() ? resultSet.getInt(1) : 0;
                    }
                }
                if (count >= maxCountInWorld) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(waypointUpsertSql())) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, waypoint.waypointId());
                    statement.setString(3, waypoint.name());
                    statement.setString(4, waypoint.description());
                    statement.setString(5, waypoint.world());
                    statement.setDouble(6, waypoint.x());
                    statement.setDouble(7, waypoint.y());
                    statement.setDouble(8, waypoint.z());
                    statement.setLong(9, waypoint.createdAt());
                    statement.setLong(10, waypoint.updatedAt());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * 加载玩家的全部路径点列表。
     *
     * @param playerUuid 玩家 UUID
     * @return 路径点列表，按更新时间降序排列
     * @throws SQLException 数据库查询失败时抛出
     */
    @Override
    public List<MapWaypoint> loadWaypoints(UUID playerUuid) throws SQLException {
        List<MapWaypoint> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT waypoint_id, name, description, world, x, y, z, created_at, updated_at "
                     + "FROM AXS_map_waypoints WHERE uuid = ? ORDER BY updated_at DESC, waypoint_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(
                        new MapWaypoint(
                            resultSet.getString("waypoint_id"),
                            resultSet.getString("name"),
                            resultSet.getString("description"),
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

    /**
     * 新增或更新玩家路径点（upsert 语义）。
     *
     * @param playerUuid 玩家 UUID
     * @param waypoint   路径点数据
     * @throws SQLException 数据库写入失败时抛出
     */
    @Override
    public void upsertWaypoint(UUID playerUuid, MapWaypoint waypoint) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(waypointUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypoint.waypointId());
            statement.setString(3, waypoint.name());
            statement.setString(4, waypoint.description());
            statement.setString(5, waypoint.world());
            statement.setDouble(6, waypoint.x());
            statement.setDouble(7, waypoint.y());
            statement.setDouble(8, waypoint.z());
            statement.setLong(9, waypoint.createdAt());
            statement.setLong(10, waypoint.updatedAt());
            statement.executeUpdate();
        }
    }

    /**
     * 删除玩家指定路径点。
     *
     * @param playerUuid  玩家 UUID
     * @param waypointId  路径点 ID
     * @return {@code true} 表示删除成功
     * @throws SQLException 数据库删除失败时抛出
     */
    @Override
    public boolean deleteWaypoint(UUID playerUuid, String waypointId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM AXS_map_waypoints WHERE uuid = ? AND waypoint_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, waypointId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * 清除玩家的全部地图数据（解锁记录与路径点），事务操作。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void clearPlayer(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteUnlocks = connection.prepareStatement("DELETE FROM AXS_map_unlocks WHERE uuid = ?");
                 PreparedStatement deleteWaypoints = connection.prepareStatement("DELETE FROM AXS_map_waypoints WHERE uuid = ?");
                 PreparedStatement deletePins = connection.prepareStatement("DELETE FROM AXS_map_pins WHERE uuid = ?")) {
                deleteUnlocks.setString(1, playerUuid.toString());
                deleteUnlocks.executeUpdate();
                deleteWaypoints.setString(1, playerUuid.toString());
                deleteWaypoints.executeUpdate();
                // pin 也是玩家数据，clearPlayer 语义要求一并清掉
                deletePins.setString(1, playerUuid.toString());
                deletePins.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
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
                        description TEXT NOT NULL DEFAULT '',
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, waypoint_id)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_pins (
                        uuid TEXT NOT NULL,
                        pin_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        z REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, pin_id)
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
                        description TEXT NOT NULL DEFAULT '',
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, waypoint_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS AXS_map_pins (
                        uuid VARCHAR(36) NOT NULL,
                        pin_id VARCHAR(128) NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, pin_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
            // Migration: 给已存在的 waypoints 表加 description 列（不存在才加）。
            // MySQL 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 语法）——
            // 统一走 JDBC DatabaseMetaData 检查，SQLite/MySQL 都通用
            try (java.sql.ResultSet cols = connection.getMetaData()
                    .getColumns(connection.getCatalog(), null, "AXS_map_waypoints", "description")) {
                if (!cols.next()) {
                    statement.execute("ALTER TABLE AXS_map_waypoints ADD COLUMN description TEXT NOT NULL DEFAULT ''");
                }
            }
        }
    }

    private String waypointUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO AXS_map_waypoints (uuid, waypoint_id, name, description, world, x, y, z, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, waypoint_id) DO UPDATE SET
                    name = excluded.name,
                    description = excluded.description,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO AXS_map_waypoints (uuid, waypoint_id, name, description, world, x, y, z, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                description = VALUES(description),
                world = VALUES(world),
                x = VALUES(x),
                y = VALUES(y),
                z = VALUES(z),
                updated_at = VALUES(updated_at)
            """;
    }

    private String pinUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO AXS_map_pins (uuid, pin_id, name, description, world, x, z, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, pin_id) DO UPDATE SET
                    name = excluded.name,
                    description = excluded.description,
                    world = excluded.world,
                    x = excluded.x,
                    z = excluded.z,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO AXS_map_pins (uuid, pin_id, name, description, world, x, z, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                description = VALUES(description),
                world = VALUES(world),
                x = VALUES(x),
                z = VALUES(z),
                updated_at = VALUES(updated_at)
            """;
    }

    @Override
    public boolean createPinIfUnderWorldLimit(UUID playerUuid, MapPin pin, String worldId, int maxCountInWorld) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement countStatement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM AXS_map_pins WHERE uuid = ? AND world = ?"
                )) {
                    countStatement.setString(1, playerUuid.toString());
                    countStatement.setString(2, worldId);
                    try (ResultSet resultSet = countStatement.executeQuery()) {
                        count = resultSet.next() ? resultSet.getInt(1) : 0;
                    }
                }
                if (count >= maxCountInWorld) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(pinUpsertSql())) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, pin.pinId());
                    statement.setString(3, pin.name());
                    statement.setString(4, pin.description());
                    statement.setString(5, pin.world());
                    statement.setDouble(6, pin.x());
                    statement.setDouble(7, pin.z());
                    statement.setLong(8, pin.createdAt());
                    statement.setLong(9, pin.updatedAt());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<MapPin> loadPins(UUID playerUuid) throws SQLException {
        List<MapPin> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "SELECT pin_id, name, description, world, x, z, created_at, updated_at "
                    + "FROM AXS_map_pins WHERE uuid = ? ORDER BY updated_at DESC, pin_id ASC"
            )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(
                        new MapPin(
                            resultSet.getString("pin_id"),
                            resultSet.getString("name"),
                            resultSet.getString("description"),
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
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
    public void upsertPin(UUID playerUuid, MapPin pin) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(pinUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, pin.pinId());
            statement.setString(3, pin.name());
            statement.setString(4, pin.description());
            statement.setString(5, pin.world());
            statement.setDouble(6, pin.x());
            statement.setDouble(7, pin.z());
            statement.setLong(8, pin.createdAt());
            statement.setLong(9, pin.updatedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deletePin(UUID playerUuid, String pinId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM AXS_map_pins WHERE uuid = ? AND pin_id = ?"
            )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, pinId);
            return statement.executeUpdate() > 0;
        }
    }}
