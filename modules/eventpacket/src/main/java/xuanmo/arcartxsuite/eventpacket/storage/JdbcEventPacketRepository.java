package xuanmo.arcartxsuite.eventpacket.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * 基于 JDBC 的事件包仓储实现。
 * <p>
 * 支持 SQLite 与 MySQL 两种方言，自动建表并提供击杀进度与
 * 已触发规则的增删改查能力。继承 {@link AbstractModuleRepository}
 * 以复用宿主统一的连接池与数据迁移基础设施。
 */
public final class JdbcEventPacketRepository extends AbstractModuleRepository implements EventPacketRepository {
    private final String ownerId;

    public JdbcEventPacketRepository(DataSource dataSource, StorageDescriptor descriptor,
                                     File dataFolder, Logger logger) throws IOException {
        super("AXS-EventPacket", dataSource, descriptor, dataFolder, logger);
        Files.createDirectories(dataFolder.toPath());
        var path = dataFolder.toPath().resolve("admission-inbox-node.id");
        if (!Files.exists(path)) {
            try {
                Files.writeString(path, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
                // Another initializer has already assigned this node its identity.
            }
        }
        ownerId = UUID.fromString(Files.readString(path).trim()).toString();
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        try(Statement s=conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS eventpacket_auth_inbox (fact_id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,plan TEXT NOT NULL,step INTEGER NOT NULL,state VARCHAR(16) NOT NULL,owner_id VARCHAR(36) NOT NULL DEFAULT '',UNIQUE(player_uuid,state,fact_id))");
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("eventpacket_kill_progress", "eventpacket_fired_rules", "eventpacket_auth_inbox");
    }

    @Override
    protected List<String> allTables() {
        return List.of("eventpacket_kill_progress", "eventpacket_fired_rules", "eventpacket_auth_inbox");
    }

    // ─── Kill count ──────────────────────────────────────────
    @Override public void enqueueAuthFact(UUID id,UUID player,String plan) throws SQLException {
        String sql=isMysql()?"INSERT IGNORE INTO eventpacket_auth_inbox (fact_id,player_uuid,plan,step,state) VALUES (?,?,?,0,'PENDING')"
            :"INSERT INTO eventpacket_auth_inbox (fact_id,player_uuid,plan,step,state) VALUES (?,?,?,0,'PENDING') ON CONFLICT(fact_id) DO NOTHING";
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
            s.setString(1,id.toString()); s.setString(2,player.toString()); s.setString(3,plan); s.executeUpdate();
        }
    }
    @Override public AuthFact nextAuthFact(UUID player) throws SQLException {
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement("SELECT * FROM eventpacket_auth_inbox WHERE player_uuid=? AND state='PENDING' ORDER BY fact_id LIMIT 1")) {
            s.setString(1,player.toString()); try(ResultSet r=s.executeQuery()) { return r.next()?authFact(r):null; }
        }
    }
    private static AuthFact authFact(ResultSet r) throws SQLException {
        return new AuthFact(UUID.fromString(r.getString("fact_id")),UUID.fromString(r.getString("player_uuid")),r.getString("plan"),r.getInt("step"),r.getString("state"));
    }
    @Override public boolean claimAuthStep(UUID id,int step) throws SQLException {
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement("UPDATE eventpacket_auth_inbox SET state='RUNNING',owner_id=? WHERE fact_id=? AND step=? AND state='PENDING'")) {
            s.setString(1, ownerId);
            s.setString(2, id.toString());
            s.setInt(3, step);
            return s.executeUpdate() == 1;
        }
    }
    @Override public void finishAuthStep(UUID id,int step,String state) throws SQLException {
        if(!java.util.Set.of("PENDING","DONE","REVIEW","RETRY").contains(state)) throw new IllegalArgumentException("Invalid inbox state");
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement("UPDATE eventpacket_auth_inbox SET state=?,step=?,owner_id='' WHERE fact_id=? AND step=? AND state='RUNNING' AND owner_id=?")) {
            s.setString(1,state.equals("RETRY")?"PENDING":state); s.setInt(2,step+(state.equals("PENDING")?1:0)); s.setString(3,id.toString()); s.setInt(4,step);
            s.setString(5, ownerId);
            s.executeUpdate();
        }
    }
    @Override public void recoverAuthSteps() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement s = c.prepareStatement("UPDATE eventpacket_auth_inbox SET state='REVIEW',owner_id='' WHERE state='RUNNING' AND owner_id=?")) {
            s.setString(1, ownerId);
            s.executeUpdate();
        }
    }
    @Override public java.util.Set<UUID> pendingAuthProfiles() throws SQLException {
        var profiles=new java.util.HashSet<UUID>();
        try(Connection c=getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT DISTINCT player_uuid FROM eventpacket_auth_inbox WHERE state='PENDING'")) {
            while(r.next()) profiles.add(UUID.fromString(r.getString(1)));
        }
        return java.util.Set.copyOf(profiles);
    }
    @Override public java.util.List<AuthFact> reviewAuthFacts(UUID player) throws SQLException {
        var result=new java.util.ArrayList<AuthFact>();
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement("SELECT * FROM eventpacket_auth_inbox WHERE player_uuid=? AND state IN ('REVIEW','RUNNING') ORDER BY fact_id LIMIT 32")) {
            s.setString(1,player.toString()); try(ResultSet r=s.executeQuery()) { while(r.next()) result.add(authFact(r)); }
        }
        return java.util.List.copyOf(result);
    }
    @Override public boolean resolveAuthStep(UUID id,boolean retry) throws SQLException {
        try(Connection c=getConnection(); PreparedStatement s=c.prepareStatement("UPDATE eventpacket_auth_inbox SET state='PENDING',step=step+? WHERE fact_id=? AND state='REVIEW'")) {
            s.setInt(1,retry?0:1); s.setString(2,id.toString()); return s.executeUpdate()==1;
        }
    }

    /**
     * 查询指定玩家在某条规则下的累计击杀数。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 当前击杀数，无记录时返回 0
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public int getKillCount(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = getConnection();
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

    /**
     * 设置指定玩家在某条规则下的击杀数，使用 upsert 语义写入。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @param count      要设置的击杀数
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void setKillCount(UUID playerUuid, String ruleId, int count) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(killCountUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setInt(3, Math.max(0, count));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * 将指定玩家在某条规则下的击杀数自增 1，按方言使用对应的 upsert 语句。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void incrementKillCount(UUID playerUuid, String ruleId) throws SQLException {
        if (!isMysql()) {
            try (Connection connection = getConnection();
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
            try (Connection connection = getConnection();
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

    /**
     * 加载指定玩家所有规则的击杀进度。
     *
     * @param playerUuid 玩家 UUID
     * @return 规则 ID 到击杀数的映射
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public Map<String, Integer> loadAllKillCounts(UUID playerUuid) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = getConnection();
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

    /**
     * 判断指定玩家是否已触发过某条规则。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @return 已触发返回 {@code true}
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public boolean hasFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = getConnection();
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

    /**
     * 标记指定玩家已触发某条规则，使用 upsert 语义写入。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void markFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(firedRuleUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * 移除指定玩家某条规则的已触发标记。
     *
     * @param playerUuid 玩家 UUID
     * @param ruleId     规则 ID
     * @throws SQLException 数据库操作失败时抛出
     */
    @Override
    public void removeFired(UUID playerUuid, String ruleId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM eventpacket_fired_rules WHERE player_uuid = ? AND rule_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, ruleId);
            statement.executeUpdate();
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────

    /**
     * 关闭仓储，释放底层连接池资源。
     */
    @Override
    public void close() {
        shutdown();
    }

    // ─── Internal ────────────────────────────────────────────

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
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
        if (!isMysql()) {
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
        if (!isMysql()) {
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
