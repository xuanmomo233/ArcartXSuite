package xuanmo.arcartxsuite.chat.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.capability.ChatMutable.MuteInfo;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.model.ChatMessageReport;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaim;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome;
import xuanmo.arcartxsuite.chat.model.RedPacketDelivery;
import xuanmo.arcartxsuite.chat.model.RedPacketEntryRow;
import xuanmo.arcartxsuite.chat.model.RedPacketReward;
import xuanmo.arcartxsuite.chat.model.RedPacketRewardKind;
import xuanmo.arcartxsuite.chat.model.RedPacketType;

/**
 * 基于 JDBC 的聊天数据仓储实现，支持 SQLite 与 MySQL 两种后端。
 * <p>
 * 继承 {@link AbstractModuleRepository} 获得连接池管理与数据迁移能力，
 * 实现 {@link ChatRepository} 接口提供玩家状态、禁言记录与玩家档案的 CRUD 操作。
 * 建表语句根据存储模式自动适配方言。
 */
public final class JdbcChatRepository extends AbstractModuleRepository implements ChatRepository {
    private static final int IGNORE_WRITE_TIMEOUT_SECONDS = 3;
    private final ChatRelayJournal relayJournal = BuildEdition.CROSS_SERVER_INCLUDED ? new ChatRelayJournal(this::getConnection) : null;
    private final AtomicReference<HikariDataSource> guardPool = new AtomicReference<>();
    private final Object guardPoolCreation = new Object();
    private volatile boolean guardPoolClosed;
    private final java.util.concurrent.atomic.AtomicBoolean closeRequested = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.CompletableFuture<Void> closed = new java.util.concurrent.CompletableFuture<>();
    private volatile boolean ignoreGuardsInitialized;
    private static final String SELECT_PACKET = """
        SELECT p.*, s.audience_key, s.refund_on_expire
        FROM chat_red_packets p LEFT JOIN chat_red_packet_scope s ON p.packet_id = s.packet_id
        """;

    public JdbcChatRepository(DataSource dataSource, StorageDescriptor descriptor,
                              File dataFolder, Logger logger) {
        super("AXS-Chat", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    public ChatRelayJournal relayJournal() {
        return isMysql() ? relayJournal : null;
    }

    @Override
    public Optional<String> networkId() throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return Optional.empty();
        if (!isMysql()) return Optional.empty();
        try (Connection connection = getConnection();
             PreparedStatement ensure = connection.prepareStatement(
                 "INSERT IGNORE INTO chat_network_identity (slot, network_id) VALUES (1, ?)");
             PreparedStatement load = connection.prepareStatement(
                 "SELECT network_id FROM chat_network_identity WHERE slot = 1")) {
            ensure.setString(1, UUID.randomUUID().toString());
            ensure.executeUpdate();
            try (ResultSet row = load.executeQuery()) {
                if (!row.next()) throw new SQLException("Chat 网络标识缺失");
                return Optional.of(row.getString(1));
            }
        }
    }

    @Override
    protected List<String> playerDataTables() {
        // Packet claims and delivery rows are financial records and prevent duplicate payouts.
        return List.of("chat_player_states", "chat_player_mutes", "chat_player_profiles", "chat_ignored_players",
            "chat_channel_preferences", "chat_unread_mentions", "chat_moderation_log", "chat_message_reports");
    }

    @Override
    protected int onPurgePlayerData(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int count = 0;
                for (String table : playerDataTables()) {
                    String additional = switch (table) {
                        case "chat_ignored_players" -> " OR ignored_uuid = ?";
                        case "chat_message_reports" -> " OR reporter_uuid = ?";
                        default -> "";
                    };
                    try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE player_uuid = ?" + additional
                    )) {
                        delete.setString(1, playerUuid.toString());
                        if (!additional.isEmpty()) delete.setString(2, playerUuid.toString());
                        count += delete.executeUpdate();
                    }
                }
                connection.commit();
                return count;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    protected List<String> allTables() {
        return List.of("chat_player_states", "chat_player_mutes", "chat_player_profiles", "chat_ignored_players",
            "chat_red_packets", "chat_red_packet_entries", "chat_red_packet_claims", "chat_red_packet_delivery",
            "chat_red_packet_scope", "chat_channel_preferences", "chat_unread_mentions",
            "chat_moderation_log", "chat_message_reports");
    }

    @Override
    public ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException {
        return loadRouteStates(Set.of(playerUuid), "", defaultChannelId).get(playerUuid).player();
    }

    @Override
    public Map<UUID, RouteState> loadRouteStates(Set<UUID> players, String channelId, String defaultChannelId)
            throws SQLException {
        if (players.isEmpty()) return Map.of();
        try (Connection connection = getConnection()) {
            return loadRouteStates(connection, players, channelId, defaultChannelId);
        }
    }

