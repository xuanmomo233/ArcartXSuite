package xuanmo.arcartxsuite.entitytracker.dao;

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

/**
 * Boss击杀记录DAO - 用于击杀排行和参与排行统计
 */
public class BossKillRecordDao {
    private final DataSource dataSource;
    private final Logger logger;

    public BossKillRecordDao(DataSource dataSource, JavaPlugin plugin) {
        this.dataSource = dataSource;
        this.logger = plugin.getLogger();
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
