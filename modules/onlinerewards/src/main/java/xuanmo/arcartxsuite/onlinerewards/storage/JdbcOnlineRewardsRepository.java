package xuanmo.arcartxsuite.onlinerewards.storage;

import java.io.File;
import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

/**
 * 在线奖励 JDBC 存储仓库。
 * <p>
 * 属于 {@code onlinerewards} 模块，继承 {@link AbstractModuleRepository} 实现 {@link OnlineRewardsRepository}，
 * 负责玩家状态、签到记录、全服签到目标的持久化与排行榜查询，支持 SQLite 与 MySQL 双方言。
 */
public final class JdbcOnlineRewardsRepository extends AbstractModuleRepository implements OnlineRewardsRepository {
    // Keep every statement below SQLite's 999-bind limit, independently of JDBC batching flags.
    private static final int MAX_SNAPSHOT_ROWS = 47;
    private static final int MAX_MARKER_ROWS = 128;
    private static final int MAX_DELIVERY_ROWS = 128;
    private static final int MAX_PROGRESS_REQUESTS = 128;
    private static final long MAX_STATEMENT_BYTES = 512L * 1024;
    private static final String PLAYER_STATE_INSERT = """
        INSERT INTO online_rewards_players (
            player_uuid, player_name, reward_date, online_minutes, reward_stage,
            week_key, week_minutes, month_key, month_minutes, total_minutes,
            last_sign_in_date, sign_in_streak, sign_in_total, makeup_cards, time_bonus_remainder,
            offline_savings_minutes, offline_savings_day, offline_savings_earned_minutes, offline_savings_credited_minutes,
            claimed_weekly_reward_ids, claimed_monthly_reward_ids
        )
        VALUES
        """;
    private static final String PLAYER_STATE_ROW = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_PLAYER_STATE = PLAYER_STATE_INSERT + PLAYER_STATE_ROW + "\n";
    private static final String SELECT_PLAYER_STATE = """
        SELECT player_uuid, player_name, reward_date, online_minutes, reward_stage, week_key, week_minutes,
               month_key, month_minutes, total_minutes, last_sign_in_date, sign_in_streak, sign_in_total,
               makeup_cards, time_bonus_remainder, offline_savings_minutes, offline_savings_day,
               offline_savings_earned_minutes, offline_savings_credited_minutes,
               claimed_weekly_reward_ids, claimed_monthly_reward_ids
        FROM online_rewards_players
        """;
    private static final String DELIVERY_INSERT = """
        INSERT INTO online_rewards_deliveries (player_uuid, batch_id, step, kind, payload, context, status)
        VALUES
        """;
    private static final String DELIVERY_ROW = "(?, ?, ?, ?, ?, ?, 'READY')";
    private static final String INSERT_DELIVERY = DELIVERY_INSERT + DELIVERY_ROW;
    private static final String NO_ACTIVE_ITEM_CHECKPOINT = """
        AND (kind <> 'ITEM' OR NOT EXISTS (SELECT 1 FROM online_rewards_item_checkpoints checkpoint
            WHERE checkpoint.player_uuid = online_rewards_deliveries.player_uuid))
        """;

