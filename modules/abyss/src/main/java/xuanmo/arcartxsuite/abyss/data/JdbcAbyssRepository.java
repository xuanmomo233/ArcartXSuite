package xuanmo.arcartxsuite.abyss.data;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * Abyss 数据仓库（MySQL/SQLite 双方言）。
 * <p>
 * 数据源与表前缀由 {@code StorageManager.resolveModuleDataSource} 统一解析；
 * 共享模式用宿主池+模块前缀，模块配置独立 storage 时由 resolveModuleDataSource 建模块池。
 *
 * <p>表：
 * <ul>
 *   <li>{@code <prefix>player} —— 体力值、上次回复时间</li>
 *   <li>{@code <prefix>cooldowns} —— 进本冷却（player_uuid + dungeon_id）</li>
 *   <li>{@code <prefix>records} —— 通关次数、最佳用时</li>
 *   <li>{@code <prefix>pending_location} —— 断线待恢复位置（重进服拉回）</li>
 *   <li>{@code <prefix>globals} —— 全服共享计数器（跨副本/跨实例，如全服收集进度）</li>
 * </ul>
 */
public final class JdbcAbyssRepository extends AbstractModuleRepository {

    private boolean sqlite = true;
    private String tPlayer, tCooldowns, tRecords, tPending, tGlobals, tAdmission, tRunResults;
    private String tResultSequence, tResultFeed, tAdmissionArchive, tAdmissionReturns, tAdmissionAudit;
    private String tRewardGames;
    private String tRunDocuments;

