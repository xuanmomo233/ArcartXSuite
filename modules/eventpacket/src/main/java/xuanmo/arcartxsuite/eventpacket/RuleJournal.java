package xuanmo.arcartxsuite.eventpacket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;

final class RuleJournal {
    record CursorKey(String rule, String owner) {}
    record Cursor(long sequence, long notBefore) {}
    record MailJob(String id, UUID player, String title, String content, String currency, double amount) {
        AxsMailRequest request() {
            var request = AxsMailRequest.builder(id, title).sourcePlugin("eventpacket").recipient(player).content(content);
            if (!currency.isEmpty()) {
                request.currency(currency, amount);
            }
            return request.build();
        }
    }

    record Claim(MailJob job, String token, int attempt) {}
    private record Candidate(MailJob job, int attempts, long observedAt) {}

    private final DataSource source;
    private final boolean mysql;
    private final String databaseClock;
    private final String dueQuery;
    private final String candidatesQuery;
    private final String retryUpdate;

    RuleJournal(DataSource source, boolean mysql) {
        this.source = source;
        this.mysql = mysql;
        this.databaseClock = mysql ? "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)"
            : "CAST(strftime('%s','now') AS INTEGER)*1000+CAST(substr(strftime('%f','now'),4,3) AS INTEGER)";
        this.dueQuery = "SELECT MIN(due_at), " + databaseClock
            + " FROM ep_mail_job WHERE state IN ('PENDING','RUNNING')";
        this.candidatesQuery = """
            SELECT job_id, player_uuid, title, content, currency, amount, attempts, %s
            FROM ep_mail_job WHERE state IN ('PENDING', 'RUNNING') AND due_at<=%s
            ORDER BY due_at, job_id LIMIT 8
            """.formatted(databaseClock, databaseClock);
        this.retryUpdate = """
            UPDATE ep_mail_job SET state='PENDING', due_at=%s + ?, claim_token=NULL
            WHERE job_id=? AND state='RUNNING' AND claim_token=?
            """.formatted(databaseClock);
    }

    boolean mysql() {
        return mysql;
    }

