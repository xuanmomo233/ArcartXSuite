package xuanmo.arcartxsuite.eventpacket.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketPersistenceDialect;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketStorageConfiguration;

public final class JdbcEventPacketRepository extends AbstractModuleRepository implements EventPacketRepository {

    private final EventPacketStorageConfiguration configuration;

    public JdbcEventPacketRepository(File dataFolder, EventPacketStorageConfiguration configuration, Logger logger) {
        super("AXS-EventPacket", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("eventpacket_kill_progress", "eventpacket_fired_rules");
    }

    // ─── Kill count ──────────────────────────────────────────

    @Override
    public int getKillCount(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT kill_count FROM eventpacket_kill_progress WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("kill_count") : 0;
            }
        }
    }

    @Override
    public void setKillCount(UUID playerUuid, String ruleId, int count) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(killCountUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setInt(3, Math.max(0, count));
            statement.setLong(4, System.currentTimeMillis());
            if (configuration.dialect() == EventPacketPersistenceDialect.MYSQL) {
                statement.setInt(5, Math.max(0, count));
                statement.setLong(6, System.currentTimeMillis());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void incrementKillCount(UUID playerUuid, String ruleId) throws SQLException {
        if (configuration.dialect() == EventPacketPersistenceDialect.SQLITE) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                     VALUES (?, ?, 1, ?)
                     ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                         kill_count = kill_count + 1,
                         updated_at = excluded.updated_at
                     """)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, ruleId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        } else {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                     VALUES (?, ?, 1, ?)
                     ON DUPLICATE KEY UPDATE
                         kill_count = kill_count + 1,
                         updated_at = VALUES(updated_at)
                     """)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, ruleId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public Map<String, Integer> loadAllKillCounts(UUID playerUuid) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT rule_id, kill_count FROM eventpacket_kill_progress WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString("rule_id"), resultSet.getInt("kill_count"));
                }
            }
        }
        return counts;
    }

    // ─── Fired rules ─────────────────────────────────────────

    @Override
    public boolean hasFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM eventpacket_fired_rules WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public void markFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(firedRuleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setLong(3, System.currentTimeMillis());
            if (configuration.dialect() == EventPacketPersistenceDialect.MYSQL) {
                statement.setLong(4, System.currentTimeMillis());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void removeFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM eventpacket_fired_rules WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.executeUpdate();
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────

    @Override
    public void close() {
        shutdown();
    }

    // ─── Internal ────────────────────────────────────────────

    private Connection connection() throws SQLException {
        return getConnection();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == EventPacketPersistenceDialect.SQLITE) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_kill_progress (
                        player_uuid TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        kill_count INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_fired_rules (
                        player_uuid TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        fired_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    )
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_kill_progress (
                        player_uuid VARCHAR(36) NOT NULL,
                        rule_id VARCHAR(128) NOT NULL,
                        kill_count INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS eventpacket_fired_rules (
                        player_uuid VARCHAR(36) NOT NULL,
                        rule_id VARCHAR(128) NOT NULL,
                        fired_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, rule_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            }
        }
    }

    private String killCountUpsertSql() {
        if (configuration.dialect() == EventPacketPersistenceDialect.SQLITE) {
            return """
                INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                    kill_count = excluded.kill_count,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO eventpacket_kill_progress (player_uuid, rule_id, kill_count, updated_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                kill_count = VALUES(kill_count),
                updated_at = VALUES(updated_at)
            """;
    }

    private String firedRuleUpsertSql() {
        if (configuration.dialect() == EventPacketPersistenceDialect.SQLITE) {
            return """
                INSERT INTO eventpacket_fired_rules (player_uuid, rule_id, fired_at)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, rule_id) DO UPDATE SET
                    fired_at = excluded.fired_at
                """;
        }
        return """
            INSERT INTO eventpacket_fired_rules (player_uuid, rule_id, fired_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                fired_at = VALUES(fired_at)
            """;
    }
}
