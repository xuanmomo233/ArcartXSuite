package xuanmo.arcartxsuite.chat.storage;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The bounded MySQL journal for Chat nodes that share one database. Its callers run on workers. */
public final class ChatRelayJournal {
    public static final int MAX_MESSAGES = 32_768;
    public static final int PAGE_SIZE = 32;
    private static final int PURGE_BATCH_SIZE = 256;
    private static final int UUID_BYTES = Long.BYTES * 2;
    private static final String DATABASE_TIME = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";
    private final Connections connections;
    private volatile boolean initialized;

    ChatRelayJournal(Connections connections) {
        this.connections = connections;
    }

    private synchronized void initialize() throws SQLException {
        if (initialized) return;
        try (var connection = connections.open()) {
            createTables(connection);
            initialized = true;
        }
    }

    private static void createTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_relay_sequence (
                    slot INT PRIMARY KEY,
                    high_sequence BIGINT NOT NULL,
                    retained_count INT NOT NULL,
                    next_expiry BIGINT NOT NULL
                ) ENGINE=InnoDB
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_relay_messages (
                    relay_sequence BIGINT PRIMARY KEY,
                    message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    origin_node VARCHAR(128) NOT NULL,
                    payload MEDIUMTEXT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    UNIQUE KEY chat_relay_message_id (message_id),
                    KEY chat_relay_expiry (expires_at, relay_sequence)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_relay_nodes (
                    node_id VARCHAR(255) COLLATE utf8mb4_bin PRIMARY KEY,
                    completed_sequence BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_relay_audiences (
                    node_id VARCHAR(255) COLLATE utf8mb4_bin PRIMARY KEY,
                    audience MEDIUMBLOB NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.executeUpdate("""
                INSERT IGNORE INTO chat_relay_sequence (slot, high_sequence, retained_count, next_expiry)
                VALUES (1, 0, 0, 0)
                """);
        }
    }

    /** A new node starts after all messages that committed before it first joined the journal. */
    public long openNode(String node) throws SQLException {
        String key = nodeKey(node);
        return transaction(connection -> {
            Meta meta = lockMeta(connection);
            try (var insert = connection.prepareStatement("""
                    INSERT IGNORE INTO chat_relay_nodes (node_id, completed_sequence) VALUES (?, ?)
                    """)) {
                insert.setString(1, key);
                insert.setLong(2, meta.highSequence());
                insert.executeUpdate();
            }
            try (var select = connection.prepareStatement("""
                    SELECT completed_sequence FROM chat_relay_nodes WHERE node_id = ?
                    """)) {
                select.setString(1, key);
                try (var row = select.executeQuery()) {
                    if (!row.next()) throw new SQLException("Chat relay cursor was not initialized");
                    return row.getLong(1);
                }
            }
        });
    }

    /** Reuse the message ID and expiry on retry. A successful result follows the transaction commit. */
    public boolean append(UUID messageId, String origin, String payload, long expiresAt) throws SQLException {
        Objects.requireNonNull(messageId);
        validateNode(origin);
        if (payload == null || payload.isEmpty()) return false;
        return transaction(connection -> {
            Meta meta = lockMeta(connection);
            if (expiresAt <= meta.now()) return false;
            try (var select = connection.prepareStatement("""
                    SELECT origin_node, payload, expires_at FROM chat_relay_messages WHERE message_id = ?
                    """)) {
                select.setString(1, messageId.toString());
                try (var row = select.executeQuery()) {
                    if (row.next()) {
                        return origin.equals(row.getString(1)) && payload.equals(row.getString(2)) && expiresAt == row.getLong(3);
                    }
                }
            }
            Meta active = purge(connection, meta);
            if (active.retainedCount() >= MAX_MESSAGES) return false;
            if (active.highSequence() == Long.MAX_VALUE) throw new SQLException("Chat relay sequence exhausted");
            long sequence = active.highSequence() + 1;
            try (var insert = connection.prepareStatement("""
                    INSERT INTO chat_relay_messages (relay_sequence, message_id, origin_node, payload, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setLong(1, sequence);
                insert.setString(2, messageId.toString());
                insert.setString(3, origin);
                insert.setString(4, payload);
                insert.setLong(5, expiresAt);
                insert.executeUpdate();
            }
            long nextExpiry = active.nextExpiry() == 0 ? expiresAt : Math.min(expiresAt, active.nextExpiry());
            writeMeta(connection, sequence, active.retainedCount() + 1, nextExpiry);
            return true;
        });
    }

    public List<Entry> pull(long afterSequence) throws SQLException {
        try (var connection = open();
             var select = connection.prepareStatement("""
                 SELECT relay_sequence, message_id, origin_node, payload, expires_at
                 FROM chat_relay_messages
                 WHERE relay_sequence > ? AND expires_at > %s
                 ORDER BY relay_sequence LIMIT ?
                 """.formatted(DATABASE_TIME))) {
            select.setLong(1, afterSequence);
            select.setInt(2, PAGE_SIZE);
            try (var rows = select.executeQuery()) {
                var entries = new ArrayList<Entry>();
                while (rows.next()) {
                    entries.add(new Entry(rows.getLong(1), UUID.fromString(rows.getString(2)), rows.getString(3),
                        rows.getString(4), rows.getLong(5)));
                }
                return List.copyOf(entries);
            }
        }
    }

    /** Compare-and-set keeps the completion watermark monotonic for readers and retries. */
    public boolean advance(String node, long previous, long completed) throws SQLException {
        String key = nodeKey(node);
        if (completed <= previous) return false;
        try (var connection = open()) {
            try (var update = connection.prepareStatement("""
                    UPDATE chat_relay_nodes SET completed_sequence = ?
                    WHERE node_id = ? AND completed_sequence = ?
                    AND ? <= (SELECT high_sequence FROM chat_relay_sequence WHERE slot = 1)
                    """)) {
                update.setLong(1, completed);
                update.setString(2, key);
                update.setLong(3, previous);
                update.setLong(4, completed);
                if (update.executeUpdate() == 1) return true;
            }
            // The previous attempt can have committed before the connection reported failure.
            try (var select = connection.prepareStatement("""
                    SELECT completed_sequence FROM chat_relay_nodes WHERE node_id = ?
                    """)) {
                select.setString(1, key);
                try (var row = select.executeQuery()) {
                    return row.next() && row.getLong(1) == completed;
                }
            }
        }
    }

    /** Delete only expired messages; retained rows also serve as idempotent append receipts. */
    public int purgeExpired() throws SQLException {
        return transaction(connection -> {
            Meta meta = lockMeta(connection);
            Meta retained = purge(connection, meta);
            if (retained != meta) writeMeta(connection, retained.highSequence(), retained.retainedCount(), retained.nextExpiry());
            return meta.retainedCount() - retained.retainedCount();
        });
    }

    public Set<UUID> loadAudience(String node) throws SQLException {
        try (var connection = open();
             var select = connection.prepareStatement("SELECT audience FROM chat_relay_audiences WHERE node_id = ?")) {
            select.setString(1, nodeKey(node));
            try (var row = select.executeQuery()) {
                if (!row.next()) return Set.of();
                var packed = ByteBuffer.wrap(row.getBytes(1));
                if (packed.remaining() % UUID_BYTES != 0) throw new SQLException("Invalid saved Chat relay audience");
                var audience = new LinkedHashSet<UUID>();
                while (packed.hasRemaining()) audience.add(new UUID(packed.getLong(), packed.getLong()));
                return Set.copyOf(audience);
            }
        }
    }

    public void saveAudience(String node, Set<UUID> audience) throws SQLException {
        String key = nodeKey(node);
        var packed = ByteBuffer.allocate(Math.multiplyExact(audience.size(), UUID_BYTES));
        for (UUID player : audience) {
            packed.putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits());
        }
        try (var connection = open();
             var update = connection.prepareStatement("""
                 INSERT INTO chat_relay_audiences (node_id, audience) VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE audience = VALUES(audience)
                 """)) {
            update.setString(1, key);
            update.setBytes(2, packed.array());
            update.executeUpdate();
        }
    }

    private Meta lockMeta(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var row = statement.executeQuery("""
                 SELECT high_sequence, retained_count, next_expiry, %s
                 FROM chat_relay_sequence WHERE slot = 1 FOR UPDATE
                 """.formatted(DATABASE_TIME))) {
            if (!row.next()) throw new SQLException("Chat relay sequence was not initialized");
            return new Meta(row.getLong(1), row.getInt(2), row.getLong(3), row.getLong(4));
        }
    }

    private Meta purge(Connection connection, Meta meta) throws SQLException {
        if (meta.nextExpiry() == 0 || meta.nextExpiry() > meta.now()) return meta;
        int removed;
        try (var delete = connection.prepareStatement("""
                DELETE FROM chat_relay_messages WHERE expires_at <= ?
                ORDER BY expires_at, relay_sequence LIMIT ?
                """)) {
            delete.setLong(1, meta.now());
            delete.setInt(2, PURGE_BATCH_SIZE);
            removed = delete.executeUpdate();
        }
        int retained = meta.retainedCount() - removed;
        long nextExpiry = 0;
        if (retained > 0) {
            try (var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT MIN(expires_at) FROM chat_relay_messages")) {
                if (!row.next()) throw new SQLException("Chat relay expiry cannot be read");
                nextExpiry = row.getLong(1);
                if (row.wasNull()) throw new SQLException("Chat relay count does not match stored messages");
            }
        }
        return new Meta(meta.highSequence(), retained, nextExpiry, meta.now());
    }

    private void writeMeta(Connection connection, long highSequence, int retained, long nextExpiry) throws SQLException {
        try (var update = connection.prepareStatement("""
                UPDATE chat_relay_sequence SET high_sequence = ?, retained_count = ?, next_expiry = ? WHERE slot = 1
                """)) {
            update.setLong(1, highSequence);
            update.setInt(2, retained);
            update.setLong(3, nextExpiry);
            update.executeUpdate();
        }
    }

    private <T> T transaction(Transaction<T> work) throws SQLException {
        try (var connection = open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private Connection open() throws SQLException {
        if (!initialized) initialize();
        return connections.open();
    }

    private static String nodeKey(String node) {
        validateNode(node);
        return node.toLowerCase(Locale.ROOT);
    }

    private static void validateNode(String node) {
        if (node == null || node.isBlank() || node.length() > 128 || node.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid Chat relay node");
        }
    }

    public record Entry(long sequence, UUID messageId, String origin, String payload, long expiresAt) {}

    private record Meta(long highSequence, int retainedCount, long nextExpiry, long now) {}

    @FunctionalInterface
    interface Connections {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    private interface Transaction<T> {
        T run(Connection connection) throws SQLException;
    }
}
