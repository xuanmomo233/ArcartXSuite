package xuanmo.arcartxsuite.crossserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Each inbound worker owns its connection to the shared inbox and replay database. */
final class CrossServerReplayStore implements AutoCloseable {
    private final Path database;
    private Connection connection;
    private long nextCleanup;

    CrossServerReplayStore(Path database) {
        this.database = database;
    }

    boolean[] claim(List<Claim> claims, long now, long maxAgeMs) throws IOException, SQLException {
        boolean[] inserted = new boolean[claims.size()];
        if (claims.isEmpty()) return inserted;
        try {
            Connection db = open();
            boolean cleanupDue = cleanup(db, now);
            try (var insert = db.prepareStatement("""
                    INSERT OR IGNORE INTO cross_server_replay (message_id, expires_at)
                    SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM cross_server_inbox WHERE message_id = ?)
                    """)) {
                for (int index = 0; index < claims.size(); index++) {
                    Claim claim = claims.get(index);
                    insert.setString(1, claim.messageId());
                    insert.setLong(2, expiry(claim.timestamp(), maxAgeMs));
                    insert.setString(3, claim.messageId());
                    inserted[index] = insert.executeUpdate() == 1;
                }
            }
            db.commit();
            if (cleanupDue) nextCleanup = now + 1_000;
            return inserted;
        } catch (SQLException failure) {
            try { close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    String cursor(String scope) throws IOException, SQLException {
        Connection db = open();
        String cursor;
        try (var select = db.prepareStatement("SELECT cursor FROM cross_server_cursor WHERE scope = ?")) {
            select.setString(1, scope);
            try (var result = select.executeQuery()) {
                cursor = result.next() ? result.getString(1) : CrossServerRedisStream.START;
            }
        }
        db.commit();
        return cursor;
    }

    InboxPage pending(String scope, String moduleId, long afterRow, int maxItems, int maxBytes) throws IOException, SQLException {
        Connection db = open();
        var pending = new ArrayList<InboxItem>();
        long lastRow = afterRow;
        int remaining = maxBytes;
        boolean hasMore = false;
        String moduleFilter = moduleId == null ? "" : " AND module_id = ?";
        try (var select = db.prepareStatement("""
                SELECT rowid, message_id, module_id, expires_at, length(CAST(wire AS BLOB)), wire
                FROM cross_server_inbox WHERE scope = ? AND rowid > ?
                """ + moduleFilter + " ORDER BY rowid LIMIT ?")) {
            select.setString(1, scope);
            select.setLong(2, afterRow);
            if (moduleId != null) select.setString(3, moduleId);
            select.setInt(moduleId == null ? 3 : 4, maxItems + 1);
            try (var result = select.executeQuery()) {
                while (result.next()) {
                    int bytes = result.getInt(5);
                    if (pending.size() == maxItems || (!pending.isEmpty() && bytes > remaining)) {
                        hasMore = true;
                        break;
                    }
                    lastRow = result.getLong(1);
                    pending.add(new InboxItem(result.getString(2), result.getString(3), result.getLong(4), result.getString(6)));
                    remaining -= bytes;
                }
            }
        }
        db.commit();
        return new InboxPage(pending, lastRow, hasMore);
    }

    /** The Redis offset is only advanced in the same transaction as the recoverable inbox. */
    boolean[] stage(String scope, String cursor, List<InboxItem> candidates, long now) throws IOException, SQLException {
        return stageInbox(scope, cursor, candidates, now, 0).inserted();
    }

    StageResult stageProxy(String scope, List<InboxItem> candidates, long now, int maxPending)
            throws IOException, SQLException {
        return stageInbox(scope, null, candidates, now, maxPending);
    }

    private StageResult stageInbox(String scope, String cursor, List<InboxItem> candidates, long now, int maxPending)
            throws IOException, SQLException {
        Connection db = open();
        boolean cleanupDue = cleanup(db, now);
        boolean[] inserted = new boolean[candidates.size()];
        String limit = maxPending == 0 ? "" : """
                AND (SELECT COUNT(*) FROM cross_server_inbox WHERE scope = ?) < ?
                """;
        try (var insert = db.prepareStatement("""
                INSERT OR IGNORE INTO cross_server_inbox (message_id, scope, module_id, expires_at, wire)
                SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM cross_server_replay WHERE message_id = ?)
                """ + limit)) {
            for (int index = 0; index < candidates.size(); index++) {
                InboxItem item = candidates.get(index);
                insert.setString(1, item.messageId());
                insert.setString(2, scope);
                insert.setString(3, item.moduleId());
                insert.setLong(4, item.expiresAt());
                insert.setString(5, item.wire());
                insert.setString(6, item.messageId());
                if (maxPending != 0) {
                    insert.setString(7, scope);
                    insert.setInt(8, maxPending);
                }
                inserted[index] = insert.executeUpdate() == 1;
            }
        }
        boolean atCapacity = false;
        if (maxPending != 0) {
            try (var count = db.prepareStatement("SELECT COUNT(*) FROM cross_server_inbox WHERE scope = ?")) {
                count.setString(1, scope);
                try (var result = count.executeQuery()) {
                    atCapacity = result.next() && result.getInt(1) >= maxPending;
                }
            }
        }
        if (cursor != null) {
            try (var update = db.prepareStatement("""
                    INSERT INTO cross_server_cursor (scope, cursor) VALUES (?, ?)
                    ON CONFLICT(scope) DO UPDATE SET cursor = excluded.cursor
                    """)) {
                update.setString(1, scope);
                update.setString(2, cursor);
                update.executeUpdate();
            }
        }
        db.commit();
        if (cleanupDue) nextCleanup = now + 1_000;
        return new StageResult(inserted, atCapacity);
    }

    void finish(List<InboxItem> completed) throws IOException, SQLException {
        if (completed.isEmpty()) return;
        Connection db = open();
        try (var receipt = db.prepareStatement("INSERT OR IGNORE INTO cross_server_replay VALUES (?, ?)");
             var remove = db.prepareStatement("DELETE FROM cross_server_inbox WHERE message_id = ?")) {
            for (InboxItem item : completed) {
                receipt.setString(1, item.messageId());
                receipt.setLong(2, item.expiresAt());
                receipt.executeUpdate();
                remove.setString(1, item.messageId());
                remove.executeUpdate();
            }
        }
        db.commit();
    }

    void expireInactive(String scope, java.util.Set<String> liveModules, long now) throws IOException, SQLException {
        Connection db = open();
        String excludeActive = liveModules.isEmpty() ? "" : " AND module_id NOT IN ("
            + String.join(",", java.util.Collections.nCopies(liveModules.size(), "?")) + ")";
        try (var delete = db.prepareStatement(
                "DELETE FROM cross_server_inbox WHERE scope = ? AND expires_at < ?" + excludeActive)) {
            delete.setString(1, scope);
            delete.setLong(2, now);
            int parameter = 3;
            for (String moduleId : liveModules) delete.setString(parameter++, moduleId);
            delete.executeUpdate();
        }
        db.commit();
    }

    private boolean cleanup(Connection db, long now) throws SQLException {
        if (now < nextCleanup) return false;
        try (var delete = db.prepareStatement("DELETE FROM cross_server_replay WHERE expires_at < ?")) {
            delete.setLong(1, now);
            delete.executeUpdate();
        }
        return true;
    }

    static long expiry(long timestamp, long maxAgeMs) {
        return timestamp + Math.min(maxAgeMs, Long.MAX_VALUE - timestamp);
    }

    private Connection open() throws IOException, SQLException {
        if (connection != null) return connection;
        Files.createDirectories(database.getParent());
        Connection db = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        try (var statement = db.createStatement()) {
            statement.execute("PRAGMA busy_timeout=1000");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            // A single inbox batch can exceed SQLite's default checkpoint threshold.
            statement.execute("PRAGMA wal_autocheckpoint=8192");
            statement.execute("PRAGMA journal_size_limit=33554432");
            db.setAutoCommit(false);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS cross_server_replay (
                    message_id TEXT PRIMARY KEY,
                    expires_at INTEGER NOT NULL
                ) WITHOUT ROWID
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS cross_server_replay_expiry ON cross_server_replay(expires_at)");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS cross_server_inbox (
                    message_id TEXT PRIMARY KEY,
                    scope TEXT NOT NULL,
                    module_id TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    wire TEXT NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS cross_server_inbox_scope ON cross_server_inbox(scope)");
            statement.execute("CREATE INDEX IF NOT EXISTS cross_server_inbox_module ON cross_server_inbox(scope, module_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS cross_server_inbox_expiry ON cross_server_inbox(scope, expires_at)");
            statement.execute("CREATE TABLE IF NOT EXISTS cross_server_cursor (scope TEXT PRIMARY KEY, cursor TEXT NOT NULL)");
            db.commit();
        } catch (SQLException failure) {
            try { db.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
        connection = db;
        return db;
    }

    @Override
    public void close() throws SQLException {
        Connection db = connection;
        connection = null;
        if (db != null) db.close();
    }

    record Claim(String messageId, long timestamp) {}
    record InboxItem(String messageId, String moduleId, long expiresAt, String wire) {}
    record InboxPage(List<InboxItem> items, long lastRow, boolean hasMore) {}
    record StageResult(boolean[] inserted, boolean atCapacity) {}
}
