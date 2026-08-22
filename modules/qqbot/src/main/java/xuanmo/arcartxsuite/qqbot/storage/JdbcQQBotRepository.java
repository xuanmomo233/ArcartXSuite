package xuanmo.arcartxsuite.qqbot.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * QQBot 模块基于 JDBC 的数据仓库实现。
 * <p>
 * 关联模块：{@code qqbot}。继承 {@link AbstractModuleRepository}，实现 {@link QQBotRepository} 接口，
 * 管理 QQ-游戏绑定、积分账户、签到记录、兑换流水、黑名单、红包及领取记录、群活跃度等数据表。
 * 支持 SQLite 与 MySQL，自动建表与建索引，并提供玩家数据表的枚举以供数据清理。
 */
public final class JdbcQQBotRepository extends AbstractModuleRepository implements QQBotRepository {

    private final String tableBindings;
    private final String tablePoints;
    private final String tableSignin;
    private final String tableRedeemLog;
    private final String tableBlacklist;
    private final String tableRedPackets;
    private final String tableRedPacketClaims;
    private final String tableActivity;

    public JdbcQQBotRepository(DataSource dataSource, StorageDescriptor descriptor,
                               File dataFolder, Logger logger) {
        super("AXS-QQBot", dataSource, descriptor, dataFolder, logger);
        String prefix = isMysql() ? tablePrefix() : "axs_qqbot_";
        this.tableBindings = prefix + "bindings";
        this.tablePoints = prefix + "points";
        this.tableSignin = prefix + "signin";
        this.tableRedeemLog = prefix + "redeem_log";
        this.tableBlacklist = prefix + "blacklist";
        this.tableRedPackets = prefix + "red_packets";
        this.tableRedPacketClaims = prefix + "red_packet_claims";
        this.tableActivity = prefix + "activity";
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableBindings + """
             (
                id INTEGER PRIMARY KEY""" + " " + autoIncrement() + """
                , qq_id BIGINT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(64) NOT NULL,
                bound_at BIGINT NOT NULL DEFAULT 0,
                UNIQUE(qq_id, player_uuid)
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_qq ON " + tableBindings + " (qq_id)");
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_uuid ON " + tableBindings + " (player_uuid)");
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_name ON " + tableBindings + " (player_name)");

        // 积分账户表
        String pointsSql = "CREATE TABLE IF NOT EXISTS " + tablePoints + """
             (
                qq_id BIGINT PRIMARY KEY,
                balance INT NOT NULL DEFAULT 0,
                total_earned INT NOT NULL DEFAULT 0,
                total_spent INT NOT NULL DEFAULT 0,
                updated_at BIGINT NOT NULL DEFAULT 0
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(pointsSql)) {
            ps.executeUpdate();
        }

        // 签到记录表（联合主键防重复签到）
        String signinSql = "CREATE TABLE IF NOT EXISTS " + tableSignin + """
             (
                qq_id BIGINT NOT NULL,
                sign_date VARCHAR(10) NOT NULL,
                streak INT NOT NULL DEFAULT 1,
                signed_at BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (qq_id, sign_date)
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(signinSql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_signin_qq ON " + tableSignin + " (qq_id)");

        // 兑换流水表
        String redeemSql = "CREATE TABLE IF NOT EXISTS " + tableRedeemLog + """
             (
                id INTEGER PRIMARY KEY""" + " " + autoIncrement() + """
                , qq_id BIGINT NOT NULL,
                prize_id VARCHAR(64) NOT NULL,
                cost INT NOT NULL DEFAULT 0,
                redeem_date VARCHAR(10) NOT NULL,
                created_at BIGINT NOT NULL DEFAULT 0
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(redeemSql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_redeem_qq ON " + tableRedeemLog + " (qq_id, redeem_date)");

        // 黑名单表
        String blacklistSql = "CREATE TABLE IF NOT EXISTS " + tableBlacklist + """
             (
                qq_id BIGINT PRIMARY KEY,
                added_by BIGINT NOT NULL DEFAULT 0,
                added_at BIGINT NOT NULL DEFAULT 0
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(blacklistSql)) {
            ps.executeUpdate();
        }

        // 红包表
        String redPacketSql = "CREATE TABLE IF NOT EXISTS " + tableRedPackets + """
             (
                id INTEGER PRIMARY KEY""" + " " + autoIncrement() + """
                , sender_qq BIGINT NOT NULL,
                group_id BIGINT NOT NULL,
                total_amount INT NOT NULL DEFAULT 0,
                remaining_amount INT NOT NULL DEFAULT 0,
                count INT NOT NULL DEFAULT 0,
                claimed_count INT NOT NULL DEFAULT 0,
                expire_at BIGINT NOT NULL DEFAULT 0,
                created_at BIGINT NOT NULL DEFAULT 0
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(redPacketSql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_rp_group ON " + tableRedPackets + " (group_id, expire_at)");

        // 红包领取记录表
        String claimSql = "CREATE TABLE IF NOT EXISTS " + tableRedPacketClaims + """
             (
                id INTEGER PRIMARY KEY""" + " " + autoIncrement() + """
                , red_packet_id BIGINT NOT NULL,
                claimer_qq BIGINT NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                claimed_at BIGINT NOT NULL DEFAULT 0
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(claimSql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_rpc_redpacket ON " + tableRedPacketClaims + " (red_packet_id)");

        // 活跃度表
        String activitySql = "CREATE TABLE IF NOT EXISTS " + tableActivity + """
             (
                qq_id BIGINT NOT NULL,
                group_id BIGINT NOT NULL,
                activity_date VARCHAR(10) NOT NULL,
                message_count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (qq_id, group_id, activity_date)
            )
            """;
        try (PreparedStatement ps = conn.prepareStatement(activitySql)) {
            ps.executeUpdate();
        }
        tryExecute(conn, "CREATE INDEX IF NOT EXISTS idx_qqbot_activity_date ON " + tableActivity + " (activity_date)");
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of(tableBindings);
    }

    @Override
    protected List<String> allTables() {
        return List.of(tableBindings, tablePoints, tableSignin, tableRedeemLog, tableBlacklist,
            tableRedPackets, tableRedPacketClaims, tableActivity);
    }

    /**
     * 关闭仓库，释放连接池等资源。
     */
    @Override
    public void close() {
        shutdown();
    }

    /**
     * 插入一条 QQ-游戏绑定记录。
     *
     * @param qqId       QQ 号
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     */
    @Override
    public void insertBinding(long qqId, UUID playerUuid, String playerName) {
        String sql = "INSERT INTO " + tableBindings
            + " (qq_id, player_uuid, player_name, bound_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 插入绑定失败: " + e.getMessage());
        }
    }

    /**
     * 按 QQ 号删除绑定记录。
     *
     * @param qqId 待解绑的 QQ 号
     */
    @Override
    public void deleteBindingByQq(long qqId) {
        String sql = "DELETE FROM " + tableBindings + " WHERE qq_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 删除绑定失败(QQ): " + e.getMessage());
        }
    }

    /**
     * 按玩家 UUID 删除绑定记录。
     *
     * @param playerUuid 待解绑的玩家 UUID
     */
    @Override
    public void deleteBindingByPlayer(UUID playerUuid) {
        String sql = "DELETE FROM " + tableBindings + " WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 删除绑定失败(Player): " + e.getMessage());
        }
    }

    /**
     * 按 QQ 号查询绑定记录（取第一条）。
     *
     * @param qqId QQ 号
     * @return 绑定记录，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public QQBotBinding findByQq(long qqId) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " WHERE qq_id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询绑定失败(QQ): " + e.getMessage());
        }
        return null;
    }

    /**
     * 按玩家 UUID 查询绑定记录。
     *
     * @param playerUuid 玩家 UUID
     * @return 绑定记录，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public QQBotBinding findByPlayer(UUID playerUuid) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " WHERE player_uuid = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询绑定失败(Player): " + e.getMessage());
        }
        return null;
    }

    /**
     * 按玩家名查询绑定记录。
     *
     * @param playerName 玩家名
     * @return 绑定记录，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public QQBotBinding findByPlayerName(String playerName) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " WHERE player_name = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询绑定失败(Name): " + e.getMessage());
        }
        return null;
    }

    /**
     * 查询指定 QQ 号下的全部绑定记录。
     *
     * @param qqId QQ 号
     * @return 绑定记录列表，可能为空
     */
    @Override
    public List<QQBotBinding> findAllByQq(long qqId) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " WHERE qq_id = ?";
        List<QQBotBinding> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询所有绑定失败(QQ): " + e.getMessage());
        }
        return list;
    }

    /**
     * 统计指定 QQ 号下的绑定数量。
     *
     * @param qqId QQ 号
     * @return 绑定数量，查询失败返回 {@code 0}
     */
    @Override
    public int countByQq(long qqId) {
        String sql = "SELECT COUNT(*) FROM " + tableBindings + " WHERE qq_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 统计绑定数失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 统计绑定表中的总记录数。
     *
     * @return 总绑定数，查询失败返回 {@code 0}
     */
    @Override
    public int countBindings() {
        String sql = "SELECT COUNT(*) FROM " + tableBindings;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.warning("QQBot 统计总绑定数失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 分页查询绑定记录（按绑定时间倒序）。
     *
     * @param page     页码，从 0 开始
     * @param pageSize 每页条数
     * @return 绑定记录列表
     */
    @Override
    public List<QQBotBinding> getBindingsPage(int page, int pageSize) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " ORDER BY bound_at DESC LIMIT ? OFFSET ?";
        List<QQBotBinding> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.warning("QQBot 分页查询绑定失败: " + e.getMessage());
        }
        return list;
    }

    /**
     * 按关键词模糊搜索绑定记录（匹配玩家名或 QQ 号）。
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回条数
     * @return 匹配的绑定记录列表
     */
    @Override
    public List<QQBotBinding> searchBindings(String keyword, int limit) {
        String sql = "SELECT qq_id, player_uuid, player_name, bound_at FROM " + tableBindings
            + " WHERE player_name LIKE ? OR CAST(qq_id AS TEXT) LIKE ? LIMIT ?";
        List<QQBotBinding> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.warning("QQBot 搜索绑定失败: " + e.getMessage());
        }
        return list;
    }

    private QQBotBinding mapRow(ResultSet rs) throws SQLException {
        return new QQBotBinding(
            rs.getLong("qq_id"),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("player_name"),
            rs.getLong("bound_at")
        );
    }

    // ─── 签到积分实现 ────────────────────────────────────

    /**
     * 查询指定 QQ 号的积分余额。
     *
     * @param qqId QQ 号
     * @return 积分余额，无记录或查询失败返回 {@code 0}
     */
    @Override
    public int getPoints(long qqId) {
        String sql = "SELECT balance FROM " + tablePoints + " WHERE qq_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询积分失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 查询指定 QQ 号的积分账户详情。
     *
     * @param qqId QQ 号
     * @return 积分账户，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public PointAccount getPointAccount(long qqId) {
        String sql = "SELECT qq_id, balance, total_earned, total_spent FROM " + tablePoints + " WHERE qq_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PointAccount(
                        rs.getLong("qq_id"),
                        rs.getInt("balance"),
                        rs.getInt("total_earned"),
                        rs.getInt("total_spent")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询积分账户失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 为指定 QQ 号增加积分（upsert），正增量计入累计获得。
     *
     * @param qqId   QQ 号
     * @param delta  积分变化量（可为负）
     * @param action 操作标识
     * @param detail 操作详情
     * @return 操作后的积分余额
     */
    @Override
    public synchronized int addPoints(long qqId, int delta, String action, String detail) {
        long now = System.currentTimeMillis();
        // upsert: 存在则累加，不存在则插入
        String upsert = isMysql()
            ? "INSERT INTO " + tablePoints + " (qq_id, balance, total_earned, total_spent, updated_at) "
                + "VALUES (?, ?, ?, 0, ?) "
                + "ON DUPLICATE KEY UPDATE balance = balance + ?, total_earned = total_earned + ?, updated_at = ?"
            : "INSERT INTO " + tablePoints + " (qq_id, balance, total_earned, total_spent, updated_at) "
                + "VALUES (?, ?, ?, 0, ?) "
                + "ON CONFLICT(qq_id) DO UPDATE SET balance = balance + ?, total_earned = total_earned + ?, updated_at = ?";
        int earnedDelta = Math.max(0, delta);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setLong(1, qqId);
            ps.setInt(2, delta);
            ps.setInt(3, earnedDelta);
            ps.setLong(4, now);
            ps.setInt(5, delta);
            ps.setInt(6, earnedDelta);
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 增加积分失败: " + e.getMessage());
        }
        return getPoints(qqId);
    }

    /**
     * 从指定 QQ 号扣减积分，余额不足时失败。
     *
     * @param qqId   QQ 号
     * @param amount 扣减金额（必须为正）
     * @param action 操作标识
     * @param detail 操作详情
     * @return {@code true} 表示扣减成功
     */
    @Override
    public synchronized boolean deductPoints(long qqId, int amount, String action, String detail) {
        if (amount <= 0) return true;
        int balance = getPoints(qqId);
        if (balance < amount) return false;
        long now = System.currentTimeMillis();
        String sql = "UPDATE " + tablePoints
            + " SET balance = balance - ?, total_spent = total_spent + ?, updated_at = ? WHERE qq_id = ? AND balance >= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setInt(2, amount);
            ps.setLong(3, now);
            ps.setLong(4, qqId);
            ps.setInt(5, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("QQBot 扣减积分失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 查询指定 QQ 号最近一次签到记录。
     *
     * @param qqId QQ 号
     * @return 签到记录，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public SignInRecord getLastSignIn(long qqId) {
        String sql = "SELECT qq_id, sign_date, streak, signed_at FROM " + tableSignin
            + " WHERE qq_id = ? ORDER BY sign_date DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SignInRecord(
                        rs.getLong("qq_id"),
                        rs.getString("sign_date"),
                        rs.getInt("streak"),
                        rs.getLong("signed_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询签到记录失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 判断指定 QQ 号在给定日期是否已签到。
     *
     * @param qqId QQ 号
     * @param date 日期字符串（如 {@code yyyy-MM-dd}）
     * @return {@code true} 表示已签到
     */
    @Override
    public boolean hasSignedOn(long qqId, String date) {
        String sql = "SELECT 1 FROM " + tableSignin + " WHERE qq_id = ? AND sign_date = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("QQBot 检查签到状态失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 写入一条签到记录。
     *
     * @param qqId    QQ 号
     * @param date    签到日期
     * @param streak  连续签到天数
     * @param signedAt 签到时间戳（毫秒）
     */
    @Override
    public void recordSignIn(long qqId, String date, int streak, long signedAt) {
        String sql = "INSERT INTO " + tableSignin + " (qq_id, sign_date, streak, signed_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, date);
            ps.setInt(3, streak);
            ps.setLong(4, signedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 写入签到记录失败: " + e.getMessage());
        }
    }

    /**
     * 查询积分排行榜（按余额降序）。
     *
     * @param limit 最大返回条数
     * @return 积分账户列表
     */
    @Override
    public List<PointAccount> getPointsLeaderboard(int limit) {
        String sql = "SELECT qq_id, balance, total_earned, total_spent FROM " + tablePoints
            + " ORDER BY balance DESC LIMIT ?";
        List<PointAccount> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PointAccount(
                        rs.getLong("qq_id"),
                        rs.getInt("balance"),
                        rs.getInt("total_earned"),
                        rs.getInt("total_spent")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询积分榜失败: " + e.getMessage());
        }
        return list;
    }

    /**
     * 统计指定 QQ 号今日对某奖品的兑换次数。
     *
     * @param qqId    QQ 号
     * @param prizeId 奖品 ID
     * @param date    日期字符串
     * @return 今日已兑换次数
     */
    @Override
    public int countRedeemToday(long qqId, String prizeId, String date) {
        String sql = "SELECT COUNT(*) FROM " + tableRedeemLog
            + " WHERE qq_id = ? AND prize_id = ? AND redeem_date = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, prizeId);
            ps.setString(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 统计今日兑换失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 写入一条兑换流水记录。
     *
     * @param qqId    QQ 号
     * @param prizeId 奖品 ID
     * @param cost    消耗积分
     * @param date    兑换日期
     */
    @Override
    public void logRedeem(long qqId, String prizeId, int cost, String date) {
        String sql = "INSERT INTO " + tableRedeemLog + " (qq_id, prize_id, cost, redeem_date, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, prizeId);
            ps.setInt(3, cost);
            ps.setString(4, date);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 写入兑换流水失败: " + e.getMessage());
        }
    }

    // ─── 黑名单 ────────────────────────────────────────

    /**
     * 将指定 QQ 号加入黑名单（已存在则更新）。
     *
     * @param qqId    被拉黑的 QQ 号
     * @param addedBy 操作者 QQ 号
     */
    @Override
    public void addBlacklist(long qqId, long addedBy) {
        String sql = "INSERT OR REPLACE INTO " + tableBlacklist + " (qq_id, added_by, added_at) VALUES (?, ?, ?)";
        if (isMysql()) {
            sql = "INSERT INTO " + tableBlacklist + " (qq_id, added_by, added_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE added_by=VALUES(added_by), added_at=VALUES(added_at)";
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setLong(2, addedBy);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 添加黑名单失败: " + e.getMessage());
        }
    }

    /**
     * 将指定 QQ 号移出黑名单。
     *
     * @param qqId 待移除的 QQ 号
     */
    @Override
    public void removeBlacklist(long qqId) {
        String sql = "DELETE FROM " + tableBlacklist + " WHERE qq_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 移除黑名单失败: " + e.getMessage());
        }
    }

    /**
     * 判断指定 QQ 号是否在黑名单中。
     *
     * @param qqId QQ 号
     * @return {@code true} 表示已被拉黑
     */
    @Override
    public boolean isBlacklisted(long qqId) {
        String sql = "SELECT 1 FROM " + tableBlacklist + " WHERE qq_id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询黑名单失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 获取全部黑名单 QQ 号列表（按添加时间倒序）。
     *
     * @return QQ 号列表
     */
    @Override
    public List<Long> getBlacklist() {
        List<Long> list = new ArrayList<>();
        String sql = "SELECT qq_id FROM " + tableBlacklist + " ORDER BY added_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            logger.warning("QQBot 获取黑名单列表失败: " + e.getMessage());
        }
        return list;
    }

    // ─── 红包实现 ────────────────────────────────────────

    /**
     * 创建一个红包记录并返回自增 ID。
     *
     * @param senderQq    发送者 QQ 号
     * @param groupId     目标群号
     * @param totalAmount 总积分金额
     * @param count       红包个数
     * @param expireAt    过期时间戳（毫秒）
     * @return 红包 ID，失败返回 {@code -1}
     */
    @Override
    public long createRedPacket(long senderQq, long groupId, int totalAmount, int count, long expireAt) {
        String sql = "INSERT INTO " + tableRedPackets
            + " (sender_qq, group_id, total_amount, remaining_amount, count, claimed_count, expire_at, created_at)"
            + " VALUES (?, ?, ?, ?, ?, 0, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            ps.setLong(1, senderQq);
            ps.setLong(2, groupId);
            ps.setInt(3, totalAmount);
            ps.setInt(4, totalAmount);
            ps.setInt(5, count);
            ps.setLong(6, expireAt);
            ps.setLong(7, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warning("QQBot 创建红包失败: " + e.getMessage());
        }
        return -1;
    }

    /**
     * 查询指定群中最新一个仍可领取的红包。
     *
     * @param groupId 目标群号
     * @return 红包记录，无可用红包返回 {@code null}
     */
    @Override
    @Nullable
    public RedPacket getActiveRedPacket(long groupId) {
        String sql = "SELECT id, sender_qq, group_id, total_amount, remaining_amount, count, claimed_count, expire_at, created_at"
            + " FROM " + tableRedPackets
            + " WHERE group_id = ? AND expire_at > ? AND remaining_amount > 0 AND claimed_count < count"
            + " ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RedPacket(
                        rs.getLong("id"),
                        rs.getLong("sender_qq"),
                        rs.getLong("group_id"),
                        rs.getInt("total_amount"),
                        rs.getInt("remaining_amount"),
                        rs.getInt("count"),
                        rs.getInt("claimed_count"),
                        rs.getLong("expire_at"),
                        rs.getLong("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询红包失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 尝试抢红包（事务操作），含重复领取检测与随机金额分配。
     *
     * @param redPacketId 红包 ID
     * @param claimerQq   领取者 QQ 号
     * @return 抢到的积分金额；{@code -1} 表示抢失败；{@code -2} 表示已领过；{@code -3} 表示红包不可用
     */
    @Override
    public int tryClaimRedPacket(long redPacketId, long claimerQq) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String checkSql = "SELECT 1 FROM " + tableRedPacketClaims
                    + " WHERE red_packet_id = ? AND claimer_qq = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setLong(1, redPacketId);
                    ps.setLong(2, claimerQq);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            conn.rollback();
                            return -2;
                        }
                    }
                }

                RedPacket rp;
                String selectSql = "SELECT remaining_amount, claimed_count, count, expire_at FROM " + tableRedPackets
                    + " WHERE id = ? AND expire_at > ? AND remaining_amount > 0 AND claimed_count < count";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setLong(1, redPacketId);
                    ps.setLong(2, System.currentTimeMillis());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return -3;
                        }
                        rp = new RedPacket(redPacketId, 0, 0, 0,
                            rs.getInt("remaining_amount"), rs.getInt("count"),
                            rs.getInt("claimed_count"), rs.getLong("expire_at"), 0);
                    }
                }

                int remaining = rp.remainingAmount();
                int left = rp.count() - rp.claimedCount();
                int amount = left <= 1 ? remaining : 1 + (int) (Math.random() * (remaining - left + 1));

                String updateSql = "UPDATE " + tableRedPackets
                    + " SET remaining_amount = remaining_amount - ?, claimed_count = claimed_count + 1"
                    + " WHERE id = ? AND remaining_amount >= ? AND claimed_count < count";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setLong(2, redPacketId);
                    ps.setInt(3, amount);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return -1;
                    }
                }

                String insertSql = "INSERT INTO " + tableRedPacketClaims
                    + " (red_packet_id, claimer_qq, amount, claimed_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, redPacketId);
                    ps.setLong(2, claimerQq);
                    ps.setInt(3, amount);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }

                creditPointsOnConnection(conn, claimerQq, amount);
                conn.commit();
                return amount;
            } catch (SQLException exception) {
                conn.rollback();
                logger.warning("QQBot 抢红包事务失败: " + exception.getMessage());
                return -1;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            logger.warning("QQBot 抢红包失败: " + exception.getMessage());
            return -1;
        }
    }

    private void creditPointsOnConnection(Connection conn, long qqId, int delta) throws SQLException {
        if (delta <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        String upsert = isMysql()
            ? "INSERT INTO " + tablePoints + " (qq_id, balance, total_earned, total_spent, updated_at) "
                + "VALUES (?, ?, ?, 0, ?) "
                + "ON DUPLICATE KEY UPDATE balance = balance + ?, total_earned = total_earned + ?, updated_at = ?"
            : "INSERT INTO " + tablePoints + " (qq_id, balance, total_earned, total_spent, updated_at) "
                + "VALUES (?, ?, ?, 0, ?) "
                + "ON CONFLICT(qq_id) DO UPDATE SET balance = balance + ?, total_earned = total_earned + ?, updated_at = ?";
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setLong(1, qqId);
            ps.setInt(2, delta);
            ps.setInt(3, delta);
            ps.setLong(4, now);
            ps.setInt(5, delta);
            ps.setInt(6, delta);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    /**
     * 退还所有已过期且仍有余额的红包积分给发送者。
     */
    @Override
    public void refundExpiredRedPackets() {
        String selectSql = "SELECT id, sender_qq, remaining_amount FROM " + tableRedPackets
            + " WHERE expire_at <= ? AND remaining_amount > 0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long senderQq = rs.getLong("sender_qq");
                    int remaining = rs.getInt("remaining_amount");
                    addPoints(senderQq, remaining, "redpacket_refund", "红包过期退款 #" + id);
                    String clearSql = "UPDATE " + tableRedPackets + " SET remaining_amount = 0 WHERE id = ?";
                    try (PreparedStatement upd = conn.prepareStatement(clearSql)) {
                        upd.setLong(1, id);
                        upd.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 退还过期红包失败: " + e.getMessage());
        }
    }

    // ─── 活跃度实现 ──────────────────────────────────────

    /**
     * 记录一条群消息活跃度（upsert，消息计数 +1）。
     *
     * @param qqId    发言者 QQ 号
     * @param groupId 群号
     * @param date    日期字符串
     */
    @Override
    public void recordActivity(long qqId, long groupId, String date) {
        String upsert = isMysql()
            ? "INSERT INTO " + tableActivity + " (qq_id, group_id, activity_date, message_count) VALUES (?, ?, ?, 1)"
                + " ON DUPLICATE KEY UPDATE message_count = message_count + 1"
            : "INSERT INTO " + tableActivity + " (qq_id, group_id, activity_date, message_count) VALUES (?, ?, ?, 1)"
                + " ON CONFLICT(qq_id, group_id, activity_date) DO UPDATE SET message_count = message_count + 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setLong(1, qqId);
            ps.setLong(2, groupId);
            ps.setString(3, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("QQBot 记录活跃度失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定日期范围内的群活跃度排行榜（按消息数降序）。
     *
     * @param dateStart 起始日期（含）
     * @param dateEnd   结束日期（含）
     * @param limit     最大返回条数
     * @return 活跃度记录列表
     */
    @Override
    public List<ActivityRecord> getActivityLeaderboard(String dateStart, String dateEnd, int limit) {
        String sql = "SELECT qq_id, SUM(message_count) as cnt FROM " + tableActivity
            + " WHERE activity_date >= ? AND activity_date <= ?"
            + " GROUP BY qq_id ORDER BY cnt DESC LIMIT ?";
        List<ActivityRecord> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dateStart);
            ps.setString(2, dateEnd);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActivityRecord(rs.getLong("qq_id"), rs.getInt("cnt")));
                }
            }
        } catch (SQLException e) {
            logger.warning("QQBot 查询活跃度排行失败: " + e.getMessage());
        }
        return list;
    }
}