    OptionalLong nextDueDelayMillis() throws SQLException {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement(dueQuery);
             var rows = query.executeQuery()) {
            if (!rows.next()) throw new SQLException("Could not read the next EventPacket delivery deadline");
            long due = rows.getLong(1);
            if (rows.wasNull()) return OptionalLong.empty();
            long now = rows.getLong(2);
            return OptionalLong.of(due <= now ? 0L : due - now);
        }
    }

    void initialize() throws SQLException {
        String engine = mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        String dueIndex = mysql ? ", INDEX ep_job_due (state, due_at, job_id)" : "";
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ep_rule_cursor (
                    rule_id VARCHAR(64) NOT NULL, owner_id VARCHAR(96) NOT NULL, seq BIGINT NOT NULL, not_before BIGINT NOT NULL,
                    PRIMARY KEY (rule_id, owner_id)
                )
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ep_admission_receipt (event_id CHAR(64) PRIMARY KEY)
                """ + engine);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ep_mail_job (
                    job_id CHAR(64) PRIMARY KEY, player_uuid CHAR(36) NOT NULL,
                    title TEXT NOT NULL, content TEXT NOT NULL, currency VARCHAR(64) NOT NULL, amount DOUBLE NOT NULL,
                    state VARCHAR(12) NOT NULL, due_at BIGINT NOT NULL, claim_token CHAR(36),
                    attempts INT NOT NULL, mail_id BIGINT
                """ + dueIndex + ")" + engine);
            if (!mysql) statement.execute("CREATE INDEX IF NOT EXISTS ep_job_due ON ep_mail_job(state, due_at, job_id)");
        }
    }

    Optional<Cursor> cursor(String rule, String owner) throws SQLException {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT seq, not_before FROM ep_rule_cursor WHERE rule_id=? AND owner_id=?")) {
            query.setString(1, rule);
            query.setString(2, owner);
            try (var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(new Cursor(rows.getLong(1), rows.getLong(2))) : Optional.empty();
            }
        }
    }

    Map<CursorKey, Cursor> cursors(List<RuleConfiguration.MailRule> rules) throws SQLException {
        if (rules.isEmpty()) return Map.of();
        String where = String.join(" OR ", Collections.nCopies(rules.size(), "(rule_id=? AND owner_id=?)"));
        var cursors = new HashMap<CursorKey, Cursor>();
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT rule_id, owner_id, seq, not_before FROM ep_rule_cursor WHERE " + where)) {
            int parameter = 1;
            for (var rule : rules) {
                query.setString(parameter++, rule.id());
                query.setString(parameter++, rule.owner());
            }
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    cursors.put(new CursorKey(rows.getString(1), rows.getString(2)), new Cursor(rows.getLong(3), rows.getLong(4)));
                }
            }
        }
        return cursors;
    }

    void startCursor(String rule, String owner, long sequence, boolean fromNow) throws SQLException {
        try (var connection = source.getConnection();
             var insert = connection.prepareStatement(
                 "INSERT INTO ep_rule_cursor (rule_id, owner_id, seq, not_before) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, rule);
            insert.setString(2, owner);
            insert.setLong(3, sequence);
            insert.setLong(4, fromNow ? databaseTime(connection) : 0L);
            insert.executeUpdate();
        } catch (SQLException failure) {
            if (!duplicate(failure)) throw failure;
        }
    }

    boolean stagePage(String rule, String owner, long from, long to, List<MailJob> jobs) throws SQLException {
        if (to <= from) throw new IllegalArgumentException("Result page did not advance");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement(
                "UPDATE ep_rule_cursor SET seq=? WHERE rule_id=? AND owner_id=? AND seq=?"
            )) {
                update.setLong(1, to);
                update.setString(2, rule);
                update.setString(3, owner);
                update.setLong(4, from);
                if (update.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
                insertJobs(connection, jobs);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    boolean stageEvent(String eventId, List<MailJob> jobs) throws SQLException {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO ep_admission_receipt (event_id) VALUES (?)")) {
                insert.setString(1, eventId);
                try {
                    insert.executeUpdate();
                } catch (SQLException failure) {
                    if (!duplicate(failure)) throw failure;
                    connection.rollback();
                    return false;
                }
                insertJobs(connection, jobs);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void insertJobs(Connection connection, List<MailJob> jobs) throws SQLException {
        if (jobs.isEmpty()) return;
        try (var insert = connection.prepareStatement("""
            INSERT INTO ep_mail_job
                (job_id, player_uuid, title, content, currency, amount, state, due_at, attempts)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, 0)
            """)) {
            for (MailJob job : jobs) {
                insert.setString(1, job.id());
                insert.setString(2, job.player().toString());
                insert.setString(3, job.title());
                insert.setString(4, job.content());
                insert.setString(5, job.currency());
                insert.setDouble(6, job.amount());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    Claim claim(long leaseMillis) throws SQLException {
        try (var connection = source.getConnection()) {
            // Each database decides which worker owns a job. Reading candidates is advisory.
            var candidates = new ArrayList<Candidate>();
            try (var query = connection.prepareStatement(candidatesQuery)) {
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        var job = new MailJob(rows.getString(1), UUID.fromString(rows.getString(2)), rows.getString(3),
                            rows.getString(4), rows.getString(5), rows.getDouble(6));
                        candidates.add(new Candidate(job, rows.getInt(7), rows.getLong(8)));
                    }
                }
            }
            if (candidates.isEmpty()) return null;
            try (var update = connection.prepareStatement("""
                UPDATE ep_mail_job SET state='RUNNING', due_at=?, claim_token=?, attempts=attempts+1
                WHERE job_id=? AND state IN ('PENDING', 'RUNNING') AND due_at<=? AND attempts=?
                """)) {
                for (Candidate candidate : candidates) {
                    String token = UUID.randomUUID().toString();
                    update.setLong(1, candidate.observedAt() + leaseMillis);
                    update.setString(2, token);
                    update.setString(3, candidate.job().id());
                    update.setLong(4, candidate.observedAt());
                    update.setInt(5, candidate.attempts());
                    if (update.executeUpdate() == 1) return new Claim(candidate.job(), token, candidate.attempts() + 1);
                }
            }
            return null;
        }
    }

    boolean complete(Claim claim, long mailId) throws SQLException {
        if (mailId <= 0) throw new IllegalArgumentException("A committed mail ID is required");
        try (var connection = source.getConnection();
             var update = connection.prepareStatement("""
                 UPDATE ep_mail_job SET state='DONE', mail_id=?, claim_token=NULL
                 WHERE job_id=? AND state='RUNNING' AND claim_token=?
                 """)) {
            update.setLong(1, mailId);
            bindClaim(update, claim);
            return update.executeUpdate() == 1;
        }
    }

    boolean retry(Claim claim, long delayMillis) throws SQLException {
        try (var connection = source.getConnection();
             var update = connection.prepareStatement(retryUpdate)) {
            update.setLong(1, delayMillis);
            bindClaim(update, claim);
            return update.executeUpdate() == 1;
        }
    }

    private static void bindClaim(PreparedStatement update, Claim claim) throws SQLException {
        update.setString(2, claim.job().id());
        update.setString(3, claim.token());
    }

    private long databaseTime(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT " + databaseClock)) {
            if (!rows.next()) throw new SQLException("Database time unavailable");
            return rows.getLong(1);
        }
    }

    private boolean duplicate(SQLException failure) {
        return mysql ? failure.getErrorCode() == 1062 : failure.getErrorCode() == 19;
    }
}
