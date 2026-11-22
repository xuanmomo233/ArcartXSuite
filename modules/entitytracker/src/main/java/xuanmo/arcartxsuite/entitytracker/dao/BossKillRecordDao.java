package xuanmo.arcartxsuite.entitytracker.dao;

import com.google.gson.JsonParser;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

/**
 * Boss击杀记录DAO - 用于击杀排行和参与排行统计
 */
public class BossKillRecordDao {
    private final DataSource dataSource;
    private final Logger logger;
    private volatile boolean retentionIndexesReady;

    public BossKillRecordDao(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    /**
     * 写入击杀记录并返回自增 ID。
     */
    public long insert(BossKillRecord record) throws SQLException {
        try (Connection conn = dataSource.getConnection()) { return insert(conn, record); }
    }

    public long insert(Connection conn, BossKillRecord record) throws SQLException {
        String sql = """
            INSERT INTO boss_kill_records
            (boss_id, boss_display_name, kill_time, server_name, participants, drops,
             total_damage, duration_seconds, world_name, location_x, location_y, location_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, record.getBossId());
            stmt.setString(2, record.getBossDisplayName());
            stmt.setTimestamp(3, Timestamp.valueOf(record.getKillTime() == null
                ? LocalDateTime.now() : record.getKillTime()));
            stmt.setString(4, record.getServerName());
            stmt.setString(5, record.getParticipantsJson());
            stmt.setString(6, record.getDropsJson());
            stmt.setInt(7, record.getTotalDamage());
            stmt.setInt(8, record.getDurationSeconds());
            stmt.setString(9, record.getWorldName());
            setNullableDouble(stmt, 10, record.getLocationX());
            setNullableDouble(stmt, 11, record.getLocationY());
            setNullableDouble(stmt, 12, record.getLocationZ());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    record.setId(id);
                    return id;
                }
            }
        }
        return -1L;
    }

    /** Background-only bounded cleanup. Untransferred reward plans and permanent dedup receipts survive. */
    public int deleteExpiredBatch(LocalDateTime cutoff, int limit) throws SQLException {
        if(limit<1||limit>512)throw new IllegalArgumentException("Cleanup limit must be 1..512");
        try(var conn=dataSource.getConnection()) {
            boolean mysql=conn.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("mysql");
            ensureRetentionIndexes(conn,mysql);
            boolean auto=conn.getAutoCommit();conn.setAutoCommit(false);
            try {
                var ids=new ArrayList<Long>();
                try(var select=conn.prepareStatement("SELECT k.id FROM boss_kill_records k WHERE k.kill_time<?"
                        +" AND NOT EXISTS (SELECT 1 FROM entitytracker_kill_receipts r WHERE r.kill_id=k.id AND r.handed_off=0)"
                        +" ORDER BY k.kill_time,k.id LIMIT ?"+(mysql?" FOR UPDATE":""))) {
                    select.setTimestamp(1,Timestamp.valueOf(cutoff));select.setInt(2,limit);
                    try(var rows=select.executeQuery()){while(rows.next())ids.add(rows.getLong(1));}
                }
                if(ids.isEmpty()){conn.commit();return 0;}
                String slots=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
                executeIds(conn,"DELETE FROM roll_participation_records WHERE allocation_record_id IN"
                    +" (SELECT id FROM drop_allocation_records WHERE boss_kill_id IN ("+slots+"))",ids);
                // Keep earned/spent audit and balances; detach only the expiring display-history reference.
                executeIds(conn,"UPDATE dkp_transaction_records SET boss_kill_id=NULL WHERE boss_kill_id IN ("+slots+")",ids);
                executeIds(conn,"DELETE FROM drop_allocation_records WHERE boss_kill_id IN ("+slots+")",ids);
                int removed=executeIds(conn,"DELETE FROM boss_kill_records WHERE id IN ("+slots+")",ids);
                conn.commit();return removed;
            }catch(SQLException|RuntimeException failure) {
                try{conn.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;
            }finally{conn.setAutoCommit(auto);}
        }
    }

    private static int executeIds(Connection conn,String sql,List<Long> ids) throws SQLException {
        try(var statement=conn.prepareStatement(sql)) {
            for(int i=0;i<ids.size();i++)statement.setLong(i+1,ids.get(i));
            return statement.executeUpdate();
        }
    }

    private synchronized void ensureRetentionIndexes(Connection conn,boolean mysql) throws SQLException {
        if(retentionIndexesReady)return;
        String[][] indexes={{"idx_kill_retention","boss_kill_records","kill_time,id"},
            {"idx_roll_allocation","roll_participation_records","allocation_record_id"},
            {"idx_dkp_kill","dkp_transaction_records","boss_kill_id"}};
        for(var index:indexes)try(var statement=conn.createStatement()) {
            try{statement.execute("CREATE INDEX "+(mysql?"":"IF NOT EXISTS ")+index[0]+" ON "+index[1]+" ("+index[2]+")");}
            catch(SQLException failure){if(!mysql||failure.getErrorCode()!=1061)throw failure;}
        }
        retentionIndexesReady=true;
    }

    private static void setNullableDouble(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }

public record ActivityRankings(List<PlayerBossBestDamage> kills, List<PlayerBossBestDamage> participation) {}

    /** 聚合时间范围内的参与者。击杀榜统计次数，参与榜统计不同 Boss 的数量。 */
    public ActivityRankings findActivityRankings(LocalDateTime start, LocalDateTime end, int limit,
                                                  boolean includeKills, boolean includeParticipation) throws SQLException {
        if (!includeKills && !includeParticipation) return new ActivityRankings(List.of(), List.of());

        String columns = includeParticipation ? "boss_id, participants" : "participants";
        String sql = "SELECT " + columns + " FROM boss_kill_records WHERE kill_time BETWEEN ? AND ?";
        Map<String, ActivityEntry> activity = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(start));
            statement.setTimestamp(2, Timestamp.valueOf(end));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String json = rows.getString("participants");
                    if (json == null || json.isEmpty()) continue;
                    String bossId = includeParticipation ? rows.getString("boss_id") : null;
                    accumulateActivity(activity, json, bossId, includeParticipation);
                }
            }
        }

        return new ActivityRankings(
            includeKills ? rankActivity(activity, entry -> entry.kills, limit) : List.of(),
            includeParticipation ? rankActivity(activity, entry -> entry.bossIds.size(), limit) : List.of()
        );
    }

    private void accumulateActivity(Map<String, ActivityEntry> activity, String json,
                                    String bossId, boolean includeParticipation) {
        try {
            for (var element : JsonParser.parseString(json).getAsJsonArray()) {
                var player = element.getAsJsonObject();
                String uuid = player.get("uuid").getAsString();
                String name = player.has("name") ? player.get("name").getAsString() : "Unknown";
                var entry = activity.computeIfAbsent(uuid, key -> new ActivityEntry(name, includeParticipation));
                entry.kills++;
                if (includeParticipation) entry.bossIds.add(bossId);
            }
        } catch (RuntimeException failure) {
            logger.warning("解析参与者JSON失败: " + failure.getMessage());
        }
    }

    private static List<PlayerBossBestDamage> rankActivity(Map<String, ActivityEntry> activity,
                                                            ToIntFunction<ActivityEntry> score, int limit) {
        var ordered = activity.entrySet().stream()
            .sorted((first, second) -> Integer.compare(score.applyAsInt(second.getValue()), score.applyAsInt(first.getValue())))
            .limit(limit)
            .toList();
        var rankings = new ArrayList<PlayerBossBestDamage>(ordered.size());
        int rank = 1;
        for (var entry : ordered) {
            var row = new PlayerBossBestDamage();
            row.setPlayerUuid(entry.getKey());
            row.setPlayerName(entry.getValue().playerName);
            row.setBestDamage(score.applyAsInt(entry.getValue()));
            row.setRank(rank++);
            rankings.add(row);
        }
        return rankings;
    }

    private static final class ActivityEntry {
        final String playerName;
        final Set<String> bossIds;
        int kills;

        ActivityEntry(String playerName, boolean includeParticipation) {
            this.playerName = playerName;
            this.bossIds = includeParticipation ? new HashSet<>() : Set.of();
        }
    }
}



