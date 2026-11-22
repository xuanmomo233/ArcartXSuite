package xuanmo.arcartxsuite.qqbot.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    private static final int ACTIVITY_ROWS_PER_STATEMENT = 128;

    private volatile BindingIndex bindingIndex = new BindingIndex(List.of(), List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

    private record BindingIndex(List<?> authority, List<QQBotBinding> rows,
        java.util.Map<Long, List<QQBotBinding>> byQq, java.util.Map<UUID, QQBotBinding> byPlayer,
        java.util.Map<String, QQBotBinding> byName) {}

    private BindingIndex bindings() {
        var service=xuanmo.arcartxsuite.api.AxsCapabilities.get(xuanmo.arcartxsuite.api.capability.BindingService.class);
        List<xuanmo.arcartxsuite.api.capability.BindingService.Binding> snapshot = service == null ? List.of() : service.snapshot();
        BindingIndex cached = bindingIndex;
        if (cached.authority() == snapshot) return cached;
        synchronized (this) {
            cached = bindingIndex;
            if (cached.authority() == snapshot) return cached;
            var rows = snapshot.stream().map(b -> new QQBotBinding(b.qqId(), b.profileId(), b.profileName(), b.boundAt()))
                .sorted(java.util.Comparator.comparingLong(QQBotBinding::boundAt).reversed()).toList();
            java.util.Map<Long, List<QQBotBinding>> qq = new java.util.HashMap<>();
            java.util.Map<UUID, QQBotBinding> players = new java.util.HashMap<>();
            java.util.Map<String, QQBotBinding> names = new java.util.HashMap<>();
            for (QQBotBinding row : rows) {
                qq.computeIfAbsent(row.qqId(), ignored -> new ArrayList<>()).add(row);
                players.put(row.playerUuid(), row);
                names.put(row.playerName().toLowerCase(java.util.Locale.ROOT), row);
            }
            qq.replaceAll((key, value) -> List.copyOf(value));
            return bindingIndex = new BindingIndex(snapshot, rows, java.util.Map.copyOf(qq),
                java.util.Map.copyOf(players), java.util.Map.copyOf(names));
        }
    }

    private List<QQBotBinding> bindingRows() { return bindings().rows(); }

    private final String tablePoints;
    private final String tableSignin;
    private final String tableRedeemLog;
    private final String tableBlacklist;
    private final String tableRedPackets;
    private final String tableRedPacketClaims;
    private final String tableActivity;
    private final QQBotEconomyStore economy;
    private final Object blacklistLock = new Object();
    private volatile java.util.Set<Long> blacklistSnapshot = java.util.Set.of();
    private volatile long blacklistExpiresAt;

    public JdbcQQBotRepository(DataSource dataSource, StorageDescriptor descriptor,
                               File dataFolder, Logger logger) {
        super("AXS-QQBot", dataSource, descriptor, dataFolder, logger);
        String prefix = isMysql() ? tablePrefix() : "axs_qqbot_";
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,48}")) throw new IllegalArgumentException("非法的QQBot表前缀");
        this.tablePoints = prefix + "points";
        this.tableSignin = prefix + "signin";
        this.tableRedeemLog = prefix + "redeem_log";
        this.tableBlacklist = prefix + "blacklist";
        this.tableRedPackets = prefix + "red_packets";
        this.tableRedPacketClaims = prefix + "red_packet_claims";
        this.tableActivity = prefix + "activity";
        this.economy = new QQBotEconomyStore(dataSource, isMysql(), prefix);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
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
        economy.initialize(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of();
    }

    @Override
    protected List<String> allTables() {
        List<String> tables = new ArrayList<>(List.of(tablePoints, tableSignin, tableRedeemLog, tableBlacklist,
            tableRedPackets, tableRedPacketClaims, tableActivity));
        tables.addAll(economy.tables());
        return List.copyOf(tables);
    }

    /**
     * 关闭仓库，释放连接池等资源。
     */
    @Override
    public void close() {
        try { economy.close(); }
        catch (StorageFailure e) { logger.warning("QQBot 节点关闭状态未能写入，遗留兑换将在租约过期后冻结"); }
        shutdown();
    }

    public java.util.concurrent.CompletionStage<Void> retire() {
        return economy.retire().whenComplete((ignored, failure) -> {
            if (failure != null) logger.warning("QQBot 节点关闭状态未能写入，遗留兑换将在租约过期后冻结");
        });
    }

    public xuanmo.arcartxsuite.api.storage.MigrationResult migrateSafely(StorageDescriptor target, boolean overwrite) {
        boolean safe;
        try { safe = economy.canMigrate(); }
        catch (StorageFailure e) { safe = false; }
        if (!safe) {
            var result = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
            result.addError("QQBot 不支持在线迁移。请先处理未结案兑换、停止所有节点的机器人写入，再离线迁移；奖励审计须一并保留。");
            return result;
        }
        return migrateData(target, overwrite);
    }

    /**
     * 插入一条 QQ-游戏绑定记录。
     *
     * @param qqId       QQ 号
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     */


    /**
     * 按 QQ 号删除绑定记录。
     *
     * @param qqId 待解绑的 QQ 号
     */


    /**
     * 按玩家 UUID 删除绑定记录。
     *
     * @param playerUuid 待解绑的玩家 UUID
     */


    /**
     * 按 QQ 号查询绑定记录（取第一条）。
     *
     * @param qqId QQ 号
     * @return 绑定记录，未找到返回 {@code null}
     */
    @Override
    @Nullable
    public QQBotBinding findByQq(long qqId) {
        return findAllByQq(qqId).stream().findFirst().orElse(null);
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
        return playerUuid == null ? null : bindings().byPlayer().get(playerUuid);
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
        return playerName == null ? null : bindings().byName().get(playerName.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 查询指定 QQ 号下的全部绑定记录。
     *
     * @param qqId QQ 号
     * @return 绑定记录列表，可能为空
     */
    @Override
    public List<QQBotBinding> findAllByQq(long qqId) {
        return bindings().byQq().getOrDefault(qqId, List.of());
    }

    /**
     * 统计指定 QQ 号下的绑定数量。
     *
     * @param qqId QQ 号
     * @return 绑定数量，查询失败返回 {@code 0}
     */
    @Override
    public int countByQq(long qqId) {
        return findAllByQq(qqId).size();
    }

    /**
     * 统计绑定表中的总记录数。
     *
     * @return 总绑定数，查询失败返回 {@code 0}
     */
    @Override
    public int countBindings() {
        return bindingRows().size();
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
        if(page<0 || pageSize<1 || pageSize>1000) return List.of();
        return bindingRows().stream().skip((long)page*pageSize).limit(pageSize).toList();
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
        if(keyword==null || limit<1 || limit>1000) return List.of();
        String key=keyword.toLowerCase(java.util.Locale.ROOT);
        return bindingRows().stream().filter(b -> b.playerName().toLowerCase(java.util.Locale.ROOT).contains(key) || Long.toString(b.qqId()).contains(key)).limit(limit).toList();
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
    public int addPoints(long qqId, int delta, String action, String detail) {
        return economy.adjust(qqId, delta, action, detail);
    }

    @Override public SignInOutcome signInAtomic(long qq, String date, int base, int bonus, int maxBonus) {
        return economy.signIn(qq, date, base, bonus, maxBonus);
    }
    @Override public TransferOutcome transferPointsAtomic(long from, long to, int amount) {
        return economy.transfer(from, to, amount);
    }
    @Override public RedemptionReservation reserveRedemption(long qq, String prize, UUID uuid, String name,
        int cost, String date, int limit, List<String> presets) {
        return economy.reserve(qq, prize, uuid, name, cost, date, limit, presets);
    }
    @Override public boolean beginRedemptionItem(String id, int ordinal) { return economy.beginItem(id, ordinal); }
    @Override public void recordRedemptionItem(String id, int ordinal, RedemptionItemState state, String detail) {
        economy.recordItem(id, ordinal, state, detail);
    }
    @Override public Redemption finishRedemption(String id) { return economy.finish(id); }
    @Override public void freezeRedemption(String id, String note) { economy.freeze(id, note); }
    @Override public Redemption getRedemption(String id) { return economy.get(id); }
    @Override public List<Redemption> listRedemptions(int limit) { return economy.pending(limit); }
    @Override public MutationStatus resolveRedemption(String id, RedemptionResolution resolution, String note) {
        return economy.resolve(id, resolution, note);
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
    public boolean deductPoints(long qqId, int amount, String action, String detail) {
        return economy.deduct(qqId, amount, action, detail);
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
            + " WHERE qq_id = ? AND LOWER(prize_id) = ? AND redeem_date = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, qqId);
            ps.setString(2, prizeId.toLowerCase(java.util.Locale.ROOT));
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
            throw new StorageFailure("QQBot 添加黑名单失败", e);
        } finally {
            synchronized (blacklistLock) { blacklistExpiresAt = 0; }
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
            throw new StorageFailure("QQBot 移除黑名单失败", e);
        } finally {
            synchronized (blacklistLock) { blacklistExpiresAt = 0; }
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
        return blacklistSnapshot().contains(qqId);
    }

    /**
     * 获取全部黑名单 QQ 号列表（按添加时间倒序）。
     *
     * @return QQ 号列表
     */
    @Override
    public List<Long> getBlacklist() {
        return blacklistSnapshot().stream().sorted().toList();
    }

    private java.util.Set<Long> blacklistSnapshot() {
        long now = System.nanoTime();
        if (now < blacklistExpiresAt) return blacklistSnapshot;
        synchronized (blacklistLock) {
            now = System.nanoTime();
            if (now < blacklistExpiresAt) return blacklistSnapshot;
            java.util.Set<Long> loaded = java.util.Set.copyOf(loadBlacklist());
            blacklistSnapshot = loaded;
            blacklistExpiresAt = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            return loaded;
        }
    }

    private List<Long> loadBlacklist() {
        List<Long> list = new ArrayList<>();
        String sql = "SELECT qq_id FROM " + tableBlacklist + " ORDER BY added_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            // Do not silently turn a failed authorization read into an empty deny list.
            throw new StorageFailure("QQBot 获取黑名单列表失败", e);
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
    @Deprecated
    public long createRedPacket(long senderQq, long groupId, int totalAmount, int count, long expireAt) {
        // Legacy entry point now includes the debit; callers must not pre-deduct points.
        RedPacketSendOutcome result = createRedPacketAtomic(senderQq, groupId, totalAmount, count, expireAt);
        return result.status() == MutationStatus.SUCCESS ? result.id() : -1;
    }

    @Override
    public RedPacketSendOutcome createRedPacketAtomic(long senderQq, long groupId, int totalAmount, int count, long expireAt) {
        return economy.sendRedPacket(senderQq, groupId, totalAmount, count, expireAt);
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
        return economy.claimRedPacket(redPacketId, claimerQq);
    }

    /**
     * 退还所有已过期且仍有余额的红包积分给发送者。
     */
    @Override
    public void refundExpiredRedPackets() {
        economy.refundRedPackets();
    }

    // ─── 活跃度实现 ──────────────────────────────────────

    @Override
    public void recordActivities(Map<ActivityKey, Integer> increments) {
        if (increments.isEmpty()) return;
        var rows = new ArrayList<>(increments.entrySet());
        if (isMysql()) {
            // Shared MySQL writers acquire row locks in the same order.
            rows.sort(Map.Entry.comparingByKey(Comparator.comparingLong(ActivityKey::qqId)
                .thenComparingLong(ActivityKey::groupId).thenComparing(ActivityKey::date)));
        }
        boolean uncommitted = true;
        boolean commitStarted = false;
        boolean committed = false;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            uncommitted = false;
            try {
                for (int start = 0; start < rows.size(); start += ACTIVITY_ROWS_PER_STATEMENT) {
                    int end = Math.min(start + ACTIVITY_ROWS_PER_STATEMENT, rows.size());
                    writeActivityRows(conn, rows.subList(start, end));
                }
                commitStarted = true;
                conn.commit();
                committed = true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                    uncommitted = !commitStarted;
                } catch (SQLException | RuntimeException rollback) {
                    if (rollback != failure) failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } catch (SQLException | RuntimeException e) {
            if (committed) {
                logger.warning("QQBot 活跃度已提交，关闭连接失败: " + e.getMessage());
                return;
            }
            throw new StorageFailure("QQBot 批量记录活跃度失败", e, uncommitted);
        }
    }

    private void writeActivityRows(Connection conn, List<Map.Entry<ActivityKey, Integer>> rows) throws SQLException {
        String values = "(?, ?, ?, ?)".concat(", (?, ?, ?, ?)".repeat(rows.size() - 1));
        String update = isMysql()
            ? " ON DUPLICATE KEY UPDATE message_count = message_count + VALUES(message_count)"
            : " ON CONFLICT(qq_id, group_id, activity_date) DO UPDATE SET message_count = message_count + excluded.message_count";
        String sql = "INSERT INTO " + tableActivity + " (qq_id, group_id, activity_date, message_count) VALUES " + values + update;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int parameter = 1;
            for (var entry : rows) {
                ActivityKey key = entry.getKey();
                ps.setLong(parameter++, key.qqId());
                ps.setLong(parameter++, key.groupId());
                ps.setString(parameter++, key.date().toString());
                ps.setInt(parameter++, entry.getValue());
            }
            ps.executeUpdate();
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
