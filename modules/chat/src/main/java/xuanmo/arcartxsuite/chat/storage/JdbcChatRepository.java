package xuanmo.arcartxsuite.chat.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.chat.config.ChatPersistenceDialect;
import xuanmo.arcartxsuite.chat.config.ChatStorageConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;

public final class JdbcChatRepository extends AbstractModuleRepository implements ChatRepository {

    private final ChatStorageConfiguration configuration;

    public JdbcChatRepository(File dataFolder, ChatStorageConfiguration configuration, Logger logger) {
        super("AXS-Chat", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("chat_player_states", "chat_player_mutes", "chat_player_profiles", "chat_ignored_players");
    }

    @Override
    public ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException {
        String channelId = defaultChannelId;
        boolean acceptsPrivate = true;
        boolean acceptsMentions = true;
        boolean socialSpy = false;
        Instant updatedAt = Instant.now();

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at "
                     + "FROM chat_player_states WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    channelId = nullToEmpty(resultSet.getString("current_channel_id"));
                    acceptsPrivate = resultSet.getBoolean("accepts_private_messages");
                    acceptsMentions = resultSet.getBoolean("accepts_mentions");
                    socialSpy = resultSet.getBoolean("social_spy_enabled");
                    updatedAt = Instant.ofEpochMilli(resultSet.getLong("updated_at"));
                }
            }
        }

        Set<UUID> ignored = new LinkedHashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT ignored_uuid FROM chat_ignored_players WHERE player_uuid = ? ORDER BY ignored_uuid ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ignored.add(UUID.fromString(resultSet.getString("ignored_uuid")));
                }
            }
        }
        return new ChatPlayerState(playerUuid, channelId.isBlank() ? defaultChannelId : channelId, acceptsPrivate, acceptsMentions, socialSpy, ignored, updatedAt);
    }

    @Override
    public void saveState(ChatPlayerState state) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(stateUpsertSql())) {
            statement.setString(1, state.playerUuid().toString());
            statement.setString(2, state.currentChannelId());
            statement.setBoolean(3, state.acceptsPrivateMessages());
            statement.setBoolean(4, state.acceptsMentions());
            statement.setBoolean(5, state.socialSpyEnabled());
            statement.setLong(6, state.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveIgnoredPlayers(UUID playerUuid, Set<UUID> ignoredPlayers) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM chat_ignored_players WHERE player_uuid = ?"
            )) {
                delete.setString(1, playerUuid.toString());
                delete.executeUpdate();
            }
            if (ignoredPlayers != null && !ignoredPlayers.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chat_ignored_players (player_uuid, ignored_uuid) VALUES (?, ?)"
                )) {
                    for (UUID ignoredUuid : ignoredPlayers) {
                        insert.setString(1, playerUuid.toString());
                        insert.setString(2, ignoredUuid.toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    @Override
    public Optional<ChatMuteRecord> loadMute(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, muted_by, reason, created_at, expires_at "
                     + "FROM chat_player_mutes WHERE player_uuid = ? LIMIT 1"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long expiresAt = resultSet.getLong("expires_at");
                return Optional.of(new ChatMuteRecord(
                    UUID.fromString(resultSet.getString("player_uuid")),
                    nullToEmpty(resultSet.getString("muted_by")),
                    nullToEmpty(resultSet.getString("reason")),
                    Instant.ofEpochMilli(resultSet.getLong("created_at")),
                    resultSet.wasNull() ? null : Instant.ofEpochMilli(expiresAt)
                ));
            }
        }
    }

    @Override
    public void saveMute(ChatMuteRecord muteRecord) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(muteUpsertSql())) {
            statement.setString(1, muteRecord.playerUuid().toString());
            statement.setString(2, nullToEmpty(muteRecord.mutedBy()));
            statement.setString(3, nullToEmpty(muteRecord.reason()));
            statement.setLong(4, muteRecord.createdAt().toEpochMilli());
            if (muteRecord.expiresAt() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, muteRecord.expiresAt().toEpochMilli());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteMute(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM chat_player_mutes WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void upsertProfile(ChatPlayerProfile profile) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(profileUpsertSql())) {
            statement.setString(1, profile.playerUuid().toString());
            statement.setString(2, nullToEmpty(profile.lastKnownName()));
            statement.setString(3, nullToEmpty(profile.lastKnownName()).toLowerCase());
            statement.setLong(4, profile.lastSeenAt().toEpochMilli());
            statement.setString(5, nullToEmpty(profile.lastServer()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<ChatPlayerProfile> loadProfile(UUID playerUuid) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_seen_at, last_server "
                     + "FROM chat_player_profiles WHERE player_uuid = ? LIMIT 1"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readProfile(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<ChatPlayerProfile> findProfileByName(String playerName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_seen_at, last_server "
                     + "FROM chat_player_profiles WHERE last_known_name_lower = ? LIMIT 1"
             )) {
            statement.setString(1, nullToEmpty(playerName).trim().toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readProfile(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (configuration.dialect() == ChatPersistenceDialect.SQLITE) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_states (
                        player_uuid TEXT PRIMARY KEY,
                        current_channel_id TEXT NOT NULL,
                        accepts_private_messages INTEGER NOT NULL DEFAULT 1,
                        accepts_mentions INTEGER NOT NULL DEFAULT 1,
                        social_spy_enabled INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_ignored_players (
                        player_uuid TEXT NOT NULL,
                        ignored_uuid TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, ignored_uuid)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_mutes (
                        player_uuid TEXT PRIMARY KEY,
                        muted_by TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_profiles (
                        player_uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        last_known_name_lower TEXT NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        last_server TEXT NOT NULL
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_chat_profiles_name ON chat_player_profiles(last_known_name_lower);");
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_states (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        current_channel_id VARCHAR(64) NOT NULL,
                        accepts_private_messages BOOLEAN NOT NULL DEFAULT TRUE,
                        accepts_mentions BOOLEAN NOT NULL DEFAULT TRUE,
                        social_spy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_ignored_players (
                        player_uuid VARCHAR(36) NOT NULL,
                        ignored_uuid VARCHAR(36) NOT NULL,
                        PRIMARY KEY (player_uuid, ignored_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_mutes (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        muted_by VARCHAR(64) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_profiles (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        last_known_name VARCHAR(64) NOT NULL,
                        last_known_name_lower VARCHAR(64) NOT NULL,
                        last_seen_at BIGINT NOT NULL,
                        last_server VARCHAR(64) NOT NULL,
                        INDEX idx_chat_profiles_name (last_known_name_lower)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private String stateUpsertSql() {
        if (configuration.dialect() == ChatPersistenceDialect.SQLITE) {
            return """
                INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    current_channel_id = excluded.current_channel_id,
                    accepts_private_messages = excluded.accepts_private_messages,
                    accepts_mentions = excluded.accepts_mentions,
                    social_spy_enabled = excluded.social_spy_enabled,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                current_channel_id = VALUES(current_channel_id),
                accepts_private_messages = VALUES(accepts_private_messages),
                accepts_mentions = VALUES(accepts_mentions),
                social_spy_enabled = VALUES(social_spy_enabled),
                updated_at = VALUES(updated_at)
            """;
    }

    private String muteUpsertSql() {
        if (configuration.dialect() == ChatPersistenceDialect.SQLITE) {
            return """
                INSERT INTO chat_player_mutes (player_uuid, muted_by, reason, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    muted_by = excluded.muted_by,
                    reason = excluded.reason,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                """;
        }
        return """
            INSERT INTO chat_player_mutes (player_uuid, muted_by, reason, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                muted_by = VALUES(muted_by),
                reason = VALUES(reason),
                created_at = VALUES(created_at),
                expires_at = VALUES(expires_at)
            """;
    }

    private String profileUpsertSql() {
        if (configuration.dialect() == ChatPersistenceDialect.SQLITE) {
            return """
                INSERT INTO chat_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_seen_at, last_server)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    last_known_name = excluded.last_known_name,
                    last_known_name_lower = excluded.last_known_name_lower,
                    last_seen_at = excluded.last_seen_at,
                    last_server = excluded.last_server
                """;
        }
        return """
            INSERT INTO chat_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_seen_at, last_server)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name = VALUES(last_known_name),
                last_known_name_lower = VALUES(last_known_name_lower),
                last_seen_at = VALUES(last_seen_at),
                last_server = VALUES(last_server)
            """;
    }

    private Connection connection() throws SQLException {
        return getConnection();
    }

    private static ChatPlayerProfile readProfile(ResultSet resultSet) throws SQLException {
        return new ChatPlayerProfile(
            UUID.fromString(resultSet.getString("player_uuid")),
            nullToEmpty(resultSet.getString("last_known_name")),
            Instant.ofEpochMilli(resultSet.getLong("last_seen_at")),
            nullToEmpty(resultSet.getString("last_server"))
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
