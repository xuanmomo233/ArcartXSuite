package xuanmo.arcartxsuite.entitytracker.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;

public final class PeriodRankingDao {
    public record Standing(String playerId, String playerName, String bossName, int rank, int score) {}

    private final DataSource source;
    private final RankingRewardRecordDao records;

    public PeriodRankingDao(DataSource source, RankingRewardRecordDao records) {
        this.source = source;
        this.records = records;
    }

    public void recordScores(BossDamageSettlementRecord settlement) throws SQLException {
        try (var conn = source.getConnection()) {
            conn.setAutoCommit(false);
            try (var statement = conn.prepareStatement("""
                INSERT INTO entitytracker_period_scores
                (boss_id, kill_id, player_uuid, player_name, boss_name, damage, settled_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
                for (var entry : settlement.entriesByPlayer().values()) {
                    if (entry.playerUuid() == null || entry.damage() <= 0) continue;
                    statement.setString(1, settlement.mythicMobId());
                    statement.setString(2, settlement.entityUuid());
                    statement.setString(3, entry.playerUuid().toString());
                    statement.setString(4, entry.playerName());
                    statement.setString(5, settlement.bossDisplayName());
                    statement.setInt(6, (int) Math.min(Integer.MAX_VALUE, Math.round(entry.damage())));
                    statement.setLong(7, settlement.settledAtMillis());
                    try {
                        statement.executeUpdate();
                    } catch (SQLException duplicate) {
                        if (!duplicateKey(duplicate)) throw duplicate;
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(conn, failure);
                throw failure;
            }
        }
    }

    public void freeze(String bossId, String periodType, LocalDateTime start, long from, long until,
                       int limit, Function<Standing, RankingRewardRecord> reward) throws SQLException {
        try (var conn = source.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (var statement = conn.prepareStatement("""
                    INSERT INTO entitytracker_reward_batches (boss_id, reward_type, period_start_ms)
                    VALUES (?, ?, ?)
                    """)) {
                    statement.setString(1, bossId);
                    statement.setString(2, periodType);
                    statement.setLong(3, from);
                    statement.executeUpdate();
                } catch (SQLException duplicate) {
                    if (!duplicateKey(duplicate)) throw duplicate;
                    conn.rollback();
                    return;
                }
                for (Standing standing : standings(conn, bossId, from, until, limit)) {
                    RankingRewardRecord record = reward.apply(standing);
                    if (record != null) {
                        records.insert(conn, record, from);
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(conn, failure);
                throw failure;
            }
        }
    }

    private static List<Standing> standings(Connection conn, String bossId, long from, long until, int limit)
        throws SQLException {
        // Retain each player's best kill in the period, including when their lifetime record was higher.
        try (var statement = conn.prepareStatement("""
            SELECT s.player_uuid, s.player_name, s.boss_name, s.damage
            FROM entitytracker_period_scores s
            WHERE s.boss_id = ? AND s.settled_at >= ? AND s.settled_at < ?
              AND NOT EXISTS (
                SELECT 1 FROM entitytracker_period_scores better
                WHERE better.boss_id = s.boss_id AND better.player_uuid = s.player_uuid
                  AND better.settled_at >= ? AND better.settled_at < ?
                  AND (better.damage > s.damage OR (better.damage = s.damage AND (
                    better.settled_at < s.settled_at OR
                    (better.settled_at = s.settled_at AND better.kill_id < s.kill_id))))
              )
            ORDER BY s.damage DESC, s.settled_at, s.player_uuid
            LIMIT ?
            """)) {
            statement.setString(1, bossId);
            statement.setLong(2, from);
            statement.setLong(3, until);
            statement.setLong(4, from);
            statement.setLong(5, until);
            statement.setInt(6, limit);
            List<Standing> result = new ArrayList<>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Standing(rows.getString(1), rows.getString(2), rows.getString(3),
                        result.size() + 1, rows.getInt(4)));
                }
            }
            return result;
        }
    }

    private static boolean duplicateKey(SQLException failure) {
        return failure.getErrorCode() == 1062 || failure.getErrorCode() == 19;
    }

    private static void rollback(Connection conn, Exception failure) {
        try { conn.rollback(); }
        catch (SQLException rollback) { failure.addSuppressed(rollback); }
    }
}
