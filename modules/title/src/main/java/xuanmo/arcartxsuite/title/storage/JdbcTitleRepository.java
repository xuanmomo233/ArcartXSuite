package xuanmo.arcartxsuite.title.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.title.config.TitlePersistenceDialect;
import xuanmo.arcartxsuite.title.config.TitleStorageConfiguration;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;

public final class JdbcTitleRepository extends AbstractModuleRepository implements TitleRepository {

    private final TitleStorageConfiguration configuration;

    public JdbcTitleRepository(File dataFolder, TitleStorageConfiguration configuration, Logger logger) {
        super("AXS-Title", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        migrateActivatesAtColumn(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("title_player_titles", "title_player_equipped_groups", "title_player_display_title");
    }

    @Override
    public PlayerTitleState loadState(UUID playerUuid) throws SQLException {
        Instant updatedAt = Instant.EPOCH;

        Map<String, PlayerOwnedTitle> ownedTitles = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT title_id, hidden, granted_at, activates_at, expires_at, updated_at, granted_by "
                     + "FROM title_player_titles WHERE player_uuid = ? ORDER BY granted_at ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String titleId = resultSet.getString("title_id");
                    ownedTitles.put(
                        titleId,
                        new PlayerOwnedTitle(
                            titleId,
                            resultSet.getBoolean("hidden"),
                            readInstant(resultSet, "granted_at"),
                            readNullableInstant(resultSet, "activates_at"),
                            readNullableInstant(resultSet, "expires_at"),
                            readInstant(resultSet, "updated_at"),
                            nullToEmpty(resultSet.getString("granted_by"))
                        )
                    );
                    Instant titleUpdatedAt = readInstant(resultSet, "updated_at");
                    if (titleUpdatedAt.isAfter(updatedAt)) {
                        updatedAt = titleUpdatedAt;
                    }
                }
            }
        }

        Map<String, String> equippedTitleIdsByGroup = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT group_id, title_id, updated_at FROM title_player_equipped_groups WHERE player_uuid = ? ORDER BY group_id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    equippedTitleIdsByGroup.put(resultSet.getString("group_id"), resultSet.getString("title_id"));
                    Instant equippedUpdatedAt = readInstant(resultSet, "updated_at");
                    if (equippedUpdatedAt.isAfter(updatedAt)) {
                        updatedAt = equippedUpdatedAt;
                    }
                }
            }
        }

        String displayTitleId = "";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT title_id, updated_at FROM title_player_display_title WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    displayTitleId = nullToEmpty(resultSet.getString("title_id"));
                    Instant displayUpdatedAt = readInstant(resultSet, "updated_at");
                    if (displayUpdatedAt.isAfter(updatedAt)) {
                        updatedAt = displayUpdatedAt;
                    }
                }
            }
        }

        return new PlayerTitleState(playerUuid, equippedTitleIdsByGroup, ownedTitles, displayTitleId, updatedAt);
    }

    @Override
    public void saveEquippedTitle(UUID playerUuid, String groupId, String titleId, Instant updatedAt) throws SQLException {
        if (groupId == null || groupId.isBlank() || titleId == null || titleId.isBlank()) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(equippedUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, groupId);
            statement.setString(3, titleId);
            statement.setLong(4, updatedAt.toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteEquippedGroup(UUID playerUuid, String groupId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_equipped_groups WHERE player_uuid = ? AND group_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, groupId);
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteEquippedTitle(UUID playerUuid, String titleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_equipped_groups WHERE player_uuid = ? AND title_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, titleId);
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteAllEquippedGroups(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_equipped_groups WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveOwnedTitle(UUID playerUuid, PlayerOwnedTitle ownedTitle) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(titleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ownedTitle.titleId());
            statement.setBoolean(3, ownedTitle.hidden());
            statement.setLong(4, ownedTitle.grantedAt().toEpochMilli());
            if (ownedTitle.activatesAt() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, ownedTitle.activatesAt().toEpochMilli());
            }
            if (ownedTitle.expiresAt() == null) {
                statement.setNull(6, Types.BIGINT);
            } else {
                statement.setLong(6, ownedTitle.expiresAt().toEpochMilli());
            }
            statement.setLong(7, ownedTitle.updatedAt().toEpochMilli());
            statement.setString(8, nullToEmpty(ownedTitle.grantedBy()));
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteOwnedTitle(UUID playerUuid, String titleId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_titles WHERE player_uuid = ? AND title_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, titleId);
            statement.executeUpdate();
        }
    }

    @Override
    public void saveDisplayTitle(UUID playerUuid, String titleId, Instant updatedAt) throws SQLException {
        if (titleId == null || titleId.isBlank()) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(displayTitleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, titleId);
            statement.setLong(3, updatedAt.toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteDisplayTitle(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_display_title WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public int deleteExpiredTitles(Instant now) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM title_player_titles WHERE expires_at IS NOT NULL AND expires_at <= ?"
             )) {
            statement.setLong(1, now.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == TitlePersistenceDialect.SQLITE) {
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
        if (configuration.dialect() == TitlePersistenceDialect.SQLITE) {
            return """
                INSERT INTO title_player_display_title (player_uuid, title_id, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    title_id = excluded.title_id,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO title_player_display_title (player_uuid, title_id, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                title_id = VALUES(title_id),
                updated_at = VALUES(updated_at)
            """;
    }

    private String equippedUpsertSql() {
        if (configuration.dialect() == TitlePersistenceDialect.SQLITE) {
            return """
                INSERT INTO title_player_equipped_groups (player_uuid, group_id, title_id, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, group_id) DO UPDATE SET
                    title_id = excluded.title_id,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO title_player_equipped_groups (player_uuid, group_id, title_id, updated_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                title_id = VALUES(title_id),
                updated_at = VALUES(updated_at)
            """;
    }

    private String titleUpsertSql() {
        if (configuration.dialect() == TitlePersistenceDialect.SQLITE) {
            return """
                INSERT INTO title_player_titles (player_uuid, title_id, hidden, granted_at, activates_at, expires_at, updated_at, granted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, title_id) DO UPDATE SET
                    hidden = excluded.hidden,
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
                hidden = VALUES(hidden),
                granted_at = VALUES(granted_at),
                activates_at = VALUES(activates_at),
                expires_at = VALUES(expires_at),
                updated_at = VALUES(updated_at),
                granted_by = VALUES(granted_by)
            """;
    }

    private void migrateActivatesAtColumn(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == TitlePersistenceDialect.SQLITE) {
                try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(title_player_titles)")) {
                    boolean found = false;
                    while (resultSet.next()) {
                        if ("activates_at".equalsIgnoreCase(resultSet.getString("name"))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        statement.execute("ALTER TABLE title_player_titles ADD COLUMN activates_at INTEGER");
                        logger.info("称号数据库迁移: 已添加 activates_at 列。");
                    }
                }
            } else {
                try (ResultSet resultSet = connection.getMetaData().getColumns(
                    null, null, "title_player_titles", "activates_at"
                )) {
                    if (!resultSet.next()) {
                        statement.execute(
                            "ALTER TABLE title_player_titles ADD COLUMN activates_at BIGINT NULL AFTER granted_at"
                        );
                        logger.info("称号数据库迁移: 已添加 activates_at 列。");
                    }
                }
            }
        } catch (SQLException exception) {
            logger.warning("称号数据库迁移 activates_at 列失败: " + exception.getMessage());
        }
    }

    private Connection connection() throws SQLException {
        return getConnection();
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        return Instant.ofEpochMilli(resultSet.getLong(column));
    }

    private static Instant readNullableInstant(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