    private Map<UUID, RouteState> loadRouteStates(Connection connection, Set<UUID> players, String channelId,
                                                   String defaultChannelId) throws SQLException {
        if (players.isEmpty()) return Map.of();
        String ids = "SELECT ? AS player_uuid" + " UNION ALL SELECT ?".repeat(players.size() - 1);
        String sql = """
            SELECT ids.player_uuid, s.current_channel_id, s.accepts_private_messages, s.accepts_mentions,
                s.social_spy_enabled, s.updated_at, i.ignored_uuid, pref.subscribed, pref.quiet
            FROM (%s) ids
            LEFT JOIN chat_player_states s ON s.player_uuid = ids.player_uuid
            LEFT JOIN chat_ignored_players i ON i.player_uuid = ids.player_uuid
            LEFT JOIN chat_channel_preferences pref ON pref.player_uuid = ids.player_uuid AND pref.channel_id = ?
            """.formatted(ids);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int position = 1;
            for (UUID player : players) statement.setString(position++, player.toString());
            statement.setString(position, channelId);
            Map<UUID, RouteState> states = new java.util.HashMap<>();
            Map<UUID, Set<UUID>> ignored = new java.util.HashMap<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    UUID id = UUID.fromString(row.getString("player_uuid"));
                    String selected = row.getString("current_channel_id");
                    Instant updated = Instant.ofEpochMilli(row.getLong("updated_at"));
                    ChatPlayerState state = selected == null || selected.isBlank()
                        ? ChatPlayerState.createDefault(id, defaultChannelId)
                        : new ChatPlayerState(id, selected, row.getBoolean("accepts_private_messages"),
                            row.getBoolean("accepts_mentions"), row.getBoolean("social_spy_enabled"), Set.of(), updated);
                    boolean subscribed = row.getBoolean("subscribed") || row.wasNull();
                    states.putIfAbsent(id, new RouteState(state, subscribed, row.getBoolean("quiet")));
                    String other = row.getString("ignored_uuid");
                    if (other != null) ignored.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(UUID.fromString(other));
                }
            }
            states.replaceAll((id, route) -> new RouteState(
                route.player().withIgnoredPlayers(ignored.getOrDefault(id, Set.of()), route.player().updatedAt()),
                route.subscribed(), route.quiet()));
            return states;
        }
    }

    @Override
    public boolean supportsIgnoreGuards() {
        return isMysql();
    }

    @Override
    public GuardedRoutes lockRouteStates(Set<UUID> actors) throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return ChatRepository.super.lockRouteStates(actors);
        if (!isMysql() || actors.isEmpty()) return ChatRepository.super.lockRouteStates(actors);
        Set<UUID> covered = Set.copyOf(actors);
        List<String> ordered = orderedActors(covered);
        Connection connection = getIgnoreGuardConnection();
        try {
            connection.setAutoCommit(true);
            provisionIgnoreGuards(connection, ordered);
            // The guards are existing primary-key rows. READ COMMITTED avoids retaining gap locks
            // on unrelated UUIDs while the worker waits for its delivery callback.
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            lockIgnoreGuards(connection, ordered, false);
            return new GuardedRoutes() {
                private boolean closed;

                @Override
                public synchronized Map<UUID, RouteState> loadRouteStates(Set<UUID> players, String channelId,
                                                                           String defaultChannelId) throws SQLException {
                    if (closed) throw new SQLException("Chat ignore guard is closed");
                    if (!covered.containsAll(players)) throw new IllegalArgumentException("Chat route actor is not guarded");
                    return JdbcChatRepository.this.loadRouteStates(connection, players, channelId, defaultChannelId);
                }

                @Override
                public synchronized void close() throws SQLException {
                    if (closed) return;
                    closed = true;
                    releaseIgnoreGuards(connection);
                }
            };
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                releaseIgnoreGuards(connection);
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    private static void provisionIgnoreGuards(Connection connection, List<String> actors) throws SQLException {
        var missing = new LinkedHashSet<>(actors);
        try (var select = connection.prepareStatement("""
                SELECT player_uuid FROM chat_ignore_guards WHERE player_uuid IN (%s)
                """.formatted(placeholders(actors.size())))) {
            bindActors(select, actors);
            try (var rows = select.executeQuery()) {
                while (rows.next()) missing.remove(rows.getString(1));
            }
        }
        if (missing.isEmpty()) return;
        // Runs before the locking transaction, so a newly created row is visible to all nodes.
        try (var insert = connection.prepareStatement("INSERT IGNORE INTO chat_ignore_guards (player_uuid) VALUES "
                + "(?)" + ",(?)".repeat(missing.size() - 1))) {
            bindActors(insert, missing);
            insert.executeUpdate();
        }
    }

    private static void lockIgnoreGuards(Connection connection, List<String> actors, boolean writer) throws SQLException {
        try (var lock = connection.prepareStatement("""
                SELECT player_uuid FROM chat_ignore_guards FORCE INDEX (PRIMARY)
                WHERE player_uuid IN (%s) ORDER BY player_uuid
                """.formatted(placeholders(actors.size())) + (writer ? "FOR UPDATE" : "LOCK IN SHARE MODE"))) {
            lock.setQueryTimeout(writer ? IGNORE_WRITE_TIMEOUT_SECONDS : 1);
            bindActors(lock, actors);
            int found = 0;
            try (var rows = lock.executeQuery()) {
                while (rows.next()) found++;
            }
            if (found != actors.size()) throw new SQLException("Chat ignore guard row is missing");
        }
    }

    private static List<String> orderedActors(Set<UUID> actors) {
        return actors.stream().map(UUID::toString).sorted().toList();
    }

    private static String placeholders(int count) {
        return "?" + ",?".repeat(count - 1);
    }

    private static void bindActors(PreparedStatement statement, Iterable<String> actors) throws SQLException {
        int index = 1;
        for (String actor : actors) statement.setString(index++, actor);
    }

    private static void releaseIgnoreGuards(Connection connection) throws SQLException {
        try (connection) {
            if (!connection.getAutoCommit()) connection.rollback();
        }
    }

    private Connection getIgnoreGuardConnection() throws SQLException {
        if (guardPoolClosed) throw new SQLException("Chat ignore storage is closed");
        Connection connection;
        if (!isMysql()) {
            connection = getConnection();
        } else {
            HikariDataSource pool = guardPool.get();
            if (pool == null) pool = openGuardPool();
            connection = pool.getConnection();
        }
        try {
            if (!ignoreGuardsInitialized) initializeIgnoreGuards(connection);
            return connection;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    private void initializeIgnoreGuards(Connection connection) throws SQLException {
        synchronized (guardPoolCreation) {
            if (guardPoolClosed) throw new SQLException("Chat ignore storage is closed");
            if (ignoreGuardsInitialized) return;
            // Even IF NOT EXISTS can wait for an exclusive metadata lock against another
            // backend's guarded delivery. Ordinary first use only checks the existing schema.
            try (var statement = connection.createStatement()) {
                try (var ignored = statement.executeQuery("SELECT player_uuid FROM chat_ignore_guards WHERE 1=0")) {
                    // A successful open verifies both table and column.
                } catch (SQLException failure) {
                    if (failure.getErrorCode() != 1146) throw failure;
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS chat_ignore_guards (
                            player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
                        ) ENGINE=InnoDB
                        """);
                }
            }
            ignoreGuardsInitialized = true;
        }
    }

    private HikariDataSource openGuardPool() throws SQLException {
        synchronized (guardPoolCreation) {
            if (guardPoolClosed) throw new SQLException("Chat ignore storage is closed");
            HikariDataSource active = guardPool.get();
            if (active != null) return active;
            var source = dataSource();
            HikariDataSource guardPoolSource = source instanceof HikariDataSource hikari ? hikari : source.unwrap(HikariDataSource.class);
            var config = new HikariConfig();
            guardPoolSource.copyStateTo(config);
            config.setPoolName(guardPoolSource.getPoolName() + "-ChatIgnore");
            config.setMinimumIdle(0);
            config.setMaximumPoolSize(2);
            config.setInitializationFailTimeout(-1);
            config.setRegisterMbeans(false);
            config.setScheduledExecutor(null);
            active = new HikariDataSource(config);
            guardPool.set(active);
            if (guardPoolClosed) {
                if (guardPool.compareAndSet(active, null)) active.close();
                throw new SQLException("Chat ignore storage is closed");
            }
            return active;
        }
    }

    @Override
    public void shutdown() {
        if (!closeRequested.compareAndSet(false, true)) return;
        guardPoolClosed = true;
        if (!isMysql()) {
            closed.complete(null);
            return;
        }
        Thread closer = new Thread(() -> {
            try {
                HikariDataSource active;
                synchronized (guardPoolCreation) { active = guardPool.getAndSet(null); }
                if (active != null) active.close();
                closed.complete(null);
            } catch (RuntimeException failure) { closed.completeExceptionally(failure); }
        }, "AXS-Chat-Ignore-Pool-Close");
        closer.setDaemon(true);
        closer.start();
    }

    public java.util.concurrent.CompletionStage<Void> closed() { return closed.copy(); }

    @Override
    public void setChannelPreference(UUID player, String channelId, boolean quietField, boolean value) throws SQLException {
        String column = quietField ? "quiet" : "subscribed";
        String update = isMysql() ? "ON DUPLICATE KEY UPDATE %1$s = VALUES(%1$s)"
            : "ON CONFLICT(player_uuid, channel_id) DO UPDATE SET %1$s = excluded.%1$s";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO chat_channel_preferences (player_uuid, channel_id, subscribed, quiet) VALUES (?, ?, ?, ?) "
                     + update.formatted(column)
             )) {
            statement.setString(1, player.toString());
            statement.setString(2, channelId);
            statement.setBoolean(3, quietField || value);
            statement.setBoolean(4, quietField && value);
            statement.executeUpdate();
        }
    }

    @Override
    public void addUnreadMention(UUID player) throws SQLException {
        String update = isMysql() ? "ON DUPLICATE KEY UPDATE unread = LEAST(9999, unread + 1)"
            : "ON CONFLICT(player_uuid) DO UPDATE SET unread = MIN(9999, unread + 1)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO chat_unread_mentions (player_uuid, unread) VALUES (?, 1) " + update
             )) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public int takeUnreadMentions(UUID player) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                     "SELECT unread FROM chat_unread_mentions WHERE player_uuid = ?" + (isMysql() ? " FOR UPDATE" : ""));
                 PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM chat_unread_mentions WHERE player_uuid = ?"
                 )) {
                select.setString(1, player.toString());
                int unread;
                try (ResultSet row = select.executeQuery()) {
                    unread = row.next() ? row.getInt(1) : 0;
                }
                if (unread != 0) {
                    delete.setString(1, player.toString());
                    delete.executeUpdate();
                }
                connection.commit();
                return unread;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void saveState(ChatPlayerState state, StateField field) throws SQLException {
        String column = switch (field) {
            case CHANNEL -> "current_channel_id";
            case PRIVATE_MESSAGES -> "accepts_private_messages";
            case MENTIONS -> "accepts_mentions";
            case SOCIAL_SPY -> "social_spy_enabled";
        };
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(stateUpsertSql(column))) {
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
    public boolean setIgnored(UUID playerUuid, UUID ignoredUuid, boolean ignored) throws SQLException {
        try (Connection connection = isMysql() ? getIgnoreGuardConnection() : getConnection()) {
            if (!isMysql()) return setIgnored(connection, playerUuid, ignoredUuid, ignored);
            List<String> actor = List.of(playerUuid.toString());
            connection.setAutoCommit(true);
            provisionIgnoreGuards(connection, actor);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                lockIgnoreGuards(connection, actor, true);
                boolean changed = setIgnored(connection, playerUuid, ignoredUuid, ignored);
                connection.commit();
                return changed;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private boolean setIgnored(Connection connection, UUID playerUuid, UUID ignoredUuid, boolean ignored)
            throws SQLException {
        String sql = ignored
            ? (isMysql() ? "INSERT IGNORE" : "INSERT OR IGNORE")
                + " INTO chat_ignored_players (player_uuid, ignored_uuid) VALUES (?, ?)"
            : "DELETE FROM chat_ignored_players WHERE player_uuid = ? AND ignored_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (isMysql()) statement.setQueryTimeout(IGNORE_WRITE_TIMEOUT_SECONDS);
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ignoredUuid.toString());
            return statement.executeUpdate() != 0;
        }
    }

    @Override
    public Optional<ChatMuteRecord> loadMute(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, muted_by, reason, created_at, expires_at "
                     + "FROM chat_player_mutes WHERE player_uuid = ? LIMIT 1"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMute(resultSet));
            }
        }
    }

    @Override
    public List<MuteInfo> loadActiveMutes(Instant now, int limit, int offset) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT m.player_uuid, m.muted_by, m.reason, m.created_at, m.expires_at, p.last_known_name
                 FROM chat_player_mutes m LEFT JOIN chat_player_profiles p ON m.player_uuid = p.player_uuid
                 WHERE m.expires_at IS NULL OR m.expires_at > ?
                 ORDER BY m.player_uuid LIMIT ? OFFSET ?
                 """
             )) {
            statement.setLong(1, now.toEpochMilli());
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet rows = statement.executeQuery()) {
                List<MuteInfo> result = new ArrayList<>();
                while (rows.next()) {
                    ChatMuteRecord mute = readMute(rows);
                    result.add(new MuteInfo(mute.playerUuid(), rows.getString("last_known_name"), mute.mutedBy(),
                        mute.reason(), mute.createdAt(), mute.expiresAt()));
                }
                return List.copyOf(result);
            }
        }
    }

    private static ChatMuteRecord readMute(ResultSet row) throws SQLException {
        long expiration = row.getLong("expires_at");
        Instant expiresAt = row.wasNull() ? null : Instant.ofEpochMilli(expiration);
        return new ChatMuteRecord(UUID.fromString(row.getString("player_uuid")),
            nullToEmpty(row.getString("muted_by")), nullToEmpty(row.getString("reason")),
            Instant.ofEpochMilli(row.getLong("created_at")), expiresAt);
    }

    @Override
    public void deleteExpiredMutes(Instant now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM chat_player_mutes WHERE expires_at IS NOT NULL AND expires_at <= ?"
             )) {
            statement.setLong(1, now.toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveMute(ChatMuteRecord mute, ChatModerationReceipt receipt) throws SQLException {
        changeMute(mute.playerUuid(), mute, receipt);
    }

    @Override
    public void deleteMute(UUID playerUuid, ChatModerationReceipt receipt) throws SQLException {
        changeMute(playerUuid, null, receipt);
    }

    private void changeMute(UUID player, ChatMuteRecord mute, ChatModerationReceipt receipt) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                mute == null ? "DELETE FROM chat_player_mutes WHERE player_uuid = ?" : muteUpsertSql()
            )) {
                statement.setString(1, player.toString());
                if (mute != null) {
                    statement.setString(2, mute.mutedBy());
                    statement.setString(3, mute.reason());
                    statement.setLong(4, mute.createdAt().toEpochMilli());
                    setInstant(statement, 5, mute.expiresAt());
                }
                statement.executeUpdate();
                try (PreparedStatement log = connection.prepareStatement("""
                    INSERT INTO chat_moderation_log
                        (id, player_uuid, player_name, action, operator_name, reason, origin_node, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    log.setString(1, receipt.id().toString());
                    log.setString(2, player.toString());
                    log.setString(3, receipt.playerName());
                    log.setString(4, receipt.action());
                    log.setString(5, receipt.operator());
                    log.setString(6, receipt.reason());
                    log.setString(7, receipt.node());
                    log.setLong(8, receipt.createdAt().toEpochMilli());
                    setInstant(log, 9, receipt.expiresAt());
                    log.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value.toEpochMilli());
    }

    private static ChatModerationReceipt readReceipt(ResultSet row) throws SQLException {
        long expires = row.getLong("expires_at");
        Instant until = row.wasNull() ? null : Instant.ofEpochMilli(expires);
        return new ChatModerationReceipt(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("player_uuid")),
            row.getString("player_name"), row.getString("action"), row.getString("operator_name"), row.getString("reason"),
            row.getString("origin_node"), Instant.ofEpochMilli(row.getLong("created_at")), until);
    }

    @Override
    public List<ChatModerationReceipt> moderationHistory(String playerName, int limit) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT * FROM chat_moderation_log WHERE LOWER(player_name) = ?
                 ORDER BY created_at DESC, id DESC LIMIT ?
                 """)) {
            statement.setString(1, playerName.toLowerCase(java.util.Locale.ROOT));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<ChatModerationReceipt> result = new ArrayList<>();
                while (rows.next()) result.add(readReceipt(rows));
                return result;
            }
        }
    }

    @Override
    public Optional<ChatModerationReceipt> findModerationReceipt(UUID id) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM chat_moderation_log WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readReceipt(row)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean saveReport(ChatMessageReport report) throws SQLException {
        String insert = isMysql() ? "INSERT IGNORE" : "INSERT OR IGNORE";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(insert + """
                  INTO chat_message_reports
                    (id, reporter_uuid, player_uuid, player_name, message_id, channel_id, message_text, reason, created_at)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """)) {
            statement.setString(1, report.id().toString());
            statement.setString(2, report.reporterId().toString());
            statement.setString(3, report.senderId().toString());
            statement.setString(4, report.senderName());
            statement.setString(5, report.messageId());
            statement.setString(6, report.channel());
            statement.setString(7, report.text());
            statement.setString(8, report.reason());
            statement.setLong(9, report.createdAt().toEpochMilli());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<ChatMessageReport> loadOpenReports(int limit) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT * FROM chat_message_reports WHERE reviewed_at IS NULL ORDER BY created_at, id LIMIT ?
                 """)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<ChatMessageReport> result = new ArrayList<>();
                while (rows.next()) result.add(readReport(rows));
                return result;
            }
        }
    }

    @Override
    public Optional<ChatMessageReport> findReport(UUID id) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM chat_message_reports WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readReport(row)) : Optional.empty();
            }
        }
    }

    private static ChatMessageReport readReport(ResultSet row) throws SQLException {
        return new ChatMessageReport(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("reporter_uuid")),
            UUID.fromString(row.getString("player_uuid")), row.getString("player_name"), row.getString("message_id"),
            row.getString("channel_id"), row.getString("message_text"), row.getString("reason"),
            Instant.ofEpochMilli(row.getLong("created_at")));
    }

    @Override
    public boolean closeReport(UUID id, String operator) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE chat_message_reports SET reviewed_at = ?, reviewed_by = ? WHERE id = ? AND reviewed_at IS NULL
                 """)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, operator);
            statement.setString(3, id.toString());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public void upsertProfile(ChatPlayerProfile profile) throws SQLException {
        try (Connection connection = getConnection();
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
        try (Connection connection = getConnection();
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
        try (Connection connection = getConnection();
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

    // ── 红包 ─────────────────────────────────────────────

    @Override
    public void insertRedPacket(RedPacket packet, List<RedPacketEntryRow> entries) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chat_red_packets (packet_id, packet_type, sender_uuid, sender_name, sender_display, channel_id,"
                        + " currency_id, total_amount, per_amounts, total_count, claimed_count, remaining_amount, wish, preset_id,"
                        + " admin_packet, status, created_at, expires_at, origin_node)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    statement.setString(1, packet.id());
                    statement.setString(2, packet.type().name());
                    statement.setString(3, nullToEmpty(packet.senderUuid()));
                    statement.setString(4, nullToEmpty(packet.senderName()));
                    statement.setString(5, nullToEmpty(packet.senderDisplay()));
                    statement.setString(6, nullToEmpty(packet.channelId()));
                    statement.setString(7, nullToEmpty(packet.currencyId()));
                    statement.setString(8, packet.totalAmount() == null ? "0" : packet.totalAmount().toPlainString());
                    statement.setString(9, encodeAmounts(packet.amounts()));
                    statement.setInt(10, packet.totalCount());
                    statement.setInt(11, packet.claimedCount());
                    statement.setString(12, packet.remainingAmount() == null ? "0" : packet.remainingAmount().toPlainString());
                    statement.setString(13, nullToEmpty(packet.wish()));
                    statement.setString(14, nullToEmpty(packet.presetId()));
                    statement.setBoolean(15, packet.admin());
                    statement.setString(16, packet.status());
                    statement.setLong(17, packet.createdAt().toEpochMilli());
                    statement.setLong(18, packet.expiresAt().toEpochMilli());
                    statement.setString(19, nullToEmpty(packet.originNode()));
                    statement.executeUpdate();
                }
                try (PreparedStatement scope = connection.prepareStatement(
                    "INSERT INTO chat_red_packet_scope (packet_id, audience_key, refund_on_expire) VALUES (?, ?, ?)"
                )) {
                    scope.setString(1, packet.id());
                    scope.setString(2, packet.audienceKey());
                    scope.setBoolean(3, packet.refundOnExpire());
                    scope.executeUpdate();
                }
                if (entries != null && !entries.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO chat_red_packet_entries (packet_id, entry_index, reward_type, currency_id, amount,"
                            + " item_data, command_text, description, weight, remaining) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    )) {
                        for (RedPacketEntryRow entry : entries) {
                            RedPacketReward reward = entry.reward();
                            statement.setString(1, packet.id());
                            statement.setInt(2, entry.entryIndex());
                            statement.setString(3, reward.kind().name());
                            statement.setString(4, nullToEmpty(reward.currencyId()));
                            statement.setString(5, reward.amount() == null ? "" : reward.amount().toPlainString());
                            statement.setString(6, nullToEmpty(reward.itemData()));
                            statement.setString(7, nullToEmpty(reward.command()));
                            statement.setString(8, nullToEmpty(reward.description()));
                            statement.setInt(9, entry.weight());
                            statement.setInt(10, entry.remaining());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
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
    public Optional<RedPacket> findRedPacket(String packetId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 SELECT_PACKET + " WHERE p.packet_id = ? LIMIT 1"
             )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readPacket(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<RedPacket> loadActiveRedPackets() throws SQLException {
        List<RedPacket> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 SELECT_PACKET + " WHERE p.status = 'active_v2' ORDER BY p.created_at ASC"
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(readPacket(resultSet));
            }
        }
        return result;
    }

    @Override
    public RedPacketClaimOutcome claimRedPacket(RedPacket authorizedPacket, UUID playerUuid, String playerName,
                                               boolean senderCanClaim) throws SQLException {
        String packetId = authorizedPacket.id();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                RedPacket packet = selectPacketForUpdate(connection, packetId);
                if (packet == null) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.NOT_FOUND);
                }
                if (!packet.createdAt().equals(authorizedPacket.createdAt())
                        || !packet.channelId().equals(authorizedPacket.channelId())
                        || !packet.originNode().equals(authorizedPacket.originNode())
                        || !packet.audienceKey().equals(authorizedPacket.audienceKey())
                        || !java.util.Objects.equals(packet.senderUuid(), authorizedPacket.senderUuid())
                        || isIgnored(connection, playerUuid, packet.senderUuid())) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.NOT_ELIGIBLE);
                }
                Instant now = Instant.now();
                if (packet.expired(now)) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXPIRED, packet);
                }
                if (!RedPacket.STATUS_ACTIVE.equals(packet.status()) || packet.claimedCount() >= packet.totalCount()) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                }
                if (!senderCanClaim && packet.senderUuid() != null
                        && packet.senderUuid().equals(playerUuid.toString())) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.SENDER_DENIED, packet);
                }
                try (PreparedStatement dupCheck = connection.prepareStatement(
                    "SELECT 1 FROM chat_red_packet_claims WHERE packet_id = ? AND player_uuid = ? LIMIT 1"
                )) {
                    dupCheck.setString(1, packetId);
                    dupCheck.setString(2, playerUuid.toString());
                    try (ResultSet rs = dupCheck.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.ALREADY_CLAIMED, packet);
                        }
                    }
                }

                int slotIndex = packet.claimedCount();
                RedPacketReward reward;
                BigDecimal currencyAmount = null;
                boolean poolDrained = false;
                if (packet.type() == RedPacketType.POOL) {
                    List<RedPacketEntryRow> entries = selectPacketEntries(connection, packetId);
                    RedPacketEntryRow picked = pickWeighted(entries);
                    if (picked == null) {
                        markPacketStatus(connection, packetId, RedPacket.STATUS_EXHAUSTED);
                        connection.commit();
                        return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                    }
                    if (picked.remaining() > 0) {
                        try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE chat_red_packet_entries SET remaining = remaining - 1 WHERE packet_id = ? AND entry_index = ?"
                        )) {
                            update.setString(1, packetId);
                            update.setInt(2, picked.entryIndex());
                            update.executeUpdate();
                        }
                    }
                    // 扣减后检查池是否仍有可抽奖项
                    List<RedPacketEntryRow> after = selectPacketEntries(connection, packetId);
                    poolDrained = pickWeighted(after) == null;
                    reward = picked.reward();
                    if (reward.kind() == RedPacketRewardKind.CURRENCY && reward.amount() != null) {
                        currencyAmount = reward.amount();
                    }
                } else {
                    List<BigDecimal> amounts = packet.amounts();
                    if (slotIndex >= amounts.size()) {
                        connection.rollback();
                        return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                    }
                    currencyAmount = amounts.get(slotIndex);
                    reward = RedPacketReward.currency(packet.currencyId(), currencyAmount,
                        currencyAmount.toPlainString() + " " + packet.currencyId());
                }

                String rewardDesc = reward.description().isBlank()
                    ? reward.kind().name().toLowerCase(java.util.Locale.ROOT)
                    : reward.description();
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chat_red_packet_claims (packet_id, player_uuid, player_name, reward_desc, amount, claimed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, packetId);
                    insert.setString(2, playerUuid.toString());
                    insert.setString(3, nullToEmpty(playerName));
                    insert.setString(4, rewardDesc);
                    insert.setString(5, currencyAmount == null ? null : currencyAmount.toPlainString());
                    insert.setLong(6, now.toEpochMilli());
                    insert.executeUpdate();
                }

                int newClaimed = packet.claimedCount() + 1;
                BigDecimal newRemaining = packet.remainingAmount()
                    .subtract(currencyAmount == null ? BigDecimal.ZERO : currencyAmount);
                String newStatus = (newClaimed >= packet.totalCount() || poolDrained)
                    ? RedPacket.STATUS_EXHAUSTED : RedPacket.STATUS_ACTIVE;
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE chat_red_packets SET claimed_count = ?, remaining_amount = ?, status = ? WHERE packet_id = ?"
                )) {
                    update.setInt(1, newClaimed);
                    update.setString(2, newRemaining.toPlainString());
                    update.setString(3, newStatus);
                    update.setString(4, packetId);
                    update.executeUpdate();
                }
                insertDelivery(connection, RedPacketDelivery.claim(packetId, playerUuid, reward, now));
                connection.commit();

                RedPacket updated = new RedPacket(
                    packet.id(), packet.type(), packet.senderUuid(), packet.senderName(), packet.senderDisplay(),
                    packet.channelId(), packet.currencyId(), packet.totalAmount(), newRemaining, packet.amounts(),
                    packet.totalCount(), newClaimed, packet.wish(), packet.presetId(), packet.admin(),
                    newStatus, packet.createdAt(), packet.expiresAt(), packet.originNode(), packet.audienceKey(),
                    packet.refundOnExpire()
                );
                return RedPacketClaimOutcome.success(updated, reward, slotIndex, currencyAmount);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean expireRedPacket(String packetId) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                RedPacket packet = selectPacketForUpdate(connection, packetId);
                Instant now = Instant.now();
                if (packet == null || !packet.expired(now)) {
                    connection.rollback();
                    return false;
                }
                if (!RedPacket.STATUS_ACTIVE.equals(packet.status())) {
                    connection.rollback();
                    return false;
                }
                if (packet.refundOnExpire() && !packet.admin() && packet.type() != RedPacketType.POOL
                        && packet.remainingAmount().signum() > 0 && packet.senderUuid() != null) {
                    insertDelivery(connection, RedPacketDelivery.refund(packet, now));
                }
                markPacketStatus(connection, packetId, RedPacket.STATUS_EXPIRED);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean completeDebit(String packetId, boolean charged) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE chat_red_packets SET status = ? WHERE packet_id = ? AND status = 'debiting'"
             )) {
            statement.setString(1, charged ? RedPacket.STATUS_ACTIVE : RedPacket.STATUS_CANCELLED);
            statement.setString(2, packetId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<RedPacket> reconcileDebit(String packetId, boolean charged, Instant before) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                RedPacket packet = selectPacketForUpdate(connection, packetId);
                if (packet == null || !RedPacket.STATUS_DEBITING.equals(packet.status()) || packet.createdAt().isAfter(before)) {
                    connection.rollback();
                    return Optional.empty();
                }
                Instant now = Instant.now();
                String next = charged ? RedPacket.STATUS_ACTIVE : RedPacket.STATUS_CANCELLED;
                if (charged && packet.expired(now)) {
                    if (packet.refundOnExpire()) insertDelivery(connection, RedPacketDelivery.refund(packet, now));
                    next = RedPacket.STATUS_EXPIRED;
                }
                markPacketStatus(connection, packetId, next);
                connection.commit();
                return Optional.of(packet.withStatus(next));
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<RedPacket> loadUncertainDebits(Instant before) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 SELECT_PACKET + " WHERE p.status = 'debiting' AND p.created_at <= ? ORDER BY p.created_at LIMIT 100"
             )) {
            statement.setLong(1, before.toEpochMilli());
            try (ResultSet rows = statement.executeQuery()) {
                List<RedPacket> result = new ArrayList<>();
                while (rows.next()) result.add(readPacket(rows));
                return result;
            }
        }
    }

    private boolean isIgnored(Connection connection, UUID playerId, String senderId) throws SQLException {
        if (senderId == null || senderId.equals(playerId.toString())) return false;
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM chat_ignored_players WHERE (player_uuid = ? AND ignored_uuid = ?)"
                + " OR (player_uuid = ? AND ignored_uuid = ?)"
        )) {
            statement.setString(1, playerId.toString());
            statement.setString(2, senderId);
            statement.setString(3, senderId);
            statement.setString(4, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void insertDelivery(Connection connection, RedPacketDelivery delivery) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO chat_red_packet_delivery (delivery_id, packet_id, player_uuid, reward_type,
                currency_id, amount, item_data, command_text, description, is_refund, status, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            RedPacketReward reward = delivery.reward();
            statement.setString(1, delivery.id());
            statement.setString(2, delivery.packetId());
            statement.setString(3, delivery.playerId().toString());
            statement.setString(4, reward.kind().name());
            statement.setString(5, reward.currencyId());
            statement.setString(6, reward.amount() == null ? "" : reward.amount().toPlainString());
            statement.setString(7, reward.itemData());
            statement.setString(8, reward.command());
            statement.setString(9, reward.description());
            statement.setBoolean(10, delivery.refund());
            statement.setString(11, delivery.status().name());
            statement.setLong(12, delivery.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public List<RedPacketDelivery> loadReadyDeliveries(Set<UUID> players, int limit) throws SQLException {
        if (players.isEmpty()) return List.of();
        String parameters = String.join(",", java.util.Collections.nCopies(players.size(), "?"));
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM chat_red_packet_delivery WHERE status = 'READY' AND player_uuid IN ("
                     + parameters + ") ORDER BY updated_at, delivery_id LIMIT ?"
             )) {
            int position = 1;
            for (UUID player : players) statement.setString(position++, player.toString());
            statement.setInt(position, limit);
            return readDeliveries(statement);
        }
    }

    @Override
    public List<RedPacketDelivery> loadUncertainDeliveries(Instant before) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT * FROM chat_red_packet_delivery
                 WHERE status = 'REVIEW' OR (status = 'EXECUTING' AND updated_at <= ?)
                 ORDER BY updated_at, delivery_id LIMIT 100
                 """)) {
            statement.setLong(1, before.toEpochMilli());
            return readDeliveries(statement);
        }
    }

    private List<RedPacketDelivery> readDeliveries(PreparedStatement statement) throws SQLException {
        List<RedPacketDelivery> result = new ArrayList<>();
        try (ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                RedPacketReward reward = new RedPacketReward(RedPacketRewardKind.valueOf(row.getString("reward_type")),
                    row.getString("currency_id"), parseAmountOrNull(row.getString("amount")),
                    row.getString("item_data"), row.getString("command_text"), row.getString("description"));
                result.add(new RedPacketDelivery(row.getString("delivery_id"), row.getString("packet_id"),
                    UUID.fromString(row.getString("player_uuid")), reward, row.getBoolean("is_refund"),
                    RedPacketDelivery.Status.valueOf(row.getString("status")), Instant.ofEpochMilli(row.getLong("updated_at"))));
            }
        }
        return result;
    }

    @Override
    public boolean transitionDelivery(RedPacketDelivery expected, RedPacketDelivery.Status next, Instant now)
            throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE chat_red_packet_delivery SET status = ?, updated_at = ?
                 WHERE delivery_id = ? AND status = ? AND updated_at = ?
                 """)) {
            statement.setString(1, next.name());
            statement.setLong(2, now.toEpochMilli());
            statement.setString(3, expected.id());
            statement.setString(4, expected.status().name());
            statement.setLong(5, expected.updatedAt().toEpochMilli());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean reconcileDelivery(String id, Instant before, RedPacketDelivery.Status next) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE chat_red_packet_delivery
                 SET status = ?, updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END
                 WHERE delivery_id = ? AND (status = 'REVIEW' OR (status = 'EXECUTING' AND updated_at <= ?))
                 """)) {
            statement.setString(1, next.name());
            long now = System.currentTimeMillis();
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, id);
            statement.setLong(5, before.toEpochMilli());
            return statement.executeUpdate() != 0;
        }
    }

    @Override
    public List<RedPacketClaim> listRedPacketClaims(String packetId) throws SQLException {
        List<RedPacketClaim> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT packet_id, player_uuid, player_name, reward_desc, amount, claimed_at"
                     + " FROM chat_red_packet_claims WHERE packet_id = ? ORDER BY claimed_at ASC"
             )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new RedPacketClaim(
                        resultSet.getString("packet_id"),
                        resultSet.getString("player_uuid"),
                        nullToEmpty(resultSet.getString("player_name")),
                        nullToEmpty(resultSet.getString("reward_desc")),
                        parseAmountOrNull(resultSet.getString("amount")),
                        Instant.ofEpochMilli(resultSet.getLong("claimed_at"))
                    ));
                }
            }
        }
        return result;
    }

    /** 读取并锁定红包行（MySQL 使用 FOR UPDATE，SQLite 由单写连接串行化）。 */
    private RedPacket selectPacketForUpdate(Connection connection, String packetId) throws SQLException {
        String sql = SELECT_PACKET + " WHERE p.packet_id = ?" + (isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readPacket(resultSet) : null;
            }
        }
    }

    private List<RedPacketEntryRow> selectPacketEntries(Connection connection, String packetId) throws SQLException {
        List<RedPacketEntryRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT entry_index, reward_type, currency_id, amount, item_data, command_text, description, weight, remaining"
                + " FROM chat_red_packet_entries WHERE packet_id = ? ORDER BY entry_index ASC"
        )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    RedPacketRewardKind kind = RedPacketRewardKind.parse(resultSet.getString("reward_type"));
                    if (kind == null) {
                        continue;
                    }
                    RedPacketReward reward = new RedPacketReward(
                        kind,
                        nullToEmpty(resultSet.getString("currency_id")),
                        parseAmountOrNull(resultSet.getString("amount")),
                        nullToEmpty(resultSet.getString("item_data")),
                        nullToEmpty(resultSet.getString("command_text")),
                        nullToEmpty(resultSet.getString("description"))
                    );
                    result.add(new RedPacketEntryRow(
                        resultSet.getInt("entry_index"), reward,
                        resultSet.getInt("weight"), resultSet.getInt("remaining")
                    ));
                }
            }
        }
        return result;
    }

    /** 在可抽取条目（remaining != 0）中按权重随机选取。 */
    private static RedPacketEntryRow pickWeighted(List<RedPacketEntryRow> entries) {
        int totalWeight = 0;
        for (RedPacketEntryRow entry : entries) {
            if (entry.drawable()) {
                totalWeight += Math.max(1, entry.weight());
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (RedPacketEntryRow entry : entries) {
            if (!entry.drawable()) {
                continue;
            }
            roll -= Math.max(1, entry.weight());
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }

    private void markPacketStatus(Connection connection, String packetId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE chat_red_packets SET status = ? WHERE packet_id = ?"
        )) {
            statement.setString(1, status);
            statement.setString(2, packetId);
            statement.executeUpdate();
        }
    }

    private static RedPacket readPacket(ResultSet resultSet) throws SQLException {
        String senderUuid = nullToEmpty(resultSet.getString("sender_uuid"));
        RedPacketType type = RedPacketType.parse(resultSet.getString("packet_type"));
        return new RedPacket(
            resultSet.getString("packet_id"),
            type == null ? RedPacketType.NORMAL : type,
            senderUuid.isBlank() ? null : senderUuid,
            nullToEmpty(resultSet.getString("sender_name")),
            nullToEmpty(resultSet.getString("sender_display")),
            nullToEmpty(resultSet.getString("channel_id")),
            nullToEmpty(resultSet.getString("currency_id")),
            parseAmount(resultSet.getString("total_amount")),
            parseAmount(resultSet.getString("remaining_amount")),
            decodeAmounts(resultSet.getString("per_amounts")),
            resultSet.getInt("total_count"),
            resultSet.getInt("claimed_count"),
            nullToEmpty(resultSet.getString("wish")),
            nullToEmpty(resultSet.getString("preset_id")),
            resultSet.getBoolean("admin_packet"),
            nullToEmpty(resultSet.getString("status")),
            Instant.ofEpochMilli(resultSet.getLong("created_at")),
            Instant.ofEpochMilli(resultSet.getLong("expires_at")),
            nullToEmpty(resultSet.getString("origin_node")),
            nullToEmpty(resultSet.getString("audience_key")),
            resultSet.getBoolean("refund_on_expire")
        );
    }

    /** 金额列以纯文本存储（VARCHAR），读取时还原为 BigDecimal，空串/非法值回退 0。 */
    private static BigDecimal parseAmount(String raw) {
        BigDecimal value = parseAmountOrNull(raw);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 解析可空金额列，空串/NULL/非法值返回 {@code null}。 */
    private static BigDecimal parseAmountOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String encodeAmounts(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < amounts.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(amounts.get(i).toPlainString());
        }
        return builder.toString();
    }

    private static List<BigDecimal> decodeAmounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<BigDecimal> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                result.add(new BigDecimal(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            String engine = isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
            if (BuildEdition.CROSS_SERVER_INCLUDED) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_network_identity (
                        slot INTEGER PRIMARY KEY,
                        network_id VARCHAR(36) NOT NULL
                    )
                    """ + engine);
            }
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_moderation_log (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    action VARCHAR(16) NOT NULL,
                    operator_name VARCHAR(64) NOT NULL,
                    reason TEXT NOT NULL,
                    origin_node VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NULL
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_message_reports (
                    id VARCHAR(36) PRIMARY KEY,
                    reporter_uuid VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    message_id VARCHAR(36) NOT NULL,
                    channel_id VARCHAR(64) NOT NULL,
                    message_text MEDIUMTEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    reviewed_by VARCHAR(64) NULL,
                    reviewed_at BIGINT NULL,
                    UNIQUE (reporter_uuid, message_id)
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_channel_preferences (
                    player_uuid VARCHAR(36) NOT NULL,
                    channel_id VARCHAR(64) NOT NULL,
                    subscribed BOOLEAN NOT NULL,
                    quiet BOOLEAN NOT NULL,
                    PRIMARY KEY (player_uuid, channel_id)
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_unread_mentions (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    unread INTEGER NOT NULL
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_red_packet_scope (
                    packet_id VARCHAR(16) PRIMARY KEY,
                    audience_key TEXT NOT NULL,
                    refund_on_expire BOOLEAN NOT NULL
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_red_packet_delivery (
                    delivery_id VARCHAR(80) PRIMARY KEY,
                    packet_id VARCHAR(16) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    reward_type VARCHAR(16) NOT NULL,
                    currency_id VARCHAR(64) NOT NULL,
                    amount VARCHAR(64) NOT NULL,
                    item_data MEDIUMTEXT NOT NULL,
                    command_text MEDIUMTEXT NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    is_refund BOOLEAN NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            if (!isMysql()) {
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
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packets (
                        packet_id TEXT PRIMARY KEY,
                        packet_type TEXT NOT NULL,
                        sender_uuid TEXT NOT NULL,
                        sender_name TEXT NOT NULL,
                        sender_display TEXT NOT NULL,
                        channel_id TEXT NOT NULL,
                        currency_id TEXT NOT NULL,
                        total_amount TEXT NOT NULL DEFAULT '0',
                        per_amounts TEXT NOT NULL,
                        total_count INTEGER NOT NULL,
                        claimed_count INTEGER NOT NULL DEFAULT 0,
                        remaining_amount TEXT NOT NULL DEFAULT '0',
                        wish TEXT NOT NULL,
                        preset_id TEXT NOT NULL,
                        admin_packet INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        origin_node TEXT NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_entries (
                        packet_id TEXT NOT NULL,
                        entry_index INTEGER NOT NULL,
                        reward_type TEXT NOT NULL,
                        currency_id TEXT NOT NULL,
                        amount TEXT NOT NULL DEFAULT '',
                        item_data TEXT NOT NULL,
                        command_text TEXT NOT NULL,
                        description TEXT NOT NULL,
                        weight INTEGER NOT NULL,
                        remaining INTEGER NOT NULL,
                        PRIMARY KEY (packet_id, entry_index)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_claims (
                        packet_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        reward_desc TEXT NOT NULL,
                        amount TEXT,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY (packet_id, player_uuid)
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
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packets (
                        packet_id VARCHAR(16) PRIMARY KEY,
                        packet_type VARCHAR(16) NOT NULL,
                        sender_uuid VARCHAR(36) NOT NULL,
                        sender_name VARCHAR(64) NOT NULL,
                        sender_display VARCHAR(191) NOT NULL,
                        channel_id VARCHAR(64) NOT NULL,
                        currency_id VARCHAR(64) NOT NULL,
                        total_amount VARCHAR(64) NOT NULL DEFAULT '0',
                        per_amounts MEDIUMTEXT NOT NULL,
                        total_count INT NOT NULL,
                        claimed_count INT NOT NULL DEFAULT 0,
                        remaining_amount VARCHAR(64) NOT NULL DEFAULT '0',
                        wish VARCHAR(255) NOT NULL,
                        preset_id VARCHAR(64) NOT NULL,
                        admin_packet BOOLEAN NOT NULL DEFAULT FALSE,
                        status VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        origin_node VARCHAR(64) NOT NULL,
                        INDEX idx_rp_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_entries (
                        packet_id VARCHAR(16) NOT NULL,
                        entry_index INT NOT NULL,
                        reward_type VARCHAR(16) NOT NULL,
                        currency_id VARCHAR(64) NOT NULL,
                        amount VARCHAR(64) NOT NULL DEFAULT '',
                        item_data MEDIUMTEXT NOT NULL,
                        command_text MEDIUMTEXT NOT NULL,
                        description VARCHAR(255) NOT NULL,
                        weight INT NOT NULL,
                        remaining INT NOT NULL,
                        PRIMARY KEY (packet_id, entry_index)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_claims (
                        packet_id VARCHAR(16) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        reward_desc VARCHAR(255) NOT NULL,
                        amount VARCHAR(64) NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (packet_id, player_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private String stateUpsertSql(String column) {
        if (!isMysql()) {
            return """
                INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    %1$s = excluded.%1$s,
                    updated_at = excluded.updated_at
                """.formatted(column);
        }
        return """
            INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                %1$s = VALUES(%1$s),
                updated_at = VALUES(updated_at)
            """.formatted(column);
    }

    private String muteUpsertSql() {
        if (!isMysql()) {
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
        if (!isMysql()) {
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
