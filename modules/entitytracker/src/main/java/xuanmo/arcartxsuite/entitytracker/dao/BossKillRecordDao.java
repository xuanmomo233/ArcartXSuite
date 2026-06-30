package xuanmo.arcartxsuite.entitytracker.dao;

import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * Boss击杀记录DAO - 用于击杀排行和参与排行统计
 */
public class BossKillRecordDao {
    private final DataSource dataSource;
    private final Logger logger;

    public BossKillRecordDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.logger = AxsLog.logger();
    }

    /**
     * 写入击杀记录并返回自增 ID。
     */
    public long insert(BossKillRecord record) throws SQLException {
        String sql = """
            INSERT INTO boss_kill_records
            (boss_id, boss_display_name, kill_time, server_name, participants, drops,
             total_damage, duration_seconds, world_name, location_x, location_y, location_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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

    /**
     * 跨服入站去重：同一子服、Boss、击杀时间戳已存在则跳过。
     */
    public boolean existsByServerBossAndKillTime(String serverName, String bossId, LocalDateTime killTime)
        throws SQLException {
        if (serverName == null || bossId == null || killTime == null) {
            return false;
        }
        String sql = """
            SELECT 1 FROM boss_kill_records
            WHERE server_name = ? AND boss_id = ? AND kill_time = ?
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serverName);
            stmt.setString(2, bossId);
            stmt.setTimestamp(3, Timestamp.valueOf(killTime));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int deleteOlderThanDays(int days) throws SQLException {
        String sql = """
            DELETE FROM boss_kill_records
            WHERE kill_time < datetime('now', '-' || ? || ' days')
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, days);
            return stmt.executeUpdate();
        }
    }

    private static void setNullableDouble(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }

    /**
     * 获取击杀排行 - 统计玩家参与击杀Boss的次数
     * participants字段是JSON数组格式: [{"uuid":"xxx","name":"xxx","damage":123}, ...]
     */
    public List<PlayerBossBestDamage> findKillRankings(LocalDateTime startTime, 
                                                      LocalDateTime endTime, 
                                                      int limit) throws SQLException {
        // 由于SQLite不直接支持JSON查询，先获取所有记录再在Java中聚合
        String sql = """
            SELECT participants
            FROM boss_kill_records 
            WHERE kill_time BETWEEN ? AND ?
            """;

        Map<String, KillCountEntry> playerKills = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(startTime));
            stmt.setTimestamp(2, Timestamp.valueOf(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String participantsJson = rs.getString("participants");
                    if (participantsJson == null || participantsJson.isEmpty()) continue;

                    parseAndCountKills(participantsJson, playerKills);
                }
            }
        }

        // 转换为排行列表并排序
        List<PlayerBossBestDamage> rankings = new ArrayList<>();
        List<Map.Entry<String, KillCountEntry>> sortedEntries = playerKills.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().killCount, a.getValue().killCount))
            .limit(limit)
            .toList();

        int rank = 1;
        for (Map.Entry<String, KillCountEntry> entry : sortedEntries) {
            PlayerBossBestDamage record = new PlayerBossBestDamage();
            record.setPlayerUuid(entry.getKey());
            record.setPlayerName(entry.getValue().playerName);
            record.setBestDamage(entry.getValue().killCount); // 用bestDamage字段存储击杀次数
            record.setRank(rank++);
            rankings.add(record);
        }

        return rankings;
    }

    /**
     * 获取参与排行 - 统计玩家参与不同Boss的数量
     */
    public List<PlayerBossBestDamage> findParticipateRankings(LocalDateTime startTime, 
                                                             LocalDateTime endTime, 
                                                             int limit) throws SQLException {
        String sql = """
            SELECT boss_id, participants
            FROM boss_kill_records 
            WHERE kill_time BETWEEN ? AND ?
            """;

        // uuid -> {playerName, Set<bossId>}
        Map<String, ParticipateEntry> playerParticipation = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(startTime));
            stmt.setTimestamp(2, Timestamp.valueOf(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String bossId = rs.getString("boss_id");
                    String participantsJson = rs.getString("participants");
                    if (participantsJson == null || participantsJson.isEmpty()) continue;

                    parseAndCountParticipation(bossId, participantsJson, playerParticipation);
                }
            }
        }

        // 转换为排行列表并排序
        List<PlayerBossBestDamage> rankings = new ArrayList<>();
        List<Map.Entry<String, ParticipateEntry>> sortedEntries = playerParticipation.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().bossCount(), a.getValue().bossCount()))
            .limit(limit)
            .toList();

        int rank = 1;
        for (Map.Entry<String, ParticipateEntry> entry : sortedEntries) {
            PlayerBossBestDamage record = new PlayerBossBestDamage();
            record.setPlayerUuid(entry.getKey());
            record.setPlayerName(entry.getValue().playerName);
            record.setBestDamage(entry.getValue().bossCount()); // 用bestDamage字段存储参与数
            record.setRank(rank++);
            rankings.add(record);
        }

        return rankings;
    }

    /**
     * 解析participants JSON并统计击杀次数
     */
    private void parseAndCountKills(String json, Map<String, KillCountEntry> playerKills) {
        try {
            com.google.gson.JsonArray participants = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (com.google.gson.JsonElement element : participants) {
                com.google.gson.JsonObject player = element.getAsJsonObject();
                String uuid = player.get("uuid").getAsString();
                String name = player.has("name") ? player.get("name").getAsString() : "Unknown";

                playerKills.computeIfAbsent(uuid, k -> new KillCountEntry(name))
                    .killCount++;
            }
        } catch (Exception e) {
            logger.warning("解析参与者JSON失败: " + e.getMessage());
        }
    }

    /**
     * 解析participants JSON并统计参与不同Boss数
     */
    private void parseAndCountParticipation(String bossId, String json, 
                                           Map<String, ParticipateEntry> playerParticipation) {
        try {
            com.google.gson.JsonArray participants = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (com.google.gson.JsonElement element : participants) {
                com.google.gson.JsonObject player = element.getAsJsonObject();
                String uuid = player.get("uuid").getAsString();
                String name = player.has("name") ? player.get("name").getAsString() : "Unknown";

                playerParticipation.computeIfAbsent(uuid, k -> new ParticipateEntry(name))
                    .bossIds.add(bossId);
            }
        } catch (Exception e) {
            logger.warning("解析参与者JSON失败: " + e.getMessage());
        }
    }

    // 内部辅助类
    private static class KillCountEntry {
        String playerName;
        int killCount = 0;
        KillCountEntry(String playerName) { this.playerName = playerName; }
    }

    private static class ParticipateEntry {
        String playerName;
        java.util.Set<String> bossIds = new java.util.HashSet<>();
        ParticipateEntry(String playerName) { this.playerName = playerName; }
        int bossCount() { return bossIds.size(); }
    }
}
