package xuanmo.arcartxsuite.title.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;

/**
 * 称号模块 JDBC 仓储实现。
 * <p>
 * 基于 {@link AbstractModuleRepository} 提供 SQLite/MySQL 双方言支持，
 * 管理玩家拥有称号、装备分组、展示称号三张表的 CRUD 操作，
 * 并提供过期称号批量清理功能。属于 title 模块存储层。
 */
public final class JdbcTitleRepository extends AbstractModuleRepository implements TitleRepository {
    private static final int EXPIRATION_BATCH_SIZE = 256;
    private static final Instant NEW_GRANT = Instant.ofEpochMilli(Long.MIN_VALUE);
    private static final List<String> PRESENTATION_TABLES = List.of(
        "title_player_equipped_groups", "title_player_display_title");
    private static final String TITLE_INSERT_SQL = """
        INSERT INTO title_player_titles (player_uuid, title_id, hidden, granted_at, activates_at, expires_at, updated_at, granted_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String OWNED_STATE_QUERY = """
        SELECT 'owned' AS row_kind, title_id, '' AS group_id, hidden,
               granted_at, activates_at, expires_at, updated_at, granted_by
        FROM title_player_titles WHERE player_uuid = ?
        """;
    private static final String EQUIPPED_STATE_QUERY = """
        SELECT 'equipped' AS row_kind, title_id, group_id, 0 AS hidden, 0 AS granted_at,
               NULL AS activates_at, NULL AS expires_at, updated_at, '' AS granted_by
        FROM title_player_equipped_groups WHERE player_uuid = ?
        """;
    private static final String DISPLAY_STATE_QUERY = """
        SELECT 'display' AS row_kind, title_id, '' AS group_id, 0 AS hidden, 0 AS granted_at,
               NULL AS activates_at, NULL AS expires_at, updated_at, '' AS granted_by
        FROM title_player_display_title WHERE player_uuid = ?
        """;
    private static final String MYSQL_STATE_QUERY = OWNED_STATE_QUERY + " UNION ALL "
        + EQUIPPED_STATE_QUERY + " UNION ALL " + DISPLAY_STATE_QUERY
        + " ORDER BY row_kind ASC, granted_at ASC, group_id ASC";
    private static final List<String> SQLITE_STATE_QUERIES = List.of(
        OWNED_STATE_QUERY + " ORDER BY granted_at ASC",
        EQUIPPED_STATE_QUERY + " ORDER BY group_id ASC",
        DISPLAY_STATE_QUERY);

    public JdbcTitleRepository(DataSource dataSource, StorageDescriptor descriptor,
                               File dataFolder, Logger logger) {
        super("AXS-Title", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("title_player_titles", "title_player_equipped_groups", "title_player_display_title");
    }

    @Override
    protected List<String> allTables() {
        return List.of("title_player_titles", "title_player_equipped_groups", "title_player_display_title");
    }

    /**
     * 加载玩家的完整称号状态（拥有称号、装备分组、展示称号）。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家称号状态
     * @throws SQLException 数据库异常
     */
    @Override
    public PlayerTitleState loadState(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection()) {
            if (isMysql()) return readState(connection, playerUuid);
            connection.setAutoCommit(false);
            try {
                PlayerTitleState state = readState(connection, playerUuid);
                connection.commit();
                return state;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private PlayerTitleState readState(Connection connection, UUID playerUuid) throws SQLException {
        StateRows rows = new StateRows();
        if (isMysql()) readStateRows(connection, MYSQL_STATE_QUERY, playerUuid, 3, rows);
        else for (String sql : SQLITE_STATE_QUERIES) readStateRows(connection, sql, playerUuid, 1, rows);
        return new PlayerTitleState(playerUuid, rows.equipped, rows.owned, rows.display, rows.updatedAt);
    }

    private void readStateRows(Connection connection, String sql, UUID playerUuid, int parameterCount, StateRows rows)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String playerId = playerUuid.toString();
            for (int parameter = 1; parameter <= parameterCount; parameter++) statement.setString(parameter, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(result);
            }
        }
    }

    private static final class StateRows {
        private final Map<String, PlayerOwnedTitle> owned = new LinkedHashMap<>();
        private final Map<String, String> equipped = new LinkedHashMap<>();
        private String display = "";
        private Instant updatedAt = Instant.EPOCH;

        void add(ResultSet result) throws SQLException {
            Instant rowUpdatedAt = readInstant(result, "updated_at");
            if (rowUpdatedAt.isAfter(updatedAt)) updatedAt = rowUpdatedAt;
            String titleId = result.getString("title_id");
            switch (result.getString("row_kind")) {
                case "owned" -> owned.put(titleId, new PlayerOwnedTitle(
                    titleId, result.getBoolean("hidden"), readInstant(result, "granted_at"),
                    readNullableInstant(result, "activates_at"), readNullableInstant(result, "expires_at"),
                    rowUpdatedAt, nullToEmpty(result.getString("granted_by"))));
                case "equipped" -> equipped.put(result.getString("group_id"), titleId);
                case "display" -> display = nullToEmpty(titleId);
                default -> throw new SQLException("Unknown title state row kind");
            }
        }
    }

    /**
     * 保存（Upsert）玩家在指定分组的装备称号。
     *
     * @param playerUuid 玩家 UUID
     * @param groupId    分组 ID
     * @param titleId    称号 ID
     * @param updatedAt  更新时间
     * @throws SQLException 数据库异常
     */
    @Override
    public PlayerTitleState setEquippedTitle(UUID playerUuid, String groupId, String titleId, Instant updatedAt) throws SQLException {
        if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("称号分组不能为空");
        return mutateState(playerUuid, connection -> {
            if (titleId == null || titleId.isBlank()) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM title_player_equipped_groups WHERE player_uuid = ? AND group_id = ?")) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, groupId);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(equippedUpsertSql())) {
                    bindEquipped(statement, playerUuid, groupId, titleId, updatedAt);
                    statement.executeUpdate();
                }
            }
            deleteInvalidDisplay(connection, playerUuid, updatedAt);
        });
    }

    /**
     * 删除玩家在指定分组的装备记录。
     *
     * @param playerUuid 玩家 UUID
     * @param groupId    分组 ID
     * @throws SQLException 数据库异常
     */
    @Override
    public PlayerTitleState clearEquippedTitles(UUID playerUuid) throws SQLException {
        return mutateState(playerUuid, connection -> {
            deletePlayerRows(connection, "title_player_equipped_groups", playerUuid);
            deletePlayerRows(connection, "title_player_display_title", playerUuid);
        });
    }

    /**
     * 删除玩家的全部装备分组记录。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException 数据库异常
     */
    private void deletePlayerRows(Connection connection, String table, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private void bindEquipped(PreparedStatement statement, UUID playerUuid, String groupId, String titleId,
                              Instant updatedAt) throws SQLException {
        String playerId = playerUuid.toString();
        statement.setString(1, playerId);
        statement.setString(2, groupId);
        statement.setString(3, titleId);
        statement.setLong(4, updatedAt.toEpochMilli());
        statement.setString(5, playerId);
        statement.setString(6, titleId);
        statement.setLong(7, updatedAt.toEpochMilli());
        statement.setLong(8, updatedAt.toEpochMilli());
    }

    /**
     * 原子授予称号，锁定同一持有记录并保留隐藏设置。
     *
     * @param playerUuid 玩家 UUID
     * @param proposal 本次授予的称号记录
     * @param keepPermanent 是否保留已有永久持有记录
     * @throws SQLException 数据库异常
     */
    @Override
    public GrantedTitle grantOwnedTitle(UUID playerUuid, PlayerOwnedTitle proposal, boolean keepPermanent) throws SQLException {
        return transaction(connection -> {
            String ensureRow = TITLE_INSERT_SQL + (isMysql()
                ? " ON DUPLICATE KEY UPDATE player_uuid = player_uuid"
                : " ON CONFLICT(player_uuid, title_id) DO NOTHING");
            // Distinguish a new row from a renewal while holding the ownership lock on both databases.
            // The provisional row is replaced before commit and is never published.
            saveOwnedRecord(connection, ensureRow, playerUuid, new PlayerOwnedTitle(proposal.titleId(),
                proposal.hidden(), NEW_GRANT, proposal.activatesAt(), proposal.expiresAt(), proposal.updatedAt(), proposal.grantedBy()));
            PlayerOwnedTitle existing = null;
            String query = OWNED_STATE_QUERY + " AND title_id = ?" + (isMysql() ? " FOR UPDATE" : "");
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, proposal.titleId());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) existing = new PlayerOwnedTitle(
                        rows.getString("title_id"), rows.getBoolean("hidden"), readInstant(rows, "granted_at"),
                        readNullableInstant(rows, "activates_at"), readNullableInstant(rows, "expires_at"),
                        readInstant(rows, "updated_at"), nullToEmpty(rows.getString("granted_by")));
                }
            }
            if (existing == null) throw new SQLException("称号持有记录未建立");
            boolean newlyGranted = NEW_GRANT.equals(existing.grantedAt());
            if (!newlyGranted && keepPermanent && existing.activatesAt() == null && existing.expiresAt() == null) {
                return new GrantedTitle(existing, false);
            }
            PlayerOwnedTitle saved = new PlayerOwnedTitle(proposal.titleId(), existing.hidden(),
                persistedInstant(proposal.grantedAt()), persistedInstant(proposal.activatesAt()),
                persistedInstant(proposal.expiresAt()), persistedInstant(proposal.updatedAt()), nullToEmpty(proposal.grantedBy()));
            Instant now = saved.grantedAt();
            boolean clearPresentation = newlyGranted || !existing.isEffective(now) || !saved.isEffective(now);
            if (clearPresentation) {
                var key = List.of(new TitleKey(playerUuid.toString(), saved.titleId()));
                for (String table : PRESENTATION_TABLES) deleteTitleRows(connection, table, key);
            }
            saveOwnedRecord(connection, titleUpsertSql(), playerUuid, saved);
            return new GrantedTitle(saved, clearPresentation);
        });
    }

    private void saveOwnedRecord(Connection connection, String sql, UUID playerUuid, PlayerOwnedTitle title) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, title.titleId());
            statement.setBoolean(3, title.hidden());
            statement.setLong(4, title.grantedAt().toEpochMilli());
            if (title.activatesAt() == null) statement.setNull(5, Types.BIGINT);
            else statement.setLong(5, title.activatesAt().toEpochMilli());
            if (title.expiresAt() == null) statement.setNull(6, Types.BIGINT);
            else statement.setLong(6, title.expiresAt().toEpochMilli());
            statement.setLong(7, title.updatedAt().toEpochMilli());
            statement.setString(8, nullToEmpty(title.grantedBy()));
            statement.executeUpdate();
        }
    }

    private static Instant persistedInstant(Instant instant) {
        return instant == null ? null : Instant.ofEpochMilli(instant.toEpochMilli());
    }

    @Override
    public PlayerTitleState setOwnedTitleHidden(UUID playerUuid, String titleId, boolean hidden, Instant updatedAt) throws SQLException {
        return mutateState(playerUuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE title_player_titles SET hidden = ?, updated_at = ? WHERE player_uuid = ? AND title_id = ?")) {
                statement.setBoolean(1, hidden);
                statement.setLong(2, updatedAt.toEpochMilli());
                statement.setString(3, playerUuid.toString());
                statement.setString(4, titleId);
                statement.executeUpdate();
            }
        });
    }

    /**
     * 原子删除玩家拥有的指定称号、装备记录和展示称号。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @throws SQLException 数据库异常
     */
    @Override
    public void deleteOwnedTitle(UUID playerUuid, String titleId) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String table : List.of("title_player_titles", "title_player_equipped_groups", "title_player_display_title")) {
                    try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE player_uuid = ? AND title_id = ?"
                    )) {
                        statement.setString(1, playerUuid.toString());
                        statement.setString(2, titleId);
                        statement.executeUpdate();
                    }
                }
                checkTransactionInterrupted();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    exception.addSuppressed(rollback);
                }
                throw exception;
            }
        }
    }

    /**
     * 保存（Upsert）玩家的展示称号。
     *
     * @param playerUuid 玩家 UUID
     * @param titleId    称号 ID
     * @param updatedAt  更新时间
     * @throws SQLException 数据库异常
     */
    @Override
    public PlayerTitleState setDisplayTitle(UUID playerUuid, String titleId, Instant updatedAt) throws SQLException {
        return mutateState(playerUuid, connection -> {
            if (titleId == null || titleId.isBlank()) deletePlayerRows(connection, "title_player_display_title", playerUuid);
            else saveDisplay(connection, playerUuid, titleId, updatedAt);
        });
    }

    private void saveDisplay(Connection connection, UUID playerUuid, String titleId, Instant updatedAt) throws SQLException {
        if (titleId == null || titleId.isBlank()) return;
        try (PreparedStatement statement = connection.prepareStatement(displayTitleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, titleId);
            statement.setLong(3, updatedAt.toEpochMilli());
            statement.setString(4, playerUuid.toString());
            statement.setString(5, titleId);
            statement.setLong(6, updatedAt.toEpochMilli());
            statement.setLong(7, updatedAt.toEpochMilli());
            statement.setString(8, playerUuid.toString());
            statement.setString(9, titleId);
            statement.executeUpdate();
        }
    }

    /**
     * 删除玩家的展示称号记录。
     *
     * @param playerUuid 玩家 UUID
     * @throws SQLException 数据库异常
     */
    private void deleteInvalidDisplay(Connection connection, UUID playerUuid, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM title_player_display_title WHERE player_uuid = ?
            AND NOT EXISTS (
                SELECT 1 FROM title_player_equipped_groups e
                JOIN title_player_titles t ON t.player_uuid = e.player_uuid AND t.title_id = e.title_id
                WHERE e.player_uuid = title_player_display_title.player_uuid
                  AND e.title_id = title_player_display_title.title_id
                  AND (t.activates_at IS NULL OR t.activates_at <= ?)
                  AND (t.expires_at IS NULL OR t.expires_at > ?)
            )
            """)) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, now.toEpochMilli());
            statement.setLong(3, now.toEpochMilli());
            statement.executeUpdate();
        }
    }

    private PlayerTitleState mutateState(UUID playerUuid, StateMutation mutation) throws SQLException {
        return transaction(connection -> {
            mutation.apply(connection);
            return readState(connection, playerUuid);
        });
    }

    private <T> T transaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection);
                checkTransactionInterrupted();
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    @FunctionalInterface
    private interface StateMutation {
        void apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlTransaction<T> {
        T run(Connection connection) throws SQLException;
    }

    private static void checkTransactionInterrupted() throws SQLException {
        if (Thread.currentThread().isInterrupted()) throw new SQLException("称号事务已取消");
    }

    /**
     * 批量删除已过期的称号记录。
     *
     * @param now 当前时间
     * @return 删除的记录数
     * @throws SQLException 数据库异常
     */
    @Override
    public int deleteExpiredTitles(Instant now) throws SQLException {
        return transaction(connection -> {
            String select = """
                SELECT player_uuid, title_id FROM title_player_titles
                WHERE expires_at <= ? ORDER BY expires_at LIMIT ?
                """ + (isMysql() ? " FOR UPDATE" : "");
            var expired = new ArrayList<TitleKey>();
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setLong(1, now.toEpochMilli());
                statement.setInt(2, EXPIRATION_BATCH_SIZE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) expired.add(new TitleKey(rows.getString("player_uuid"), rows.getString("title_id")));
                }
            }
            if (expired.isEmpty()) return 0;
            for (String table : PRESENTATION_TABLES) deleteTitleRows(connection, table, expired);
            return deleteTitleRows(connection, "title_player_titles", expired);
        });
    }

    private int deleteTitleRows(Connection connection, String table, List<TitleKey> titles) throws SQLException {
        // Keep the leading primary-key column explicit, including for the equipment table.
        String sql = "DELETE FROM " + table + " WHERE player_uuid IN ("
            + String.join(",", Collections.nCopies(titles.size(), "?")) + ") AND (player_uuid, title_id) IN ("
            + String.join(",", Collections.nCopies(titles.size(), "(?, ?)")) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (TitleKey key : titles) statement.setString(index++, key.playerUuid());
            for (TitleKey key : titles) {
                statement.setString(index++, key.playerUuid());
                statement.setString(index++, key.titleId());
            }
            return statement.executeUpdate();
        }
    }

    private record TitleKey(String playerUuid, String titleId) {}

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_titles (
                        player_uuid TEXT NOT NULL,
                        title_id TEXT NOT NULL,
                        hidden INTEGER NOT NULL DEFAULT 0,
                        granted_at INTEGER NOT NULL,
                        activates_at INTEGER,
                        expires_at INTEGER,
                        updated_at INTEGER NOT NULL,
                        granted_by TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, title_id)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_equipped_groups (
                        player_uuid TEXT NOT NULL,
                        group_id TEXT NOT NULL,
                        title_id TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, group_id)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_display_title (
                        player_uuid TEXT NOT NULL,
                        title_id TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid)
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_title_player_titles_expires_at ON title_player_titles(expires_at);");
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_titles (
                        player_uuid VARCHAR(36) NOT NULL,
                        title_id VARCHAR(128) NOT NULL,
                        hidden BOOLEAN NOT NULL DEFAULT FALSE,
                        granted_at BIGINT NOT NULL,
                        activates_at BIGINT NULL,
                        expires_at BIGINT NULL,
                        updated_at BIGINT NOT NULL,
                        granted_by VARCHAR(128) NOT NULL,
                        PRIMARY KEY (player_uuid, title_id),
                        INDEX idx_title_player_titles_expires_at (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_equipped_groups (
                        player_uuid VARCHAR(36) NOT NULL,
                        group_id VARCHAR(128) NOT NULL,
                        title_id VARCHAR(128) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, group_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS title_player_display_title (
                        player_uuid VARCHAR(36) NOT NULL,
                        title_id VARCHAR(128) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private String displayTitleUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO title_player_display_title (player_uuid, title_id, updated_at)
                SELECT ?, ?, ? FROM title_player_titles
                WHERE player_uuid = ? AND title_id = ?
                  AND (activates_at IS NULL OR activates_at <= ?)
                  AND (expires_at IS NULL OR expires_at > ?)
                  AND EXISTS (SELECT 1 FROM title_player_equipped_groups WHERE player_uuid = ? AND title_id = ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    title_id = excluded.title_id,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO title_player_display_title (player_uuid, title_id, updated_at)
            SELECT ?, ?, ? FROM title_player_titles
            WHERE player_uuid = ? AND title_id = ?
              AND (activates_at IS NULL OR activates_at <= ?)
              AND (expires_at IS NULL OR expires_at > ?)
              AND EXISTS (SELECT 1 FROM title_player_equipped_groups WHERE player_uuid = ? AND title_id = ?)
            ON DUPLICATE KEY UPDATE
                title_id = VALUES(title_id),
                updated_at = VALUES(updated_at)
            """;
    }

    private String equippedUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO title_player_equipped_groups (player_uuid, group_id, title_id, updated_at)
                SELECT ?, ?, ?, ? FROM title_player_titles
                WHERE player_uuid = ? AND title_id = ?
                  AND (activates_at IS NULL OR activates_at <= ?)
                  AND (expires_at IS NULL OR expires_at > ?)
                ON CONFLICT(player_uuid, group_id) DO UPDATE SET
                    title_id = excluded.title_id,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO title_player_equipped_groups (player_uuid, group_id, title_id, updated_at)
            SELECT ?, ?, ?, ? FROM title_player_titles
            WHERE player_uuid = ? AND title_id = ?
              AND (activates_at IS NULL OR activates_at <= ?)
              AND (expires_at IS NULL OR expires_at > ?)
            ON DUPLICATE KEY UPDATE
                title_id = VALUES(title_id),
                updated_at = VALUES(updated_at)
            """;
    }

    private String titleUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO title_player_titles (player_uuid, title_id, hidden, granted_at, activates_at, expires_at, updated_at, granted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, title_id) DO UPDATE SET
                    granted_at = excluded.granted_at,
                    activates_at = excluded.activates_at,
                    expires_at = excluded.expires_at,
                    updated_at = excluded.updated_at,
                    granted_by = excluded.granted_by
                """;
        }
        return """
            INSERT INTO title_player_titles (player_uuid, title_id, hidden, granted_at, activates_at, expires_at, updated_at, granted_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                granted_at = VALUES(granted_at),
                activates_at = VALUES(activates_at),
                expires_at = VALUES(expires_at),
                updated_at = VALUES(updated_at),
                granted_by = VALUES(granted_by)
            """;
    }


    private static Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        return Instant.ofEpochMilli(resultSet.getLong(column));
    }

    private static Instant readNullableInstant(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
