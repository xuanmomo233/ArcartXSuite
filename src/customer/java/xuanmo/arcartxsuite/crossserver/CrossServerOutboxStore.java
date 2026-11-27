package xuanmo.arcartxsuite.crossserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** A single SQLite connection owned by either the admission thread or the drain thread. */
final class CrossServerOutboxStore implements AutoCloseable {
    private static final int MAX_ROWS = 65_536;
    private final Path database;
    private Connection connection;

    CrossServerOutboxStore(Path database) {
        this.database = database;
    }

    boolean[] admit(String scope, List<Write> writes, long now, long maxBytes, int frameBytes) throws IOException, SQLException {
        var db = open();
        boolean[] admitted = new boolean[writes.size()];
        try (var insert = db.prepareStatement("""
                INSERT INTO outbox (scope, module, expires_at, wire_chars, wire_bytes, wire)
                SELECT ?, ?, ?, ?, ?, ?
                WHERE ? >= ? AND EXISTS (
                    SELECT 1 FROM outbox_usage WHERE id = 1 AND row_count < ? AND used_bytes <= ? - ?
                )
                """)) {
            for (int index = 0; index < writes.size(); index++) {
                Write write = writes.get(index);
                if (write.wireBytes() > frameBytes) continue;
                insert.setString(1, scope);
                insert.setString(2, write.module());
                insert.setLong(3, write.expiresAt());
                insert.setInt(4, write.wire().length());
                insert.setInt(5, write.wireBytes());
                insert.setString(6, write.wire());
                insert.setLong(7, write.expiresAt());
                insert.setLong(8, now);
                insert.setInt(9, MAX_ROWS);
                insert.setLong(10, maxBytes);
                insert.setInt(11, write.wireBytes());
                admitted[index] = insert.executeUpdate() == 1;
            }
        }
        db.commit();
        return admitted;
    }

    List<Head> peek(String scope, Collection<String> modules, long now, int limit) throws IOException, SQLException {
        if (modules.isEmpty()) return List.of();
        var db = open();
        String placeholders = String.join(",", Collections.nCopies(modules.size(), "?"));
        try (var select = db.prepareStatement("""
                SELECT seq, expires_at, wire_chars, module FROM outbox
                WHERE scope = ? AND expires_at >= ? AND module IN (%s)
                ORDER BY seq LIMIT ?
                """.formatted(placeholders))) {
            select.setString(1, scope);
            select.setLong(2, now);
            int index = 3;
            for (String module : modules) select.setString(index++, module);
            select.setInt(index, limit);
            var heads = new ArrayList<Head>(limit);
            try (var result = select.executeQuery()) {
                while (result.next()) {
                    heads.add(new Head(result.getLong(1), result.getLong(2), result.getInt(3), result.getString(4)));
                }
            }
            db.commit();
            return heads;
        }
    }

    List<Frame> frames(List<Head> heads) throws IOException, SQLException {
        if (heads.isEmpty()) return List.of();
        var db = open();
        var frames = new ArrayList<Frame>(heads.size());
        String placeholders = String.join(",", Collections.nCopies(heads.size(), "?"));
        try (var select = db.prepareStatement("""
                SELECT seq, expires_at, wire_chars, module, wire
                FROM outbox WHERE seq IN (%s) ORDER BY seq
                """.formatted(placeholders))) {
            for (int index = 0; index < heads.size(); index++) {
                select.setLong(index + 1, heads.get(index).sequence());
            }
            try (var result = select.executeQuery()) {
                while (result.next()) {
                    frames.add(new Frame(new Head(result.getLong(1), result.getLong(2),
                        result.getInt(3), result.getString(4)), result.getString(5)));
                }
            }
        }
        db.commit();
        return frames;
    }

    void remove(List<Frame> frames, int confirmed) throws IOException, SQLException {
        if (confirmed == 0) return;
        var db = open();
        String placeholders = String.join(",", Collections.nCopies(confirmed, "?"));
        try (var delete = db.prepareStatement("DELETE FROM outbox WHERE seq IN (" + placeholders + ")")) {
            for (int index = 0; index < confirmed; index++) delete.setLong(index + 1, frames.get(index).head().sequence());
            delete.executeUpdate();
        }
        db.commit();
    }

    int purgeExpired(long now) throws IOException, SQLException {
        var db = open();
        int count;
        try (var delete = db.prepareStatement("DELETE FROM outbox WHERE expires_at < ?")) {
            delete.setLong(1, now);
            count = delete.executeUpdate();
        }
        db.commit();
        return count;
    }

    private Connection open() throws IOException, SQLException {
        if (connection != null) return connection;
        Files.createDirectories(database.getParent());
        var db = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        try (var statement = db.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA wal_autocheckpoint=4096");
            statement.execute("PRAGMA journal_size_limit=16777216");
            db.setAutoCommit(false);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    scope TEXT NOT NULL,
                    module TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    wire_chars INTEGER NOT NULL,
                    wire_bytes INTEGER NOT NULL,
                    wire TEXT NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS outbox_scope_seq ON outbox(scope, seq)");
            statement.execute("CREATE INDEX IF NOT EXISTS outbox_expiry ON outbox(expires_at)");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS outbox_usage (
                    id INTEGER PRIMARY KEY CHECK(id = 1),
                    row_count INTEGER NOT NULL,
                    used_bytes INTEGER NOT NULL
                )
                """);
            statement.execute("""
                INSERT OR IGNORE INTO outbox_usage SELECT 1, COUNT(*), COALESCE(SUM(wire_bytes), 0) FROM outbox
                """);
            statement.execute("""
                CREATE TRIGGER IF NOT EXISTS outbox_added AFTER INSERT ON outbox BEGIN
                    UPDATE outbox_usage SET row_count = row_count + 1, used_bytes = used_bytes + NEW.wire_bytes WHERE id = 1;
                END
                """);
            statement.execute("""
                CREATE TRIGGER IF NOT EXISTS outbox_removed AFTER DELETE ON outbox BEGIN
                    UPDATE outbox_usage SET row_count = row_count - 1, used_bytes = used_bytes - OLD.wire_bytes WHERE id = 1;
                END
                """);
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
        var db = connection;
        connection = null;
        if (db != null) db.close();
    }

    record Write(String module, long expiresAt, int wireBytes, String wire) {}
    record Head(long sequence, long expiresAt, int wireChars, String module) {}
    record Frame(Head head, String wire) {}
}