    public JdbcAbyssRepository(DataSource dataSource, StorageDescriptor descriptor,
                             File dataFolder, Logger logger) {
        super("AXS-Abyss", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        sqlite = !isMysql();
        String p = tablePrefix();
        tPlayer = p + "player";
        tCooldowns = p + "cooldowns";
        tRecords = p + "records";
        tPending = p + "pending_location";
        tGlobals = p + "globals";
        tAdmission = p + "admission_requests";
        tRunResults = p + "run_results";
        tResultSequence = p + "result_sequence";
        tResultFeed = p + "result_feed";
        tAdmissionArchive = p + "admission_archive";
        tAdmissionReturns = p + "admission_returns";
        tAdmissionAudit = p + "admission_audit";
        tRewardGames = p + "reward_games";
        tRunDocuments = p + "run_documents";

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRunDocuments
                + " (document_key VARCHAR(192) PRIMARY KEY, run_id VARCHAR(36) NOT NULL,"
                + " player_uuid VARCHAR(36), snapshot " + (sqlite ? "TEXT" : "LONGTEXT") + " NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmission
                + " (owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL,"
                + " fingerprint VARCHAR(64) NOT NULL, snapshot TEXT NOT NULL, PRIMARY KEY (owner, request_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRunResults
                + " (run_id VARCHAR(36) PRIMARY KEY, owner VARCHAR(96) NOT NULL,"
                + " ended_at BIGINT NOT NULL, snapshot TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tResultSequence + " (id INTEGER PRIMARY KEY, seq BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tResultFeed
                + " (seq BIGINT PRIMARY KEY, run_id VARCHAR(36) NOT NULL UNIQUE)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmissionArchive
                + " (owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL, snapshot TEXT NOT NULL,"
                + " archived_at BIGINT NOT NULL, PRIMARY KEY (owner, request_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmissionReturns
                + " (owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL, player_uuid VARCHAR(36) NOT NULL,"
                + " saved_at BIGINT NOT NULL, confirmed INTEGER NOT NULL, PRIMARY KEY (owner, request_id, player_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAdmissionAudit
                + " (id VARCHAR(36) PRIMARY KEY, owner VARCHAR(96) NOT NULL, request_id VARCHAR(36) NOT NULL,"
                + " transaction_id VARCHAR(36), observed_state VARCHAR(32), observed_at BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRewardGames
                + " (offer_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL,"
                + " strategy VARCHAR(64) NOT NULL, title VARCHAR(128) NOT NULL, deck " + (sqlite ? "TEXT" : "LONGTEXT")
                + " NOT NULL, pick_limit INTEGER NOT NULL, expires_at BIGINT NOT NULL,"
                + " picked INTEGER NOT NULL, accepted INTEGER NOT NULL, closed INTEGER NOT NULL, touched_at BIGINT NOT NULL)");
            st.executeUpdate(sqlite ? "INSERT INTO " + tResultSequence + " VALUES (1,0) ON CONFLICT(id) DO NOTHING"
                : "INSERT INTO " + tResultSequence + " VALUES (1,0) ON DUPLICATE KEY UPDATE id=id");
            if (sqlite) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayer + " ("
                    + "player_uuid TEXT PRIMARY KEY,"
                    + "stamina INTEGER NOT NULL DEFAULT 0,"
                    + "stamina_last_regen INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCooldowns + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "dungeon_id TEXT NOT NULL,"
                    + "expiry_millis INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecords + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "dungeon_id TEXT NOT NULL,"
                    + "completions INTEGER NOT NULL DEFAULT 0,"
                    + "best_time_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "last_time_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
                    + "player_uuid TEXT PRIMARY KEY,"
                    + "world TEXT NOT NULL,"
                    + "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,"
                    + "yaw REAL NOT NULL DEFAULT 0, pitch REAL NOT NULL DEFAULT 0,"
                    + "saved_at INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobals + " ("
                    + "gkey TEXT PRIMARY KEY,"
                    + "gvalue INTEGER NOT NULL DEFAULT 0)");
            } else {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPlayer + " ("
                    + "player_uuid CHAR(36) PRIMARY KEY,"
                    + "stamina INT NOT NULL DEFAULT 0,"
                    + "stamina_last_regen BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tCooldowns + " ("
                    + "player_uuid CHAR(36) NOT NULL,"
                    + "dungeon_id VARCHAR(64) NOT NULL,"
                    + "expiry_millis BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tRecords + " ("
                    + "player_uuid CHAR(36) NOT NULL,"
                    + "dungeon_id VARCHAR(64) NOT NULL,"
                    + "completions INT NOT NULL DEFAULT 0,"
                    + "best_time_seconds BIGINT NOT NULL DEFAULT 0,"
                    + "last_time_seconds BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, dungeon_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tPending + " ("
                    + "player_uuid CHAR(36) PRIMARY KEY,"
                    + "world VARCHAR(64) NOT NULL,"
                    + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                    + "yaw FLOAT NOT NULL DEFAULT 0, pitch FLOAT NOT NULL DEFAULT 0,"
                    + "saved_at BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tGlobals + " ("
                    + "gkey VARCHAR(64) PRIMARY KEY,"
                    + "gvalue BIGINT NOT NULL DEFAULT 0)");
            }
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_abyss_records_dungeon ON "
            + tRecords + "(dungeon_id, best_time_seconds)");
        tryExecute(conn, "CREATE INDEX " + (sqlite ? "IF NOT EXISTS " : "") + "idx_abyss_game_player ON "
            + tRewardGames + "(player_uuid, closed, touched_at)");
        tryExecute(conn, "CREATE INDEX " + (sqlite ? "IF NOT EXISTS " : "") + "idx_abyss_game_expiry ON "
            + tRewardGames + "(closed, expires_at, touched_at)");
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tPlayer, tCooldowns, tRecords, tPending, tRewardGames);
    }

    @Override
    protected List<String> allTables() {
        // globals 是全服共享计数（非玩家数据），不列入 playerDataTables 防清理误删
        List<String> all = new ArrayList<>(playerDataTables());
        all.add(tGlobals);
        all.add(tAdmission);
        all.add(tRunResults);
        all.add(tResultSequence);
        all.add(tResultFeed);
        all.add(tAdmissionArchive);
        all.add(tAdmissionReturns);
        all.add(tAdmissionAudit);
        all.add(tRunDocuments);
        return all;
    }

    @FunctionalInterface
    public interface RunDocumentWork<T> { T execute(RunDocuments documents) throws Exception; }

    /** Worker-thread only. The run row is the cross-process lock for its documents. */
    public <T> T runTransaction(UUID run, RunDocumentWork<T> work) throws Exception {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("UPDATE " + tRunDocuments
                        + " SET snapshot=snapshot WHERE document_key=?")) {
                    lock.setString(1, "run/" + run);
                    if (lock.executeUpdate() == 0) {
                        // MySQL useAffectedRows=true reports zero for a no-op update even when its row was locked.
                        if (new RunDocuments(connection, run).read("run/" + run) == null)
                            throw new IllegalStateException("UNKNOWN_RUN");
                    }
                }
                T result = work.execute(new RunDocuments(connection, run));
                connection.commit();
                return result;
            } catch (Exception | Error failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    public String runDocument(String key) throws SQLException {
        try (Connection connection = getConnection()) {
            return new RunDocuments(connection, null).read(key);
        }
    }

    public void insertRunOperation(UUID run, String key, UUID player, String snapshot) throws SQLException {
        try (Connection connection = getConnection()) {
            new RunDocuments(connection, run).insert(key, player, snapshot);
        }
    }

    public record RunDocument(String key, String snapshot) { }
    public List<RunDocument> runDocuments(String prefix, String after, int limit) throws SQLException {
        try (Connection connection = getConnection(); var statement = connection.prepareStatement(
                "SELECT document_key,snapshot FROM " + tRunDocuments
                    + " WHERE document_key LIKE ? AND document_key>? ORDER BY document_key LIMIT ?")) {
            statement.setString(1, prefix + "%");
            statement.setString(2, after);
            statement.setInt(3, limit);
            var values = new ArrayList<RunDocument>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new RunDocument(rows.getString(1), rows.getString(2)));
            }
            return values;
        }
    }

    public void initializeRunDocument(UUID run, String snapshot) throws SQLException {
        try (Connection connection = getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO " + tRunDocuments + " (document_key,run_id,snapshot) VALUES (?,?,?)"
                + (sqlite ? " ON CONFLICT(document_key) DO NOTHING" : " ON DUPLICATE KEY UPDATE document_key=document_key"))) {
            statement.setString(1, "run/" + run);
            statement.setString(2, run.toString());
            statement.setString(3, snapshot);
            statement.executeUpdate();
        }
    }

    public final class RunDocuments {
        private final Connection connection;
        private final UUID run;
        private RunDocuments(Connection connection, UUID run) { this.connection = connection; this.run = run; }
        public String read(String key) throws SQLException {
            try (var statement = connection.prepareStatement("SELECT snapshot FROM " + tRunDocuments + " WHERE document_key=?")) {
                statement.setString(1, key);
                try (var result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
            }
        }
        public void write(String key, UUID player, String snapshot) throws SQLException {
            try (var statement = connection.prepareStatement("INSERT INTO " + tRunDocuments
                    + " (document_key,run_id,player_uuid,snapshot) VALUES (?,?,?,?)"
                    + (sqlite ? " ON CONFLICT(document_key) DO UPDATE SET snapshot=excluded.snapshot,run_id=excluded.run_id,player_uuid=excluded.player_uuid"
                        : " ON DUPLICATE KEY UPDATE snapshot=VALUES(snapshot),run_id=VALUES(run_id),player_uuid=VALUES(player_uuid)"))) {
                statement.setString(1, key);
                statement.setString(2, run.toString());
                statement.setString(3, player == null ? null : player.toString());
                statement.setString(4, snapshot);
                statement.executeUpdate();
            }
        }
        public void insert(String key, UUID player, String snapshot) throws SQLException {
            try (var statement = connection.prepareStatement("INSERT INTO " + tRunDocuments
                    + " (document_key,run_id,player_uuid,snapshot) VALUES (?,?,?,?)")) {
                statement.setString(1, key);
                statement.setString(2, run.toString());
                statement.setString(3, player == null ? null : player.toString());
                statement.setString(4, snapshot);
                statement.executeUpdate();
            }
        }
        public void delete(String key) throws SQLException {
            try (var statement = connection.prepareStatement("DELETE FROM " + tRunDocuments + " WHERE document_key=?")) {
                statement.setString(1, key);
                statement.executeUpdate();
            }
        }
    }

    public record AdmissionReceipt(String owner, String requestId, String fingerprint, String snapshot) { }

    public record RewardOffer(UUID id, UUID player, String strategy, String title, String deck, int limit,
                              long expiresAt, int picked, int accepted, boolean closed) { }

    public void insertRewardOffer(RewardOffer offer) throws SQLException {
        String sql = "INSERT INTO " + tRewardGames
            + " (offer_id,player_uuid,strategy,title,deck,pick_limit,expires_at,picked,accepted,closed,touched_at)"
            + " VALUES (?,?,?,?,?,?,?,0,0,0,?)" + (sqlite ? " ON CONFLICT(offer_id) DO NOTHING" : "");
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, offer.id().toString()); ps.setString(2, offer.player().toString());
            ps.setString(3, offer.strategy()); ps.setString(4, offer.title()); ps.setString(5, offer.deck());
            ps.setInt(6, offer.limit()); ps.setLong(7, offer.expiresAt()); ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException failure) {
            if (sqlite || failure.getErrorCode() != 1062) throw failure;
        }
    }

    public @Nullable RewardOffer loadRewardOffer(UUID id, UUID player) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM "
                + tRewardGames + " WHERE offer_id=? AND player_uuid=?")) {
            ps.setString(1, id.toString()); ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rewardOffer(rs) : null; }
        }
    }

    public List<RewardOffer> pendingRewardOffers(UUID player, int limit) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM "
                + tRewardGames + " WHERE player_uuid=? AND closed=0 ORDER BY touched_at,offer_id LIMIT ?")) {
            ps.setString(1, player.toString()); ps.setInt(2, limit);
            return rewardOffers(ps);
        }
    }

    public List<RewardOffer> expiredRewardOffers(long now, int limit) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM "
                + tRewardGames + " WHERE closed=0 AND expires_at>0 AND expires_at<=? ORDER BY touched_at,offer_id LIMIT ?")) {
            ps.setLong(1, now); ps.setInt(2, limit);
            return rewardOffers(ps);
        }
    }

    private static List<RewardOffer> rewardOffers(PreparedStatement ps) throws SQLException {
        var rows = new ArrayList<RewardOffer>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(rewardOffer(rs)); }
        return rows;
    }

    private static RewardOffer rewardOffer(ResultSet row) throws SQLException {
        return new RewardOffer(UUID.fromString(row.getString("offer_id")), UUID.fromString(row.getString("player_uuid")),
            row.getString("strategy"), row.getString("title"), row.getString("deck"), row.getInt("pick_limit"),
            row.getLong("expires_at"), row.getInt("picked"), row.getInt("accepted"), row.getInt("closed") != 0);
    }

    public boolean selectRewardSlots(RewardOffer offer, int picked, long now, boolean expired) throws SQLException {
        String deadline = expired ? "expires_at>0 AND expires_at<=?" : "(expires_at=0 OR expires_at>?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE " + tRewardGames
                + " SET picked=?,touched_at=? WHERE offer_id=? AND player_uuid=? AND picked=? AND closed=0 AND " + deadline)) {
            ps.setInt(1, picked); ps.setLong(2, now); ps.setString(3, offer.id().toString());
            ps.setString(4, offer.player().toString()); ps.setInt(5, offer.picked()); ps.setLong(6, now);
            return ps.executeUpdate() == 1;
        }
    }

    public void acceptRewardSlots(RewardOffer offer, int accepted) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE " + tRewardGames
                + " SET accepted=?,closed=?,touched_at=? WHERE offer_id=? AND player_uuid=? AND picked=? AND accepted=?")) {
            ps.setInt(1, accepted);
            ps.setInt(2, accepted == offer.picked() && Integer.bitCount(accepted) == offer.limit() ? 1 : 0);
            ps.setLong(3, System.currentTimeMillis()); ps.setString(4, offer.id().toString());
            ps.setString(5, offer.player().toString()); ps.setInt(6, offer.picked()); ps.setInt(7, offer.accepted());
            ps.executeUpdate();
        }
    }

    public void touchRewardOffer(RewardOffer offer) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE " + tRewardGames
                + " SET touched_at=? WHERE offer_id=? AND player_uuid=? AND closed=0")) {
            ps.setLong(1, System.currentTimeMillis()); ps.setString(2, offer.id().toString());
            ps.setString(3, offer.player().toString()); ps.executeUpdate();
        }
    }
    public record CommittedSnapshot(long sequence, String snapshot) { }

    public boolean insertRunResult(UUID run, String owner, long endedAt, String snapshot) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tRunResults + " (run_id, owner, ended_at, snapshot) VALUES (?, ?, ?, ?) ON CONFLICT(run_id) DO NOTHING"
            : "INSERT INTO " + tRunResults + " (run_id, owner, ended_at, snapshot) VALUES (?, ?, ?, ?)";
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                lockResultSequence(c);
                boolean inserted;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, run.toString()); ps.setString(2, owner);
                    ps.setLong(3, endedAt); ps.setString(4, snapshot);
                    inserted = ps.executeUpdate() == 1;
                }
                if (inserted) appendResultFeed(c, run.toString());
                c.commit();
                return inserted;
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            if (!sqlite && failure.getErrorCode() == 1062) return false;
            throw failure;
        }
    }

    private void lockResultSequence(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // Serialize publishers until commit, including when multiple servers share MySQL.
            st.executeUpdate("UPDATE " + tResultSequence + " SET seq=seq+1 WHERE id=1");
        }
    }

    private void appendResultFeed(Connection c, String run) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tResultFeed
                + " (seq, run_id) SELECT seq, ? FROM " + tResultSequence + " WHERE id=1")) {
            ps.setString(1, run);
            ps.executeUpdate();
        }
    }

    public void backfillResultFeed() throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                lockResultSequence(c);
                var pending = new ArrayList<String>();
                try (Statement st = c.createStatement(); ResultSet rows = st.executeQuery(
                        "SELECT r.run_id FROM " + tRunResults + " r LEFT JOIN " + tResultFeed
                        + " f ON f.run_id=r.run_id WHERE f.run_id IS NULL ORDER BY r.ended_at,r.run_id LIMIT 100")) {
                    while (rows.next()) pending.add(rows.getString(1));
                }
                for (String run : pending) {
                    appendResultFeed(c, run);
                    lockResultSequence(c);
                }
                c.commit();
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    public List<CommittedSnapshot> committedResults(String owner, long after, int limit) throws SQLException {
        var results = new ArrayList<CommittedSnapshot>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT f.seq,r.snapshot FROM " + tResultFeed + " f JOIN " + tRunResults
                + " r ON r.run_id=f.run_id WHERE f.seq>? AND r.owner=? ORDER BY f.seq LIMIT ?")) {
            ps.setLong(1, after); ps.setString(2, owner); ps.setInt(3, limit);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) results.add(new CommittedSnapshot(rows.getLong(1), rows.getString(2)));
            }
        }
        return results;
    }

    public long latestResultSequence() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(seq),0) FROM " + tResultFeed)) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    public @Nullable String loadRunResult(UUID run) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT snapshot FROM " + tRunResults + " WHERE run_id=?")) {
            ps.setString(1, run.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    public List<String> loadRunResults(String owner, long afterTime, String afterRun, int limit) throws SQLException {
        var results = new ArrayList<String>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT snapshot FROM " + tRunResults
                + " WHERE owner=? AND (ended_at>? OR (ended_at=? AND run_id>?))"
                + " ORDER BY ended_at, run_id LIMIT ?")) {
            ps.setString(1, owner); ps.setLong(2, afterTime); ps.setLong(3, afterTime);
            ps.setString(4, afterRun); ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) results.add(rs.getString(1)); }
        }
        return results;
    }

    public List<AdmissionReceipt> loadAdmissions() throws SQLException {
        var receipts = new ArrayList<AdmissionReceipt>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT owner, request_id, fingerprint, snapshot FROM " + tAdmission + " LIMIT 32769");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) receipts.add(new AdmissionReceipt(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        return receipts;
    }

    public @Nullable AdmissionReceipt loadAdmission(String owner, UUID request) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT a.fingerprint,COALESCE(b.snapshot,a.snapshot) FROM " + tAdmission + " a LEFT JOIN "
                + tAdmissionArchive + " b ON a.owner=b.owner AND a.request_id=b.request_id"
                + " WHERE a.owner=? AND a.request_id=?")) {
            ps.setString(1, owner); ps.setString(2, request.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AdmissionReceipt(owner, request.toString(), rs.getString(1), rs.getString(2)) : null;
            }
        }
    }

    public List<UUID> confirmedAdmissionReturns(String owner, UUID request) throws SQLException {
        var players = new ArrayList<UUID>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid FROM " + tAdmissionReturns + " WHERE owner=? AND request_id=? AND confirmed=1")) {
            ps.setString(1, owner); ps.setString(2, request.toString());
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) players.add(UUID.fromString(rows.getString(1)));
            }
        }
        return players;
    }

    public List<UUID> admissionReviewIds(String owner, UUID after, int limit) throws SQLException {
        var requests = new ArrayList<UUID>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT request_id FROM " + tAdmission
                + " WHERE owner=? AND request_id>? AND (snapshot LIKE 'phase: REVIEW_REQUIRED%'"
                + " OR snapshot LIKE 'phase: PREPARING%' OR snapshot LIKE 'phase: TELEPORTING%'"
                + " OR snapshot LIKE 'phase: RESERVING_COST%' OR snapshot LIKE 'phase: COMMITTING_COST%'"
                + " OR snapshot LIKE 'phase: COMPENSATING_COST%') ORDER BY request_id LIMIT ?")) {
            ps.setString(1, owner); ps.setString(2, after == null ? "" : after.toString()); ps.setInt(3, limit);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) requests.add(UUID.fromString(rows.getString(1)));
            }
        }
        return requests;
    }

    public void recordAdmissionReview(String owner, UUID request, @Nullable UUID transaction,
                                      @Nullable String observed) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO " + tAdmissionAudit
                + " (id,owner,request_id,transaction_id,observed_state,observed_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, owner); ps.setString(3, request.toString());
            ps.setString(4, transaction == null ? null : transaction.toString()); ps.setString(5, observed);
            ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate();
        }
    }

    public boolean archiveAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + tAdmission
                        + " SET snapshot='' WHERE owner=? AND request_id=? AND "
                        + (sqlite ? "snapshot=?" : "BINARY snapshot=BINARY ?"))) {
                    ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId()); ps.setString(3, receipt.snapshot());
                    if (ps.executeUpdate() != 1) { c.rollback(); return false; }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tAdmissionArchive
                        + " (owner,request_id,snapshot,archived_at) VALUES (?,?,?,?)")) {
                    ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId());
                    ps.setString(3, receipt.snapshot()); ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    public void insertAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO " + tAdmission
                + " (owner, request_id, fingerprint, snapshot) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, receipt.owner()); ps.setString(2, receipt.requestId());
            ps.setString(3, receipt.fingerprint()); ps.setString(4, receipt.snapshot());
            ps.executeUpdate();
        }
    }

    public void updateAdmission(AdmissionReceipt receipt) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE " + tAdmission
                + " SET snapshot=? WHERE owner=? AND request_id=? AND fingerprint=? AND snapshot<>''")) {
            ps.setString(1, receipt.snapshot()); ps.setString(2, receipt.owner());
            ps.setString(3, receipt.requestId()); ps.setString(4, receipt.fingerprint());
            if (ps.executeUpdate() != 1) throw new SQLException("Admission receipt not found");
        }
    }

    // ═══ 体力 ═══

    public void saveStamina(UUID player, int stamina, long lastRegen) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tPlayer + " (player_uuid, stamina, stamina_last_regen)"
              + " VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET"
              + " stamina=excluded.stamina, stamina_last_regen=excluded.stamina_last_regen"
            : "INSERT INTO " + tPlayer + " (player_uuid, stamina, stamina_last_regen)"
              + " VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE"
              + " stamina=VALUES(stamina), stamina_last_regen=VALUES(stamina_last_regen)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setInt(2, stamina);
            ps.setLong(3, lastRegen);
            ps.executeUpdate();
        }
    }

    /** @return [stamina, lastRegenMillis]；无记录返回 null。 */
    public @Nullable long[] loadStamina(UUID player) throws SQLException {
        String sql = "SELECT stamina, stamina_last_regen FROM " + tPlayer + " WHERE player_uuid = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new long[]{rs.getInt(1), rs.getLong(2)};
                }
            }
        }
        return null;
    }

    // ═══ 冷却 ═══

    public void saveCooldown(UUID player, String dungeonId, long expiryMillis) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tCooldowns + " (player_uuid, dungeon_id, expiry_millis)"
              + " VALUES (?, ?, ?) ON CONFLICT(player_uuid, dungeon_id) DO UPDATE SET"
              + " expiry_millis=excluded.expiry_millis"
            : "INSERT INTO " + tCooldowns + " (player_uuid, dungeon_id, expiry_millis)"
              + " VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE expiry_millis=VALUES(expiry_millis)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            ps.setLong(3, expiryMillis);
            ps.executeUpdate();
        }
    }

    public long loadCooldown(UUID player, String dungeonId) throws SQLException {
        String sql = "SELECT expiry_millis FROM " + tCooldowns
            + " WHERE player_uuid = ? AND dungeon_id = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public Map<String, Long> loadAllCooldowns(UUID player) throws SQLException {
        String sql = "SELECT dungeon_id, expiry_millis FROM " + tCooldowns + " WHERE player_uuid = ?";
        Map<String, Long> m = new LinkedHashMap<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) m.put(rs.getString(1), rs.getLong(2));
            }
        }
        return m;
    }

    // ═══ 通关记录 ═══

    public void saveRecord(UUID player, String dungeonId, long timeSeconds) throws SQLException {
        // completions+1，best_time 取最小（0 视为无记录）
        String sql = sqlite
            ? "INSERT INTO " + tRecords + " (player_uuid, dungeon_id, completions, best_time_seconds, last_time_seconds)"
              + " VALUES (?, ?, 1, ?, ?) ON CONFLICT(player_uuid, dungeon_id) DO UPDATE SET"
              + " completions=completions+1,"
              + " best_time_seconds=CASE WHEN best_time_seconds=0 OR excluded.best_time_seconds<best_time_seconds"
              + " THEN excluded.best_time_seconds ELSE best_time_seconds END,"
              + " last_time_seconds=excluded.last_time_seconds"
            : "INSERT INTO " + tRecords + " (player_uuid, dungeon_id, completions, best_time_seconds, last_time_seconds)"
              + " VALUES (?, ?, 1, ?, ?) ON DUPLICATE KEY UPDATE"
              + " completions=completions+1,"
              + " best_time_seconds=IF(best_time_seconds=0 OR VALUES(best_time_seconds)<best_time_seconds,"
              + " VALUES(best_time_seconds), best_time_seconds),"
              + " last_time_seconds=VALUES(last_time_seconds)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            ps.setLong(3, timeSeconds);
            ps.setLong(4, timeSeconds);
            ps.executeUpdate();
        }
    }

    /** 单玩家某地牢记录：[completions, bestTimeSeconds]。无记录 null。 */
    public @Nullable long[] loadRecord(UUID player, String dungeonId) throws SQLException {
        String sql = "SELECT completions, best_time_seconds FROM " + tRecords
            + " WHERE player_uuid = ? AND dungeon_id = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new long[]{rs.getLong(1), rs.getLong(2)};
            }
        }
        return null;
    }

    /** 地牢排行榜：最佳用时升序。 */
    public List<LeaderboardEntry> loadLeaderboard(String dungeonId, int limit) throws SQLException {
        String sql = "SELECT player_uuid, best_time_seconds, completions FROM " + tRecords
            + " WHERE dungeon_id = ? AND best_time_seconds > 0"
            + " ORDER BY best_time_seconds ASC LIMIT ?";
        List<LeaderboardEntry> list = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    list.add(new LeaderboardEntry(rank++,
                        UUID.fromString(rs.getString(1)),
                        rs.getLong(2), rs.getLong(3)));
                }
            }
        }
        return list;
    }

    public record LeaderboardEntry(int rank, UUID playerUuid, long bestTimeSeconds, long completions) { }

    // ═══ 断线待恢复位置 ═══

    public void savePendingLocation(UUID player, String world,
                                    double x, double y, double z,
                                    float yaw, float pitch) throws SQLException {
        savePendingLocation(player, world, x, y, z, yaw, pitch, null, null);
    }

    public void savePendingLocation(UUID player, String world, double x, double y, double z,
                                    float yaw, float pitch, @Nullable String owner, @Nullable UUID request) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tPending + " (player_uuid, world, x, y, z, yaw, pitch, saved_at)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET"
              + " world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,"
              + " yaw=excluded.yaw, pitch=excluded.pitch, saved_at=MAX(saved_at+1,excluded.saved_at)"
            : "INSERT INTO " + tPending + " (player_uuid, world, x, y, z, yaw, pitch, saved_at)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
              + " world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),"
              + " yaw=VALUES(yaw), pitch=VALUES(pitch), saved_at=GREATEST(saved_at+1,VALUES(saved_at))";
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            long savedAt = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat(6, yaw);
            ps.setFloat(7, pitch);
            ps.setLong(8, savedAt);
            ps.executeUpdate();
            if (owner != null && request != null) {
                try (PreparedStatement stamp = c.prepareStatement("SELECT saved_at FROM " + tPending + " WHERE player_uuid=?")) {
                    stamp.setString(1, player.toString());
                    try (ResultSet result = stamp.executeQuery()) {
                        if (!result.next()) throw new SQLException("Pending return location disappeared");
                        savedAt = result.getLong(1);
                    }
                }
                String upsert = sqlite
                    ? " ON CONFLICT(owner,request_id,player_uuid) DO UPDATE SET saved_at=excluded.saved_at,confirmed=0"
                    : " ON DUPLICATE KEY UPDATE saved_at=VALUES(saved_at),confirmed=0";
                try (PreparedStatement receipt = c.prepareStatement("INSERT INTO " + tAdmissionReturns
                        + " (owner,request_id,player_uuid,saved_at,confirmed) VALUES (?,?,?,?,0)" + upsert)) {
                    receipt.setString(1, owner); receipt.setString(2, request.toString());
                    receipt.setString(3, player.toString()); receipt.setLong(4, savedAt);
                    receipt.executeUpdate();
                }
            }
            c.commit();
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    /** 只读恢复位置，传送成功后才确认删除。 */
    public @Nullable PendingLocation loadPendingLocation(UUID player) throws SQLException {
        String sql = "SELECT world, x, y, z, yaw, pitch, saved_at FROM " + tPending + " WHERE player_uuid = ?";
        PendingLocation loc = null;
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loc = new PendingLocation(rs.getString(1),
                        rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getFloat(5), rs.getFloat(6),rs.getLong(7));
                }
            }
        }
        return loc;
    }

    public boolean confirmPendingLocation(UUID player,PendingLocation expected)throws SQLException {
        try(Connection c=getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps=c.prepareStatement("DELETE FROM "+tPending
                +" WHERE player_uuid=? AND saved_at=? AND world=? AND x=? AND y=? AND z=? AND yaw=? AND pitch=?")) {
            ps.setString(1,player.toString());ps.setLong(2,expected.savedAt());ps.setString(3,expected.world());
            ps.setDouble(4,expected.x());ps.setDouble(5,expected.y());ps.setDouble(6,expected.z());
            ps.setFloat(7,expected.yaw());ps.setFloat(8,expected.pitch());
            if (ps.executeUpdate() != 1) { c.rollback(); return false; }
            try (PreparedStatement receipt = c.prepareStatement("UPDATE " + tAdmissionReturns
                    + " SET confirmed=1 WHERE player_uuid=? AND saved_at=?")) {
                receipt.setString(1, player.toString()); receipt.setLong(2, expected.savedAt());
                receipt.executeUpdate();
            }
            c.commit();
            return true;
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    public record PendingLocation(String world, double x, double y, double z,
                                  float yaw, float pitch,long savedAt) { }

    // ═══ 全服共享计数器 ═══

    /** 启动时全量加载（计数器通常个位数，全表读无碍）。 */
    public Map<String, Long> loadAllGlobals() throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT gkey, gvalue FROM " + tGlobals);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString(1), rs.getLong(2));
        }
        return m;
    }

    public void saveGlobal(String key, long value) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + tGlobals + " (gkey, gvalue) VALUES (?, ?)"
              + " ON CONFLICT(gkey) DO UPDATE SET gvalue=excluded.gvalue"
            : "INSERT INTO " + tGlobals + " (gkey, gvalue) VALUES (?, ?)"
              + " ON DUPLICATE KEY UPDATE gvalue=VALUES(gvalue)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setLong(2, value);
            ps.executeUpdate();
        }
    }
}