    public JdbcOnlineRewardsRepository(DataSource dataSource, StorageDescriptor descriptor,
                                       File dataFolder, Logger logger) {
        super("AXS-OnlineRewards", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createOrMigrateTable(conn);
        ensureIndexes(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("online_rewards_players", "online_rewards_sign_ins", "online_rewards_deliveries",
            "online_rewards_presence", "online_rewards_time_batches", "online_rewards_item_checkpoints", "online_rewards_attendance");
    }

    @Override
    protected List<String> allTables() {
        return List.of("online_rewards_players", "online_rewards_sign_ins", "online_rewards_server_goals",
            "online_rewards_schema_meta", "online_rewards_deliveries", "online_rewards_presence", "online_rewards_time_batches",
            "online_rewards_item_checkpoints", "online_rewards_attendance");
    }

    /**
     * 加载玩家运行时状态，不存在时返回默认状态。
     *
     * @param playerUuid 玩家唯一 id
     * @return 玩家状态
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public OnlineRewardsPlayerState loadState(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PLAYER_STATE + "WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return readPlayerState(resultSet);
            }
        }
        return new OnlineRewardsPlayerState();
    }

    private OnlineRewardsPlayerState readPlayerState(ResultSet row) throws SQLException {
        var state = new OnlineRewardsPlayerState(
            nullToEmpty(row.getString("player_name")), nullToEmpty(row.getString("reward_date")),
            row.getInt("online_minutes"), row.getInt("reward_stage"), nullToEmpty(row.getString("week_key")),
            row.getInt("week_minutes"), nullToEmpty(row.getString("month_key")), row.getInt("month_minutes"),
            row.getInt("total_minutes"), nullToEmpty(row.getString("last_sign_in_date")), row.getInt("sign_in_streak"),
            row.getInt("sign_in_total"), row.getInt("makeup_cards"), row.getDouble("time_bonus_remainder"),
            row.getInt("offline_savings_minutes"), parseIdSet(nullToEmpty(row.getString("claimed_weekly_reward_ids"))),
            parseIdSet(nullToEmpty(row.getString("claimed_monthly_reward_ids"))));
        state.setOfflineSavingsDay(row.getString("offline_savings_day"));
        state.setOfflineSavingsEarnedMinutes(row.getInt("offline_savings_earned_minutes"));
        state.setOfflineSavingsCreditedMinutes(row.getInt("offline_savings_credited_minutes"));
        return state;
    }

    /**
     * 保存或更新玩家运行时状态。
     *
     * @param playerUuid 玩家唯一 id
     * @param state      玩家状态
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void saveState(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException {
        try (Connection connection = getConnection()) {
            writeState(connection, upsertSql(true), playerUuid, state, state.makeupCards());
        }
    }

    @Override
    public void saveProgress(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException {
        try (Connection connection = getConnection()) {
            writeState(connection, upsertSql(false), playerUuid, state, 0);
        }
    }

    @Override
    public void saveProgressBatch(List<ProgressCommit> commits) throws SQLException {
        if (commits.isEmpty()) return;
        List<ProgressCommit> ordered = commits.stream().sorted(Comparator.comparing(commit -> commit.playerUuid().toString())).toList();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                saveSnapshots(connection, ordered);
                saveTimeRewards(connection, ordered);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    @Override
    public List<ProgressResult> updateProgress(List<ProgressRequest> requests) throws SQLException {
        if (requests.isEmpty()) return List.of();
        if (requests.size() > MAX_PROGRESS_REQUESTS) throw new IllegalArgumentException("Online rewards progress batch too large");
        var ordered = requests.stream().sorted(Comparator.comparing(request -> request.playerUuid().toString())).toList();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                var states = lockProgressPlayers(connection, ordered);
                var clocks = readAttendance(connection, ordered);
                var commits = new ArrayList<ProgressCommit>();
                var cursors = new ArrayList<ProgressRequest>();
                var results = new ArrayList<ProgressResult>(ordered.size());
                for (var request : ordered) {
                    var clock = request.attendance();
                    long previous = clock == null ? 0L : clocks.getOrDefault(clock.connectionId(), clock.initialMinutes());
                    long delta = clock == null ? 0L : Math.max(0L, clock.eligibleMinutes() - previous);
                    var state = states.get(request.playerUuid());
                    var plan = request.mutation().apply(state, delta);
                    if (plan.changed()) commits.add(new ProgressCommit(request.playerUuid(), state, plan.rewards()));
                    if (delta > 0 && plan.changed()) cursors.add(request);
                    results.add(new ProgressResult(request.playerUuid(), state, plan.changed(), !plan.rewards().isEmpty(),
                        delta > 0 && plan.changed() ? clock.eligibleMinutes() : previous));
                }
                saveSnapshots(connection, commits);
                saveTimeRewards(connection, commits);
                saveAttendance(connection, cursors);
                connection.commit();
                return results;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private Map<UUID, OnlineRewardsPlayerState> lockProgressPlayers(Connection connection, List<ProgressRequest> requests) throws SQLException {
        // The upsert takes player locks in a stable order and acquires SQLite's writer before any reads.
        String insert = "INSERT INTO online_rewards_players (player_uuid, player_name, claimed_weekly_reward_ids, claimed_monthly_reward_ids) VALUES "
            + rows("(?, ?, '', '')", requests.size())
            + (isMysql() ? " ON DUPLICATE KEY UPDATE player_uuid = online_rewards_players.player_uuid" : " ON CONFLICT(player_uuid) DO NOTHING");
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            int parameter = 1;
            for (var request : requests) {
                statement.setString(parameter++, request.playerUuid().toString());
                statement.setString(parameter++, request.playerName());
            }
            statement.executeUpdate();
        }
        var states = new HashMap<UUID, OnlineRewardsPlayerState>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PLAYER_STATE + "WHERE player_uuid IN ("
                + rows("?", requests.size()) + ")" + (isMysql() ? " FOR UPDATE" : ""))) {
            int parameter = 1;
            for (var request : requests) statement.setString(parameter++, request.playerUuid().toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) states.put(UUID.fromString(rows.getString("player_uuid")), readPlayerState(rows));
            }
        }
        return states;
    }

    private Map<UUID, Long> readAttendance(Connection connection, List<ProgressRequest> requests) throws SQLException {
        var clocks = requests.stream().map(ProgressRequest::attendance)
            .filter(clock -> clock != null && clock.eligibleMinutes() > 0).toList();
        if (clocks.isEmpty()) return Map.of();
        var previous = new HashMap<UUID, Long>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT connection_id, eligible_minutes FROM online_rewards_attendance"
                + " WHERE connection_id IN (" + rows("?", clocks.size()) + ")")) {
            int index = 1;
            for (var clock : clocks) statement.setString(index++, clock.connectionId().toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) previous.put(UUID.fromString(rows.getString(1)), rows.getLong(2));
            }
        }
        return previous;
    }

    private void saveAttendance(Connection connection, List<ProgressRequest> requests) throws SQLException {
        if (requests.isEmpty()) return;
        String sql = "INSERT INTO online_rewards_attendance (connection_id, player_uuid, eligible_minutes) VALUES "
            + rows("(?, ?, ?)", requests.size())
            + (isMysql() ? " ON DUPLICATE KEY UPDATE eligible_minutes = GREATEST(eligible_minutes, VALUES(eligible_minutes))"
                : " ON CONFLICT(connection_id) DO UPDATE SET eligible_minutes = MAX(eligible_minutes, excluded.eligible_minutes)");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (var request : requests) {
                statement.setString(index++, request.attendance().connectionId().toString());
                statement.setString(index++, request.playerUuid().toString());
                statement.setLong(index++, request.attendance().eligibleMinutes());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void forgetAttendance(UUID playerUuid, UUID connectionId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM online_rewards_attendance WHERE connection_id = ? AND player_uuid = ?")) {
            statement.setString(1, connectionId.toString());
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private void saveSnapshots(Connection connection, List<ProgressCommit> ordered) throws SQLException {
        int start = 0;
        while (start < ordered.size()) {
            long bytes = 0;
            int end = start;
            while (end < ordered.size() && end - start < MAX_SNAPSHOT_ROWS) {
                OnlineRewardsPlayerState state = ordered.get(end).state();
                long rowBytes = 256 + boundBytes(state.playerName(), state.rewardDate(), state.weekKey(),
                    state.monthKey(), state.lastSignInDate(), state.offlineSavingsDay())
                    + state.claimedWeeklyRewardIds().stream().mapToLong(id -> boundBytes(id)).sum()
                    + state.claimedMonthlyRewardIds().stream().mapToLong(id -> boundBytes(id)).sum();
                if (end > start && bytes + rowBytes > MAX_STATEMENT_BYTES) break;
                bytes += rowBytes;
                end++;
            }
            try (PreparedStatement statement = connection.prepareStatement(upsertSql(false, end - start))) {
                int parameter = 1;
                for (int i = start; i < end; i++) {
                    ProgressCommit commit = ordered.get(i);
                    parameter = bindState(statement, parameter, commit.playerUuid(), commit.state(), 0);
                }
                statement.executeUpdate();
            }
            start = end;
        }
    }

    @Override
    public int enqueueRewardBatch(String batchId, Map<UUID, List<Reward>> recipients) throws SQLException {
        if (recipients.isEmpty()) return 0;
        List<TimePlan> plans = recipients.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(entry -> new TimePlan(entry.getKey(), new RewardBatch(batchId, entry.getValue())))
            .toList();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // SQLite needs its writer lock before its first read, which the INSERT establishes.
                List<TimePlan> inserted = registerTimePlans(connection, plans, isMysql());
                saveTimeDeliveries(connection, inserted);
                connection.commit();
                return inserted.size();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private void saveTimeRewards(Connection connection, List<ProgressCommit> ordered) throws SQLException {
        List<List<TimePlan>> rounds = new ArrayList<>();
        UUID player = null;
        int round = 0;
        for (ProgressCommit commit : ordered) {
            if (!commit.playerUuid().equals(player)) {
                player = commit.playerUuid();
                round = 0;
            }
            // One plan per player per round preserves the first plan even for case-equivalent MySQL keys.
            for (RewardBatch batch : commit.rewards()) {
                if (round == rounds.size()) rounds.add(new ArrayList<>());
                rounds.get(round++).add(new TimePlan(player, batch));
            }
        }

        // Snapshot upserts serialize progress writers. Scheduled rewards also share the marker key.
        List<TimePlan> inserted = new ArrayList<>();
        for (List<TimePlan> plans : rounds) {
            inserted.addAll(registerTimePlans(connection, plans, true));
        }
        saveTimeDeliveries(connection, inserted);
    }

    private List<TimePlan> registerTimePlans(Connection connection, List<TimePlan> plans, boolean readExisting)
            throws SQLException {
        List<TimePlan> inserted = new ArrayList<>();
        for (int start = 0; start < plans.size(); start += MAX_MARKER_ROWS) {
            List<TimePlan> chunk = plans.subList(start, Math.min(start + MAX_MARKER_ROWS, plans.size()));
            List<TimePlan> missing = readExisting ? missingTimePlans(connection, chunk) : chunk;
            inserted.addAll(insertTimePlans(connection, missing));
        }
        return inserted;
    }

    private static List<TimePlan> missingTimePlans(Connection connection, List<TimePlan> plans) throws SQLException {
        var sql = new StringBuilder();
        for (int i = 0; i < plans.size(); i++) {
            if (i != 0) sql.append(" UNION ALL ");
            sql.append("SELECT ").append(i)
                .append(" WHERE EXISTS (SELECT 1 FROM online_rewards_time_batches WHERE player_uuid = ? AND batch_id = ?)");
        }
        // Compare the bound keys using the database collation and return the input positions.
        boolean[] existing = new boolean[plans.size()];
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindTimePlans(statement, plans);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) existing[rows.getInt(1)] = true;
            }
        }
        List<TimePlan> missing = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            if (!existing[i]) missing.add(plans.get(i));
        }
        return missing;
    }

    private List<TimePlan> insertTimePlans(Connection connection, List<TimePlan> missing) throws SQLException {
        if (missing.isEmpty()) return List.of();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO online_rewards_time_batches (player_uuid, batch_id) VALUES " + rows("(?, ?)", missing.size()))) {
            bindTimePlans(statement, missing);
            statement.executeUpdate();
            return missing;
        } catch (SQLException conflict) {
            if (!timePlanDuplicate(conflict)) throw conflict;
        }
        // A concurrent scheduled writer can insert a key after the read. Both engines roll back the
        // failed multi-row INSERT, so retry exact keys and retain only the rows this transaction won.
        List<TimePlan> inserted = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO online_rewards_time_batches (player_uuid, batch_id) VALUES (?, ?)")) {
            for (TimePlan plan : missing) {
                statement.setString(1, plan.player().toString());
                statement.setString(2, plan.batch().batchId());
                try {
                    statement.executeUpdate();
                    inserted.add(plan);
                } catch (SQLException conflict) {
                    if (!timePlanDuplicate(conflict)) throw conflict;
                }
            }
        }
        return inserted;
    }

    private boolean timePlanDuplicate(SQLException failure) {
        if (isMysql()) return failure.getErrorCode() == 1062;
        if (failure.getErrorCode() == 1555 || failure.getErrorCode() == 2067) return true;
        String message = failure.getMessage();
        return failure.getErrorCode() == 19 && message != null
            && (message.startsWith("[SQLITE_CONSTRAINT_PRIMARYKEY]") || message.startsWith("[SQLITE_CONSTRAINT_UNIQUE]"));
    }

    private static void bindTimePlans(PreparedStatement statement, List<TimePlan> plans) throws SQLException {
        int parameter = 1;
        for (TimePlan plan : plans) {
            statement.setString(parameter++, plan.player().toString());
            statement.setString(parameter++, plan.batch().batchId());
        }
    }

    private static void saveTimeDeliveries(Connection connection, List<TimePlan> inserted) throws SQLException {
        List<Delivery> chunk = new ArrayList<>(MAX_DELIVERY_ROWS);
        long bytes = 0;
        for (TimePlan plan : inserted) {
            RewardBatch batch = plan.batch();
            for (int step = 0; step < batch.rewards().size(); step++) {
                Reward reward = batch.rewards().get(step);
                // Account for UTF-8 and Connector/J's SQL string escaping in client-prepared statements.
                long rowBytes = 128 + boundBytes(batch.batchId(), reward.kind(), reward.value(), reward.context());
                if (!chunk.isEmpty() && (chunk.size() == MAX_DELIVERY_ROWS || bytes + rowBytes > MAX_STATEMENT_BYTES)) {
                    insertDeliveryRows(connection, chunk);
                    chunk.clear();
                    bytes = 0;
                }
                chunk.add(new Delivery(plan.player(), batch.batchId(), step, reward));
                bytes += rowBytes;
            }
        }
        if (!chunk.isEmpty()) insertDeliveryRows(connection, chunk);
    }

    private static void insertDeliveryRows(Connection connection, List<Delivery> chunk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELIVERY_INSERT + rows(DELIVERY_ROW, chunk.size()))) {
            int parameter = 1;
            for (Delivery delivery : chunk) {
                parameter = bindDelivery(statement, parameter, delivery.playerUuid(), delivery.batchId(),
                    delivery.step(), delivery.reward());
            }
            statement.executeUpdate();
        }
    }

    private static String rows(String row, int count) {
        return row + ("," + row).repeat(count - 1);
    }

    private static long boundBytes(String... values) {
        long bytes = 0;
        for (String value : values) bytes += value == null ? 4 : 6L * value.length() + 2;
        return bytes;
    }

    private record TimePlan(UUID player, RewardBatch batch) {}

    private static void executeCheckedBatch(PreparedStatement statement) throws SQLException {
        for (int count : statement.executeBatch()) {
            if (count == Statement.EXECUTE_FAILED) throw new SQLException("OnlineRewards statement batch did not complete");
        }
    }

    private static void writeState(Connection connection, String sql, UUID playerUuid, OnlineRewardsPlayerState state,
                                   int cardsOnInsert) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindState(statement, 1, playerUuid, state, cardsOnInsert);
            statement.executeUpdate();
        }
    }

    private static int bindState(PreparedStatement statement, int parameter, UUID playerUuid, OnlineRewardsPlayerState state,
                                 int cardsOnInsert) throws SQLException {
        statement.setString(parameter++, playerUuid.toString());
        statement.setString(parameter++, state.playerName());
        statement.setString(parameter++, state.rewardDate());
        statement.setInt(parameter++, state.onlineMinutes());
        statement.setInt(parameter++, state.rewardStage());
        statement.setString(parameter++, state.weekKey());
        statement.setInt(parameter++, state.weekMinutes());
        statement.setString(parameter++, state.monthKey());
        statement.setInt(parameter++, state.monthMinutes());
        statement.setInt(parameter++, state.totalMinutes());
        statement.setString(parameter++, state.lastSignInDate());
        statement.setInt(parameter++, state.signInStreak());
        statement.setInt(parameter++, state.signInTotal());
        statement.setInt(parameter++, cardsOnInsert);
        statement.setDouble(parameter++, state.timeBonusRemainder());
        statement.setInt(parameter++, state.offlineSavingsMinutes());
        statement.setString(parameter++, state.offlineSavingsDay());
        statement.setInt(parameter++, state.offlineSavingsEarnedMinutes());
        statement.setInt(parameter++, state.offlineSavingsCreditedMinutes());
        statement.setString(parameter++, String.join(",", state.claimedWeeklyRewardIds()));
        statement.setString(parameter++, String.join(",", state.claimedMonthlyRewardIds()));
        return parameter;
    }

    @Override
    public int adjustMakeupCards(UUID playerUuid, String playerName, MakeupCardOperation operation, int amount) throws SQLException {
        int safeAmount = Math.max(0, amount);
        String expression = switch (operation) {
            case ADD -> "CASE WHEN makeup_cards >= ? THEN ? ELSE makeup_cards + ? END";
            case REMOVE -> "CASE WHEN makeup_cards <= ? THEN 0 ELSE makeup_cards - ? END";
            case SET -> "?";
        };
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensurePlayerState(connection, playerUuid, playerName);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE online_rewards_players SET makeup_cards = " + expression + " WHERE player_uuid = ?")) {
                    int index = 1;
                    if (operation == MakeupCardOperation.ADD) {
                        update.setInt(index++, Integer.MAX_VALUE - safeAmount);
                        update.setInt(index++, Integer.MAX_VALUE);
                    } else if (operation == MakeupCardOperation.REMOVE) {
                        update.setInt(index++, safeAmount);
                    }
                    update.setInt(index++, safeAmount);
                    update.setString(index, playerUuid.toString());
                    update.executeUpdate();
                }
                int cards = loadMakeupCards(connection, playerUuid);
                connection.commit();
                return cards;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private static int loadMakeupCards(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT makeup_cards FROM online_rewards_players WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Math.max(0, rows.getInt(1)) : 0;
            }
        }
    }

    private void ensurePlayerState(Connection connection, UUID playerUuid, String playerName) throws SQLException {
        var defaults = new OnlineRewardsPlayerState();
        defaults.setPlayerName(playerName);
        String ifMissing = isMysql() ? "ON DUPLICATE KEY UPDATE player_uuid = online_rewards_players.player_uuid"
            : "ON CONFLICT(player_uuid) DO NOTHING";
        writeState(connection, INSERT_PLAYER_STATE + ifMissing, playerUuid, defaults, 0);
    }

    private static boolean debitMakeupCard(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE online_rewards_players SET makeup_cards = makeup_cards - 1
                WHERE player_uuid = ? AND makeup_cards > 0
                """)) {
            statement.setString(1, playerUuid.toString());
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * 加载玩家在指定日期范围内的签到日期集合。
     *
     * @param playerUuid 玩家唯一 id
     * @param fromDate   起始日期（含）
     * @param toDate     结束日期（含）
     * @return 签到日期字符串集合
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Set<String> loadSignInDates(UUID playerUuid, String fromDate, String toDate) throws SQLException {
        LinkedHashSet<String> dates = new LinkedHashSet<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT sign_in_date
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ? AND sign_in_date >= ? AND sign_in_date <= ?
                 ORDER BY sign_in_date ASC
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, fromDate == null ? "" : fromDate);
            statement.setString(3, toDate == null ? "" : toDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dates.add(resultSet.getString("sign_in_date"));
                }
            }
        }
        return Set.copyOf(dates);
    }

    /**
     * 加载玩家全部签到日期集合。
     *
     * @param playerUuid 玩家唯一 id
     * @return 签到日期字符串集合
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public Set<String> loadAllSignInDates(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection()) {
            return loadAllSignInDates(connection, playerUuid);
        }
    }

    private Set<String> loadAllSignInDates(Connection connection, UUID playerUuid) throws SQLException {
        LinkedHashSet<String> dates = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT sign_in_date
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ?
                 ORDER BY sign_in_date ASC
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dates.add(resultSet.getString("sign_in_date"));
                }
            }
        }
        return Set.copyOf(dates);
    }

    /**
     * 判断玩家在指定日期是否已有签到记录。
     *
     * @param playerUuid 玩家唯一 id
     * @param date       日期字符串
     * @return 是否已签到
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public boolean hasSignInRecord(UUID playerUuid, String date) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 """
                 SELECT 1
                 FROM online_rewards_sign_ins
                 WHERE player_uuid = ? AND sign_in_date = ?
                 """
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, date == null ? "" : date);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 保存签到记录（覆盖式写入）。
     *
     * @param playerUuid 玩家唯一 id
     * @param playerName 玩家名称
     * @param date       签到日期
     * @param makeup     是否为补签
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void saveSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(signInUpsertSql())) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setString(3, date == null ? "" : date);
            statement.setInt(4, makeup ? 1 : 0);
            statement.executeUpdate();
        }
    }

    /**
     * 在同一事务内写入签到日期、扣减补签卡和记录待发奖励。
     *
     * @param playerUuid 玩家唯一 id
     * @param playerName 玩家名称
     * @param date       签到日期
     * @param makeup     是否为补签
     * @return 提交结果、成功时的签到日期、数据库中的补签卡余额
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public SignInCommit commitSignInWithRewards(UUID playerUuid, String playerName, String date, boolean makeup,
                                                java.util.function.Function<Set<String>, List<Reward>> plan) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Serialize same-player sign-ins, including different dates, before reading the history.
                ensurePlayerState(connection, playerUuid, playerName);
                try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
                    VALUES (?, ?, ?, ?)
                    """)) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, playerName);
                    statement.setString(3, date);
                    statement.setInt(4, makeup ? 1 : 0);
                    statement.executeUpdate();
                } catch (SQLException duplicate) {
                    if (!duplicateKey(duplicate)) throw duplicate;
                    connection.rollback();
                    return new SignInCommit(SignInStatus.REPEATED, Set.of(), loadMakeupCards(connection, playerUuid));
                }
                if (makeup && !debitMakeupCard(connection, playerUuid)) {
                    int cards = loadMakeupCards(connection, playerUuid);
                    connection.rollback();
                    return new SignInCommit(SignInStatus.NO_CARDS, Set.of(), cards);
                }
                Set<String> dates = loadAllSignInDates(connection, playerUuid);
                insertDeliveries(connection, playerUuid, "signin:" + date, plan.apply(dates));
                saveSignInSummary(connection, playerUuid, dates);
                int cards = loadMakeupCards(connection, playerUuid);
                connection.commit();
                return new SignInCommit(SignInStatus.APPLIED, dates, cards);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private static void saveSignInSummary(Connection connection, UUID playerUuid, Set<String> dates) throws SQLException {
        var summary = xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsSignInHistory.summarize(dates);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE online_rewards_players SET last_sign_in_date = ?, sign_in_streak = ?, sign_in_total = ?
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, summary.latestDate());
            statement.setInt(2, summary.streak());
            statement.setInt(3, dates.size());
            statement.setString(4, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    /**
     * 分页加载排行榜条目。
     *
     * @param scope    排行榜范围
     * @param periodKey 周期键
     * @param offset   偏移量
     * @param limit    条数上限
     * @return 排行榜条目列表
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public List<OnlineRewardsLeaderboardEntry> loadLeaderboard(
        OnlineRewardsLeaderboardScope scope,
        String periodKey,
        int offset,
        int limit
    ) throws SQLException {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        String sql = switch (scope) {
            case DAILY -> """
                SELECT player_uuid, player_name, online_minutes AS minutes
                FROM online_rewards_players
                WHERE reward_date = ? AND online_minutes > 0
                ORDER BY online_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case WEEKLY -> """
                SELECT player_uuid, player_name, week_minutes AS minutes
                FROM online_rewards_players
                WHERE week_key = ? AND week_minutes > 0
                ORDER BY week_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case MONTHLY -> """
                SELECT player_uuid, player_name, month_minutes AS minutes
                FROM online_rewards_players
                WHERE month_key = ? AND month_minutes > 0
                ORDER BY month_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
            case TOTAL -> """
                SELECT player_uuid, player_name, total_minutes AS minutes
                FROM online_rewards_players
                WHERE total_minutes > 0
                ORDER BY total_minutes DESC, player_name ASC, player_uuid ASC
                LIMIT ? OFFSET ?
                """;
        };

        List<OnlineRewardsLeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (scope != OnlineRewardsLeaderboardScope.TOTAL) {
                statement.setString(parameterIndex++, periodKey == null ? "" : periodKey);
            }
            statement.setInt(parameterIndex++, safeLimit);
            statement.setInt(parameterIndex, safeOffset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    String playerName = nullToEmpty(resultSet.getString("player_name"));
                    if (playerName.isBlank()) {
                        playerName = playerUuid.toString();
                    }
                    entries.add(new OnlineRewardsLeaderboardEntry(playerUuid, playerName, resultSet.getInt("minutes")));
                }
            }
        }
        return List.copyOf(entries);
    }

    /**
     * 统计指定日期的签到记录数。
     *
     * @param date 日期字符串（精确匹配）
     * @return 签到记录数
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public int countSignInRecords(String date) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(DISTINCT player_uuid) FROM online_rewards_sign_ins WHERE sign_in_date = ?"
             )) {
            statement.setString(1, date == null ? "" : date);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * 判断指定日期的全服签到目标是否已触发。
     *
     * @param date   日期
     * @param goalId 目标 id
     * @return 是否已触发
     * @throws SQLException 查询失败时抛出
     */
    @Override
    public SignInGoalStatus signInGoalStatus(String date) throws SQLException {
        String sql = """
            SELECT totals.signed_count, goals.goal_id
            FROM (SELECT COUNT(*) AS signed_count FROM online_rewards_sign_ins WHERE sign_in_date = ?) totals
            LEFT JOIN online_rewards_server_goals goals ON goals.goal_date = ? AND goals.triggered = 1
            """;
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date);
            statement.setString(2, date);
            var triggered = new HashSet<String>();
            int signed = 0;
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    signed = rows.getInt(1);
                    String goal = rows.getString(2);
                    if (goal != null) triggered.add(goal);
                }
            }
            return new SignInGoalStatus(signed, triggered);
        }
    }

    @Override
    public boolean isServerGoalTriggered(String date, String goalId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM online_rewards_server_goals WHERE goal_date = ? AND goal_id = ? AND triggered = 1"
             )) {
            statement.setString(1, date == null ? "" : date);
            statement.setString(2, goalId == null ? "" : goalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * 记录指定日期的全服签到目标已触发。
     *
     * @param date   日期
     * @param goalId 目标 id
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public boolean recordServerGoalWithRewards(String date, String goalId, java.util.Map<UUID, List<Reward>> recipients) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!insertGoal(connection, date, goalId)) {
                    connection.rollback();
                    return false;
                }
                for (var recipient : recipients.entrySet()) {
                    insertDeliveries(connection, recipient.getKey(), "goal:" + date + ":" + goalId, recipient.getValue());
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    private boolean insertGoal(Connection connection, String date, String goalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO online_rewards_server_goals (goal_date, goal_id, triggered) VALUES (?, ?, 1)
            """)) {
            statement.setString(1, date);
            statement.setString(2, goalId);
            return statement.executeUpdate() == 1;
        } catch (SQLException duplicate) {
            if (!duplicateKey(duplicate)) throw duplicate;
            // Old databases can contain a pre-created goal row that has not fired.
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE online_rewards_server_goals SET triggered = 1
                WHERE goal_date = ? AND goal_id = ? AND triggered = 0
                """)) {
                statement.setString(1, date);
                statement.setString(2, goalId);
                return statement.executeUpdate() == 1;
            }
        }
    }

    private static void insertDeliveries(Connection connection, UUID player, String batch, List<Reward> rewards) throws SQLException {
        if (rewards.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DELIVERY)) {
            addDeliveries(statement, player, batch, rewards);
            executeCheckedBatch(statement);
        }
    }

    private static void addDeliveries(PreparedStatement statement, UUID player, String batch, List<Reward> rewards)
            throws SQLException {
        for (int step = 0; step < rewards.size(); step++) {
            bindDelivery(statement, 1, player, batch, step, rewards.get(step));
            statement.addBatch();
        }
    }

    private static int bindDelivery(PreparedStatement statement, int parameter, UUID player, String batch, int step,
                                    Reward reward) throws SQLException {
        statement.setString(parameter++, player.toString());
        statement.setString(parameter++, batch);
        statement.setInt(parameter++, step);
        statement.setString(parameter++, reward.kind());
        statement.setString(parameter++, reward.value());
        statement.setString(parameter++, reward.context());
        return parameter;
    }

    @Override
    public boolean stageItemCheckpoint(ItemCheckpoint checkpoint) throws SQLException {
        Delivery delivery = checkpoint.delivery();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!changeDelivery(connection, delivery.playerUuid(), delivery.batchId(), delivery.step(),
                        checkpoint.attempt(), "status = 'EXECUTING' AND kind = 'ITEM'", "ITEM_SAVING", true)) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO online_rewards_item_checkpoints
                            (player_uuid, batch_id, step, attempt_id, node_id, item_data, sink)
                        SELECT player_uuid, batch_id, step, attempt_id, ?, ?, ?
                        FROM online_rewards_deliveries
                        WHERE player_uuid = ? AND batch_id = ? AND step = ? AND attempt_id = ?
                            AND status = 'ITEM_SAVING' AND kind = 'ITEM'
                        """)) {
                    statement.setString(1, checkpoint.node());
                    statement.setString(2, checkpoint.itemData());
                    statement.setString(3, checkpoint.sink().name());
                    statement.setString(4, delivery.playerUuid().toString());
                    statement.setString(5, delivery.batchId());
                    statement.setInt(6, delivery.step());
                    statement.setString(7, checkpoint.attempt().toString());
                    if (statement.executeUpdate() != 1) throw new SQLException("Item delivery disappeared while checkpointing");
                } catch (SQLException collision) {
                    if (!duplicateKey(collision)) throw collision;
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    @Override
    public Optional<ItemCheckpoint> itemCheckpoint(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT d.batch_id, d.step, d.kind, d.payload, d.context, c.attempt_id, c.node_id, c.item_data, c.sink
                 FROM online_rewards_item_checkpoints c
                 JOIN online_rewards_deliveries d ON d.player_uuid = c.player_uuid
                     AND d.batch_id = c.batch_id AND d.step = c.step AND d.attempt_id = c.attempt_id
                 WHERE c.player_uuid = ? AND d.status = 'ITEM_SAVING' AND d.kind = 'ITEM'
                 """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var delivery = new Delivery(playerUuid, rows.getString(1), rows.getInt(2),
                    new Reward(rows.getString(3), rows.getString(4), rows.getString(5)));
                return Optional.of(new ItemCheckpoint(delivery, UUID.fromString(rows.getString(6)),
                    rows.getString(7), rows.getString(8), ItemSink.valueOf(rows.getString(9))));
            }
        }
    }

    @Override
    public boolean completeItemCheckpoint(ItemCheckpoint checkpoint) throws SQLException {
        Delivery delivery = checkpoint.delivery();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!changeDelivery(connection, delivery.playerUuid(), delivery.batchId(), delivery.step(),
                        checkpoint.attempt(), "status = 'ITEM_SAVING' AND kind = 'ITEM'", "DONE", false)) {
                    connection.rollback();
                    return false;
                }
                // Node ownership is case sensitive even when the MySQL schema's default collation is not.
                String owner = isMysql() ? "BINARY node_id = BINARY ? AND BINARY batch_id = BINARY ?"
                    : "node_id = ? AND batch_id = ?";
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM online_rewards_item_checkpoints
                        WHERE player_uuid = ? AND step = ? AND attempt_id = ? AND sink = ? AND
                        """ + owner)) {
                    statement.setString(1, delivery.playerUuid().toString());
                    statement.setInt(2, delivery.step());
                    statement.setString(3, checkpoint.attempt().toString());
                    statement.setString(4, checkpoint.sink().name());
                    statement.setString(5, checkpoint.node());
                    statement.setString(6, delivery.batchId());
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    @Override
    public List<Delivery> readyDeliveries(UUID playerUuid, int limit) throws SQLException {
        return readyDeliveries(Set.of(playerUuid), limit);
    }

    @Override
    public List<Delivery> readyDeliveries(Set<UUID> onlinePlayers, int limit) throws SQLException {
        if (onlinePlayers.isEmpty() || limit <= 0) return List.of();
        String placeholders = "?" + ",?".repeat(onlinePlayers.size() - 1);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT player_uuid, batch_id, step, kind, payload, context FROM online_rewards_deliveries
                 WHERE player_uuid IN (%s) AND status = 'READY'
                 %s ORDER BY player_uuid, batch_id, step LIMIT ?
                 """.formatted(placeholders, NO_ACTIVE_ITEM_CHECKPOINT))) {
            int index = 1;
            for (UUID player : onlinePlayers) statement.setString(index++, player.toString());
            statement.setInt(index, limit);
            List<Delivery> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Delivery(UUID.fromString(rows.getString(1)), rows.getString(2), rows.getInt(3),
                        new Reward(rows.getString(4), rows.getString(5), rows.getString(6))));
                }
            }
            return result;
        }
    }

    @Override
    public boolean beginDelivery(Delivery delivery, UUID attempt) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE online_rewards_deliveries SET status = 'EXECUTING', attempt_id = ?
                 WHERE player_uuid = ? AND batch_id = ? AND step = ? AND status = 'READY'
                 """ + NO_ACTIVE_ITEM_CHECKPOINT)) {
            statement.setString(1, attempt.toString());
            statement.setString(2, delivery.playerUuid().toString());
            statement.setString(3, delivery.batchId());
            statement.setInt(4, delivery.step());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean releaseUnstartedDelivery(Delivery delivery, UUID attempt) throws SQLException {
        return changeDelivery(delivery.playerUuid(), delivery.batchId(), delivery.step(), attempt,
            "status = 'EXECUTING'", "READY", false);
    }

    @Override
    public void finishDelivery(Delivery delivery, UUID attempt, boolean success) throws SQLException {
        changeDelivery(delivery.playerUuid(), delivery.batchId(), delivery.step(), attempt,
            "status = 'EXECUTING'", success ? "DONE" : "REVIEW", !success);
    }

    @Override
    public List<DeliveryReview> deliveriesForReview(UUID playerUuid, int limit) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT batch_id, step, kind, payload, context, attempt_id, status FROM online_rewards_deliveries
                 WHERE player_uuid = ? AND status IN ('EXECUTING', 'REVIEW') ORDER BY batch_id, step LIMIT ?
                 """)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, limit);
            List<DeliveryReview> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Delivery delivery = new Delivery(playerUuid, rows.getString(1), rows.getInt(2),
                        new Reward(rows.getString(3), rows.getString(4), rows.getString(5)));
                    result.add(new DeliveryReview(delivery, UUID.fromString(rows.getString(6)), rows.getString(7)));
                }
            }
            return result;
        }
    }

    @Override
    public boolean resolveDelivery(UUID playerUuid, String batch, int step, UUID attempt, boolean confirmed,
                                   boolean retryPreset) throws SQLException {
        if (confirmed) {
            return changeDelivery(playerUuid, batch, step, attempt, "status IN ('EXECUTING', 'REVIEW')", "DONE", false);
        }
        String eligible = retryPreset ? "(status = 'REVIEW' OR (kind = 'MAIL' AND status = 'EXECUTING'))"
            : "status = 'REVIEW' AND kind <> 'MAIL'";
        return changeDelivery(playerUuid, batch, step, attempt, eligible, "READY", false);
    }

    @Override
    public void savePresence(String nodeId, UUID serviceId, java.util.Map<UUID, String> online, long expiresAt) throws SQLException {
        if (online.isEmpty()) return;
        String insert = """
            INSERT INTO online_rewards_presence (player_uuid, node_id, service_id, player_name, expires_at) VALUES (?, ?, ?, ?, ?)
            """;
        String upsert = isMysql()
            ? "ON DUPLICATE KEY UPDATE service_id=VALUES(service_id), player_name=VALUES(player_name), expires_at=VALUES(expires_at)"
            : "ON CONFLICT(player_uuid, node_id) DO UPDATE SET service_id=excluded.service_id, "
                + "player_name=excluded.player_name, expires_at=excluded.expires_at";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(insert + upsert)) {
            for (var player : online.entrySet()) {
                statement.setString(1, player.getKey().toString());
                statement.setString(2, nodeId);
                statement.setString(3, serviceId.toString());
                statement.setString(4, player.getValue());
                statement.setLong(5, expiresAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public void removePresence(String nodeId, UUID serviceId, UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM online_rewards_presence WHERE node_id = ? AND service_id = ?"
            + (playerUuid == null ? "" : " AND player_uuid = ?");
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nodeId);
            statement.setString(2, serviceId.toString());
            if (playerUuid != null) statement.setString(3, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public java.util.Map<UUID, String> loadPresence(long now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT player_uuid, player_name FROM online_rewards_presence
                 WHERE expires_at > ? ORDER BY expires_at DESC
                 """)) {
            statement.setLong(1, now);
            java.util.Map<UUID, String> players = new java.util.LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) players.putIfAbsent(UUID.fromString(rows.getString(1)), rows.getString(2));
            }
            return players;
        }
    }

    private boolean changeDelivery(UUID playerUuid, String batch, int step, UUID attempt,
                                   String condition, String next, boolean keepAttempt) throws SQLException {
        try (Connection connection = getConnection()) {
            return changeDelivery(connection, playerUuid, batch, step, attempt, condition, next, keepAttempt);
        }
    }

    private static boolean changeDelivery(Connection connection, UUID playerUuid, String batch, int step, UUID attempt,
                                          String condition, String next, boolean keepAttempt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                 "UPDATE online_rewards_deliveries SET status = ?, attempt_id = ? "
                     + "WHERE player_uuid = ? AND batch_id = ? AND step = ? AND attempt_id = ? AND " + condition)) {
            statement.setString(1, next);
            statement.setString(2, keepAttempt ? attempt.toString() : null);
            statement.setString(3, playerUuid.toString());
            statement.setString(4, batch);
            statement.setInt(5, step);
            statement.setString(6, attempt.toString());
            return statement.executeUpdate() == 1;
        }
    }

    private boolean duplicateKey(SQLException exception) {
        return isMysql() ? exception.getErrorCode() == 1062 : exception.getErrorCode() == 19;
    }

    private static void rollback(Connection connection, Exception failure) {
        try { connection.rollback(); }
        catch (SQLException rollback) { failure.addSuppressed(rollback); }
    }

    private Set<String> parseIdSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return Set.copyOf(ids);
    }

    private void createOrMigrateTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_players (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL DEFAULT '',
                        reward_date TEXT NOT NULL DEFAULT '',
                        online_minutes INTEGER NOT NULL DEFAULT 0,
                        reward_stage INTEGER NOT NULL DEFAULT 0,
                        week_key TEXT NOT NULL DEFAULT '',
                        week_minutes INTEGER NOT NULL DEFAULT 0,
                        month_key TEXT NOT NULL DEFAULT '',
                        month_minutes INTEGER NOT NULL DEFAULT 0,
                        total_minutes INTEGER NOT NULL DEFAULT 0,
                        last_sign_in_date TEXT NOT NULL DEFAULT '',
                        sign_in_streak INTEGER NOT NULL DEFAULT 0,
                        sign_in_total INTEGER NOT NULL DEFAULT 0,
                        makeup_cards INTEGER NOT NULL DEFAULT 0,
                        time_bonus_remainder REAL NOT NULL DEFAULT 0,
                        offline_savings_minutes INTEGER NOT NULL DEFAULT 0,
                        offline_savings_day TEXT NOT NULL DEFAULT '',
                        offline_savings_earned_minutes INTEGER NOT NULL DEFAULT 0,
                        offline_savings_credited_minutes INTEGER NOT NULL DEFAULT 0,
                        claimed_weekly_reward_ids TEXT NOT NULL DEFAULT '',
                        claimed_monthly_reward_ids TEXT NOT NULL DEFAULT ''
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_sign_ins (
                        player_uuid TEXT NOT NULL,
                        sign_in_date TEXT NOT NULL,
                        player_name TEXT NOT NULL DEFAULT '',
                        makeup INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, sign_in_date)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_server_goals (
                        goal_date TEXT NOT NULL,
                        goal_id TEXT NOT NULL,
                        triggered INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (goal_date, goal_id)
                    );
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_players (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        reward_date VARCHAR(32) NOT NULL DEFAULT '',
                        online_minutes INT NOT NULL DEFAULT 0,
                        reward_stage INT NOT NULL DEFAULT 0,
                        week_key VARCHAR(32) NOT NULL DEFAULT '',
                        week_minutes INT NOT NULL DEFAULT 0,
                        month_key VARCHAR(32) NOT NULL DEFAULT '',
                        month_minutes INT NOT NULL DEFAULT 0,
                        total_minutes INT NOT NULL DEFAULT 0,
                        last_sign_in_date VARCHAR(32) NOT NULL DEFAULT '',
                        sign_in_streak INT NOT NULL DEFAULT 0,
                        sign_in_total INT NOT NULL DEFAULT 0,
                        makeup_cards INT NOT NULL DEFAULT 0,
                        time_bonus_remainder DOUBLE NOT NULL DEFAULT 0,
                        offline_savings_minutes INT NOT NULL DEFAULT 0,
                        offline_savings_day VARCHAR(32) NOT NULL DEFAULT '',
                        offline_savings_earned_minutes INT NOT NULL DEFAULT 0,
                        offline_savings_credited_minutes INT NOT NULL DEFAULT 0,
                        claimed_weekly_reward_ids TEXT NOT NULL,
                        claimed_monthly_reward_ids TEXT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_sign_ins (
                        player_uuid VARCHAR(36) NOT NULL,
                        sign_in_date VARCHAR(32) NOT NULL,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        makeup TINYINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, sign_in_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_rewards_server_goals (
                        goal_date VARCHAR(32) NOT NULL,
                        goal_id VARCHAR(64) NOT NULL,
                        triggered TINYINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (goal_date, goal_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS online_rewards_attendance (
                    connection_id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    eligible_minutes BIGINT NOT NULL
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS online_rewards_deliveries (
                    player_uuid VARCHAR(36) NOT NULL,
                    batch_id VARCHAR(128) NOT NULL,
                    step INTEGER NOT NULL,
                    kind VARCHAR(24) NOT NULL,
                    payload TEXT NOT NULL,
                    context TEXT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    attempt_id VARCHAR(36) NULL,
                    PRIMARY KEY (player_uuid, batch_id, step)
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS online_rewards_time_batches (
                    player_uuid VARCHAR(36) NOT NULL,
                    batch_id VARCHAR(128) NOT NULL,
                    PRIMARY KEY (player_uuid, batch_id)
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS online_rewards_item_checkpoints (
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    batch_id VARCHAR(128) NOT NULL,
                    step INTEGER NOT NULL,
                    attempt_id VARCHAR(36) NOT NULL,
                    node_id VARCHAR(128) NOT NULL,
                    item_data %s NOT NULL,
                    sink VARCHAR(16) NOT NULL
                )
                """.formatted(isMysql() ? "MEDIUMTEXT" : "TEXT") + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS online_rewards_presence (
                    player_uuid VARCHAR(36) NOT NULL,
                    node_id VARCHAR(128) NOT NULL,
                    service_id VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    expires_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, node_id)
                )
                """ + (isMysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ""));
        }
        ensureColumns(connection);
        // 仅在未完成迁移时执行，避免每次启动全表扫描
        if (!isMigrationCompleted(connection, "migrate_last_sign_in_records")) {
            migrateLastSignInRecords(connection);
            markMigrationCompleted(connection, "migrate_last_sign_in_records");
        }
    }

    private static final String MIGRATION_TABLE = "online_rewards_schema_meta";

    private boolean isMigrationCompleted(Connection connection, String migrationId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        migration_id TEXT PRIMARY KEY,
                        completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(MIGRATION_TABLE));
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        migration_id VARCHAR(64) PRIMARY KEY,
                        completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(MIGRATION_TABLE));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM %s WHERE migration_id = ?".formatted(MIGRATION_TABLE)
        )) {
            statement.setString(1, migrationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void markMigrationCompleted(Connection connection, String migrationId) throws SQLException {
        String sql = !isMysql()
            ? "INSERT OR IGNORE INTO %s (migration_id) VALUES (?)".formatted(MIGRATION_TABLE)
            : "INSERT IGNORE INTO %s (migration_id) VALUES (?)".formatted(MIGRATION_TABLE);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, migrationId);
            statement.executeUpdate();
        }
    }

    private void ensureColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Set<String> columns = loadColumns(connection);
            ensureColumn(statement, columns, "player_name", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(64) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "reward_date", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "online_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "reward_stage", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "week_key", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "week_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "month_key", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "month_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "total_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "last_sign_in_date", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "sign_in_streak", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "sign_in_total", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "makeup_cards", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "time_bonus_remainder", sqliteType("REAL NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "offline_savings_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "offline_savings_day", sqliteType("TEXT NOT NULL DEFAULT ''", "VARCHAR(32) NOT NULL DEFAULT ''"));
            ensureColumn(statement, columns, "offline_savings_earned_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "offline_savings_credited_minutes", sqliteType("INTEGER NOT NULL DEFAULT 0", "INT NOT NULL DEFAULT 0"));
            ensureColumn(statement, columns, "claimed_weekly_reward_ids", sqliteType("TEXT NOT NULL DEFAULT ''", "TEXT NOT NULL"));
            ensureColumn(statement, columns, "claimed_monthly_reward_ids", sqliteType("TEXT NOT NULL DEFAULT ''", "TEXT NOT NULL"));
        }
    }

    private void ensureIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Set<String> indexes = loadIndexes(connection);
            ensureIndex(statement, indexes, "idx_online_rewards_daily_rank", "online_rewards_players", "reward_date, online_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_weekly_rank", "online_rewards_players", "week_key, week_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_monthly_rank", "online_rewards_players", "month_key, month_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_total_rank", "online_rewards_players", "total_minutes");
            ensureIndex(statement, indexes, "idx_online_rewards_sign_ins_date", "online_rewards_sign_ins", "sign_in_date");
            ensureIndex(statement, indexes, "idx_online_rewards_ready", "online_rewards_deliveries",
                "player_uuid, status, batch_id, step");
            ensureIndex(statement, indexes, "idx_online_rewards_attendance_player", "online_rewards_attendance", "player_uuid");
        }
    }

    private void migrateLastSignInRecords(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    INSERT OR IGNORE INTO online_rewards_sign_ins (player_uuid, sign_in_date, player_name, makeup)
                    SELECT player_uuid, last_sign_in_date, player_name, 0
                    FROM online_rewards_players
                    WHERE last_sign_in_date IS NOT NULL AND last_sign_in_date <> ''
                    """);
            } else {
                statement.execute("""
                    INSERT IGNORE INTO online_rewards_sign_ins (player_uuid, sign_in_date, player_name, makeup)
                    SELECT player_uuid, last_sign_in_date, player_name, 0
                    FROM online_rewards_players
                    WHERE last_sign_in_date IS NOT NULL AND last_sign_in_date <> ''
                    """);
            }
        }
    }

    private String upsertSql(boolean writeCards) {
        return upsertSql(writeCards, 1);
    }

    private String upsertSql(boolean writeCards, int count) {
        String insert = PLAYER_STATE_INSERT + rows(PLAYER_STATE_ROW, count) + "\n";
        if (!isMysql()) {
            return insert + """
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    reward_date = excluded.reward_date,
                    online_minutes = excluded.online_minutes,
                    reward_stage = excluded.reward_stage,
                    week_key = excluded.week_key,
                    week_minutes = excluded.week_minutes,
                    month_key = excluded.month_key,
                    month_minutes = excluded.month_minutes,
                    total_minutes = excluded.total_minutes,
                    %s
                    time_bonus_remainder = excluded.time_bonus_remainder,
                    offline_savings_minutes = excluded.offline_savings_minutes,
                    offline_savings_day = excluded.offline_savings_day,
                    offline_savings_earned_minutes = excluded.offline_savings_earned_minutes,
                    offline_savings_credited_minutes = excluded.offline_savings_credited_minutes,
                    claimed_weekly_reward_ids = excluded.claimed_weekly_reward_ids,
                    claimed_monthly_reward_ids = excluded.claimed_monthly_reward_ids
                """.formatted(writeCards ? "last_sign_in_date = excluded.last_sign_in_date, sign_in_streak = excluded.sign_in_streak,"
                    + " sign_in_total = excluded.sign_in_total, makeup_cards = excluded.makeup_cards," : "");
        }
        return insert + """
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                reward_date = VALUES(reward_date),
                online_minutes = VALUES(online_minutes),
                reward_stage = VALUES(reward_stage),
                week_key = VALUES(week_key),
                week_minutes = VALUES(week_minutes),
                month_key = VALUES(month_key),
                month_minutes = VALUES(month_minutes),
                total_minutes = VALUES(total_minutes),
                %s
                time_bonus_remainder = VALUES(time_bonus_remainder),
                offline_savings_minutes = VALUES(offline_savings_minutes),
                offline_savings_day = VALUES(offline_savings_day),
                offline_savings_earned_minutes = VALUES(offline_savings_earned_minutes),
                offline_savings_credited_minutes = VALUES(offline_savings_credited_minutes),
                claimed_weekly_reward_ids = VALUES(claimed_weekly_reward_ids),
                claimed_monthly_reward_ids = VALUES(claimed_monthly_reward_ids)
            """.formatted(writeCards ? "last_sign_in_date = VALUES(last_sign_in_date), sign_in_streak = VALUES(sign_in_streak),"
                + " sign_in_total = VALUES(sign_in_total), makeup_cards = VALUES(makeup_cards)," : "");
    }

    private String signInUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, sign_in_date) DO UPDATE SET
                    player_name = excluded.player_name,
                    makeup = excluded.makeup
                """;
        }
        return """
            INSERT INTO online_rewards_sign_ins (player_uuid, player_name, sign_in_date, makeup)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                makeup = VALUES(makeup)
            """;
    }

    private void ensureColumn(Statement statement, Set<String> columns, String columnName, String definition) throws SQLException {
        if (columns.contains(columnName)) {
            return;
        }
        try {
            statement.execute("ALTER TABLE online_rewards_players ADD COLUMN " + columnName + " " + definition);
        } catch (SQLException failure) {
            // A second backend may have read the old schema before the first ALTER committed.
            String message = failure.getMessage();
            boolean duplicate = isMysql() ? failure.getErrorCode() == 1060
                : failure.getErrorCode() == 1 && message != null && message.contains("duplicate column name: " + columnName);
            if (!duplicate) throw failure;
        }
        columns.add(columnName);
    }

    private void ensureIndex(Statement statement, Set<String> indexes, String indexName, String tableName, String columns) throws SQLException {
        if (indexes.contains(indexName.toLowerCase())) {
            return;
        }
        String prefix = isMysql() ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
        try {
            statement.execute(prefix + indexName + " ON " + tableName + " (" + columns + ")");
        } catch (SQLException failure) {
            // Another backend can create the index after this node's metadata read.
            if (!isMysql() || failure.getErrorCode() != 1061) throw failure;
        }
        indexes.add(indexName.toLowerCase());
    }

    private String sqliteType(String sqliteDefinition, String mysqlDefinition) {
        return !isMysql() ? sqliteDefinition : mysqlDefinition;
    }

    private Set<String> loadColumns(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = metaData.getColumns(null, null, "online_rewards_players", null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> loadIndexes(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> indexes = new HashSet<>();
        loadIndexes(metaData, indexes, "online_rewards_players");
        loadIndexes(metaData, indexes, "online_rewards_sign_ins");
        loadIndexes(metaData, indexes, "online_rewards_deliveries");
        loadIndexes(metaData, indexes, "online_rewards_attendance");
        return indexes;
    }

    private void loadIndexes(DatabaseMetaData metaData, Set<String> indexes, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null && !indexName.isBlank()) {
                    indexes.add(indexName.toLowerCase());
                }
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
