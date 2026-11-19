package xuanmo.arcartxsuite.qqbot.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.*;

/** Transaction boundary for points and externally delivered rewards. Never owns the data source. */
final class QQBotEconomyStore {
    private final DataSource source;
    private final boolean mysql;
    private final String points;
    private final String signin;
    private final String legacyRedeems;
    private final String redemptions;
    private final String items;
    private final String ledger;
    private final String redPackets;
    private final String claims;
    private final String workers;
    private final String owner = UUID.randomUUID().toString();
    private static final long OWNER_LEASE_MILLIS = 60_000;
    private volatile boolean closed;
    private xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor heartbeat;
    private java.util.concurrent.CompletableFuture<Void> retirement;

    QQBotEconomyStore(DataSource source, boolean mysql, String prefix) {
        this.source = source;
        this.mysql = mysql;
        points = prefix + "points";
        signin = prefix + "signin";
        legacyRedeems = prefix + "redeem_log";
        redemptions = prefix + "redemptions";
        items = prefix + "redemption_items";
        ledger = prefix + "point_log";
        redPackets = prefix + "red_packets";
        claims = prefix + "red_packet_claims";
        workers = prefix + "economy_workers";
    }

    List<String> tables() { return List.of(redemptions, items, ledger, workers); }

    void initialize(Connection conn) throws SQLException {
        execute(conn, "CREATE TABLE IF NOT EXISTS " + redemptions + " ("
            + "id VARCHAR(36) PRIMARY KEY, qq_id BIGINT NOT NULL, prize_id VARCHAR(64) NOT NULL,"
            + "player_uuid VARCHAR(36) NOT NULL, player_name VARCHAR(64) NOT NULL, cost INT NOT NULL,"
            + "redeem_date VARCHAR(10) NOT NULL, state VARCHAR(16) NOT NULL,"
            + "created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, note VARCHAR(1000) NOT NULL,"
            + "owner_id VARCHAR(36) NOT NULL, dispatch_active INT NOT NULL DEFAULT 1)");
        execute(conn, "CREATE TABLE IF NOT EXISTS " + items + " ("
            + "redemption_id VARCHAR(36) NOT NULL, ordinal INT NOT NULL, preset_id VARCHAR(128) NOT NULL,"
            + "state VARCHAR(16) NOT NULL, detail VARCHAR(1000) NOT NULL,"
            + "PRIMARY KEY (redemption_id, ordinal))");
        execute(conn, "CREATE TABLE IF NOT EXISTS " + ledger + " ("
            + "id VARCHAR(36) PRIMARY KEY, operation_id VARCHAR(36) NOT NULL, qq_id BIGINT NOT NULL,"
            + "delta INT NOT NULL, action VARCHAR(64) NOT NULL, detail VARCHAR(1000) NOT NULL,"
            + "created_at BIGINT NOT NULL)");
        execute(conn, "CREATE TABLE IF NOT EXISTS " + workers
            + " (owner_id VARCHAR(36) PRIMARY KEY, heartbeat_at BIGINT NOT NULL, active INT NOT NULL)");
        index(conn, "idx_qqbot_redemption_lookup", redemptions, "qq_id, prize_id, redeem_date, state");
        index(conn, "idx_qqbot_redemption_owner", redemptions, "dispatch_active, owner_id, state");
        index(conn, "idx_qqbot_pointlog_qq", ledger, "qq_id, created_at");
        startOwner(conn);
    }

    private synchronized void startOwner(Connection conn) throws SQLException {
        if (heartbeat != null) return;
        closed = false;
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            if (!mysql) execute(conn, "UPDATE " + points + " SET updated_at=updated_at WHERE qq_id=-1");
            long now = databaseTime(conn);
            execute(conn, "INSERT INTO " + workers + " (owner_id,heartbeat_at,active) VALUES (?,?,1)", owner, now);
            recoverAbandoned(conn, now);
            conn.commit();
        } catch (SQLException e) { conn.rollback(); throw e; }
        finally { conn.setAutoCommit(auto); }
        heartbeat = new xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor(1, task -> {
            Thread thread = new Thread(task, "QQBot-Economy-Heartbeat"); thread.setDaemon(true); return thread;
        });
        heartbeat.scheduleWithFixedDelay(() -> {
            if (closed) return;
            try {
                transaction(connection -> {
                    long time = databaseTime(connection);
                    execute(connection, "UPDATE " + workers + " SET heartbeat_at=? WHERE owner_id=? AND active=1", time, owner);
                    recoverAbandoned(connection, time);
                    return null;
                });
            } catch (StorageFailure ignored) { /* Expired ownership freezes sending until the database recovers. */ }
        }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    void close() {
        try { retire().toCompletableFuture().join(); }
        catch (java.util.concurrent.CompletionException failure) {
            if (failure.getCause() instanceof StorageFailure storageFailure) throw storageFailure;
            throw failure;
        }
    }

    synchronized java.util.concurrent.CompletionStage<Void> retire() {
        if (retirement != null) return retirement.copy();
        closed = true;
        if (heartbeat == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        heartbeat.shutdown();
        retirement = heartbeat.stopped().thenRunAsync(this::retireOwner).toCompletableFuture();
        return retirement.copy();
    }

    private void retireOwner() {
        transaction(conn -> {
            execute(conn, "UPDATE " + workers + " SET active=0 WHERE owner_id=?", owner);
            recoverAbandoned(conn, databaseTime(conn));
            return null;
        });
    }

    boolean canMigrate() {
        if (!closed) return false;
        try (Connection conn = source.getConnection()) {
            long now = databaseTime(conn);
            return integer(conn, "SELECT COUNT(*) FROM " + workers + " WHERE active=1 AND heartbeat_at>?", 0, now - OWNER_LEASE_MILLIS) == 0
                && integer(conn, "SELECT COUNT(*) FROM " + redemptions + " WHERE state IN ('RESERVED','REVIEW')", 0) == 0;
        } catch (SQLException e) { throw new StorageFailure("核对迁移状态失败", e); }
    }

    private void recoverAbandoned(Connection conn, long now) throws SQLException {
        // Other healthy nodes keep their orders. Only expired/closed owners lose dispatch rights.
        execute(conn, "UPDATE " + redemptions + " SET state='REVIEW', dispatch_active=0, note=?, updated_at=?"
            + " WHERE state IN ('RESERVED','REVIEW') AND dispatch_active=1 AND NOT EXISTS (SELECT 1 FROM " + workers
            + " WHERE " + workers + ".owner_id=" + redemptions + ".owner_id AND active=1 AND heartbeat_at>?)",
            "发放节点已停止或租约过期，请独立核对邮件，禁止自动重发", now, now - OWNER_LEASE_MILLIS);
    }

    private void index(Connection conn, String name, String table, String columns) throws SQLException {
        try {
            execute(conn, "CREATE INDEX " + (mysql ? "" : "IF NOT EXISTS ") + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException e) {
            // MySQL has no portable CREATE INDEX IF NOT EXISTS. Ignore only duplicate index.
            if (!mysql || e.getErrorCode() != 1061) throw e;
        }
    }

    SignInOutcome signIn(long qq, String date, int base, int streakBonus, int maxBonus) {
        if (qq <= 0 || base < 0 || streakBonus < 0 || maxBonus < 0) {
            return new SignInOutcome(MutationStatus.INVALID, 0, 0, 0);
        }
        LocalDate today;
        try { today = LocalDate.parse(date); }
        catch (RuntimeException e) { return new SignInOutcome(MutationStatus.INVALID, 0, 0, 0); }
        try {
            return transaction(conn -> {
                if (closed) return new SignInOutcome(MutationStatus.FAILED, 0, 0, 0);
                requireLiveOwner(conn);
                lockAccounts(conn, qq);
                int existing = integer(conn, "SELECT streak FROM " + signin + " WHERE qq_id=? AND sign_date=?", 0, qq, date);
                if (existing > 0) return new SignInOutcome(MutationStatus.ALREADY, 0, existing, balance(conn, qq));
                int streak = 1;
                try (PreparedStatement ps = prepare(conn, "SELECT sign_date, streak FROM " + signin
                    + " WHERE qq_id=? ORDER BY sign_date DESC LIMIT 1", qq);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && today.minusDays(1).toString().equals(rs.getString(1))) {
                        streak = Math.addExact(rs.getInt(2), 1);
                    }
                }
                int reward = Math.toIntExact((long) base + Math.min((long) (streak - 1) * streakBonus, maxBonus));
                execute(conn, "INSERT INTO " + signin + " (qq_id, sign_date, streak, signed_at) VALUES (?,?,?,?)",
                    qq, date, streak, System.currentTimeMillis());
                credit(conn, qq, reward, true);
                log(conn, UUID.randomUUID().toString(), qq, reward, "signin", "streak=" + streak);
                return new SignInOutcome(MutationStatus.SUCCESS, reward, streak, balance(conn, qq));
            });
        } catch (StorageFailure e) {
            return new SignInOutcome(MutationStatus.FAILED, 0, 0, 0);
        }
    }

    TransferOutcome transfer(long from, long to, int amount) {
        if (from <= 0 || to <= 0 || from == to || amount <= 0) {
            return new TransferOutcome(MutationStatus.INVALID, 0, 0);
        }
        try {
            return transaction(conn -> {
                lockAccounts(conn, Math.min(from, to), Math.max(from, to));
                if (!debit(conn, from, amount)) return new TransferOutcome(MutationStatus.INSUFFICIENT, balance(conn, from), balance(conn, to));
                credit(conn, to, amount, true);
                String operation = UUID.randomUUID().toString();
                log(conn, operation, from, -amount, "transfer", "to=" + to);
                log(conn, operation, to, amount, "transfer", "from=" + from);
                return new TransferOutcome(MutationStatus.SUCCESS, balance(conn, from), balance(conn, to));
            });
        } catch (StorageFailure e) {
            return new TransferOutcome(MutationStatus.FAILED, 0, 0);
        }
    }

    int adjust(long qq, int delta, String action, String detail) {
        if (qq <= 0 || delta == Integer.MIN_VALUE) throw new IllegalArgumentException("非法积分变动");
        return transaction(conn -> {
            lockAccounts(conn, qq);
            if (delta >= 0) credit(conn, qq, delta, true);
            else if (!debit(conn, qq, -delta)) throw new SQLException("积分余额不足");
            log(conn, UUID.randomUUID().toString(), qq, delta, action, detail);
            return balance(conn, qq);
        });
    }

    boolean deduct(long qq, int amount, String action, String detail) {
        if (qq <= 0 || amount < 0) return false;
        return transaction(conn -> {
            lockAccounts(conn, qq);
            if (!debit(conn, qq, amount)) return false;
            log(conn, UUID.randomUUID().toString(), qq, -amount, action, detail);
            return true;
        });
    }

    RedPacketSendOutcome sendRedPacket(long sender, long group, int amount, int count, long expires) {
        if (sender <= 0 || group <= 0 || amount <= 0 || count <= 0 || count > amount || expires <= System.currentTimeMillis()) {
            return new RedPacketSendOutcome(MutationStatus.INVALID, -1, 0);
        }
        try {
            return transaction(conn -> {
                lockAccounts(conn, sender);
                if (!debit(conn, sender, amount)) return new RedPacketSendOutcome(MutationStatus.INSUFFICIENT, -1, balance(conn, sender));
                long id;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + redPackets
                    + " (sender_qq,group_id,total_amount,remaining_amount,count,claimed_count,expire_at,created_at) VALUES (?,?,?,?,?,0,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, sender); ps.setLong(2, group); ps.setInt(3, amount); ps.setInt(4, amount);
                    ps.setInt(5, count); ps.setLong(6, expires); ps.setLong(7, System.currentTimeMillis());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("红包编号未返回");
                        id = rs.getLong(1);
                    }
                }
                log(conn, UUID.randomUUID().toString(), sender, -amount, "redpacket_send", "packet=" + id);
                return new RedPacketSendOutcome(MutationStatus.SUCCESS, id, balance(conn, sender));
            });
        } catch (StorageFailure e) { return new RedPacketSendOutcome(MutationStatus.FAILED, -1, 0); }
    }

    int claimRedPacket(long packet, long claimer) {
        if (packet <= 0 || claimer <= 0) return -3;
        try {
            return transaction(conn -> {
                RedPacket current = lockedPacket(conn, packet);
                if (current == null || current.expireAt() <= System.currentTimeMillis()
                    || current.remainingAmount() <= 0 || current.claimedCount() >= current.count()) return -3;
                if (integer(conn, "SELECT COUNT(*) FROM " + claims + " WHERE red_packet_id=? AND claimer_qq=?", 0, packet, claimer) > 0) return -2;
                int left = current.count() - current.claimedCount();
                int remaining = current.remainingAmount();
                if (remaining < left) throw new SQLException("红包余额与份数不一致，停止发放");
                int amount = left == 1 ? remaining : java.util.concurrent.ThreadLocalRandom.current().nextInt(1, remaining - left + 2);
                lockAccounts(conn, claimer);
                credit(conn, claimer, amount, true);
                execute(conn, "UPDATE " + redPackets + " SET remaining_amount=remaining_amount-?,claimed_count=claimed_count+1 WHERE id=?", amount, packet);
                execute(conn, "INSERT INTO " + claims + " (red_packet_id,claimer_qq,amount,claimed_at) VALUES (?,?,?,?)",
                    packet, claimer, amount, System.currentTimeMillis());
                log(conn, UUID.randomUUID().toString(), claimer, amount, "redpacket_claim", "packet=" + packet);
                return amount;
            });
        } catch (StorageFailure e) { return -1; }
    }

    void refundRedPackets() {
        List<Long> expired = new ArrayList<>();
        try (Connection conn = source.getConnection(); PreparedStatement ps = prepare(conn, "SELECT id FROM " + redPackets
            + " WHERE expire_at<=? AND remaining_amount>0 ORDER BY id LIMIT 500", System.currentTimeMillis()); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) expired.add(rs.getLong(1));
        } catch (SQLException e) { throw new StorageFailure("查询过期红包失败", e); }
        for (long id : expired) {
            transaction(conn -> {
                RedPacket current = lockedPacket(conn, id);
                if (current == null || current.expireAt() > System.currentTimeMillis() || current.remainingAmount() <= 0) return null;
                lockAccounts(conn, current.senderQq());
                int amount = current.remainingAmount();
                if (execute(conn, "UPDATE " + points + " SET balance=balance+?,total_spent=CASE WHEN total_spent>=? THEN total_spent-? ELSE 0 END,updated_at=?"
                    + " WHERE qq_id=? AND balance<=?", amount, amount, amount, System.currentTimeMillis(), current.senderQq(), Integer.MAX_VALUE - amount) != 1) {
                    throw new SQLException("红包退款积分溢出");
                }
                execute(conn, "UPDATE " + redPackets + " SET remaining_amount=0 WHERE id=?", id);
                log(conn, UUID.randomUUID().toString(), current.senderQq(), amount, "redpacket_refund", "packet=" + id);
                return null;
            });
        }
    }

    private RedPacket lockedPacket(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = prepare(conn, "SELECT * FROM " + redPackets + " WHERE id=?" + (mysql ? " FOR UPDATE" : ""), id);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            return new RedPacket(id, rs.getLong("sender_qq"), rs.getLong("group_id"), rs.getInt("total_amount"),
                rs.getInt("remaining_amount"), rs.getInt("count"), rs.getInt("claimed_count"), rs.getLong("expire_at"), rs.getLong("created_at"));
        }
    }

    RedemptionReservation reserve(long qq, String prizeId, UUID playerUuid, String playerName,
        int cost, String date, int limit, List<String> presets) {
        if (qq <= 0 || prizeId == null || prizeId.isBlank() || prizeId.length() > 64
            || playerUuid == null || playerName == null || playerName.isBlank() || playerName.length() > 64
            || cost < 0 || limit < 0 || presets == null || presets.isEmpty() || presets.size() > 100
            || presets.stream().anyMatch(p -> p == null || p.isBlank() || p.length() > 128)) {
            return new RedemptionReservation(MutationStatus.INVALID, null);
        }
        try { LocalDate.parse(date); }
        catch (RuntimeException e) { return new RedemptionReservation(MutationStatus.INVALID, null); }
        String key = prizeId.toLowerCase(Locale.ROOT);
        try {
            return transaction(conn -> {
                if (closed) return new RedemptionReservation(MutationStatus.FAILED, null);
                requireLiveOwner(conn);
                lockAccounts(conn, qq);
                String pending = null;
                try (PreparedStatement ps = prepare(conn, "SELECT id FROM " + redemptions
                    + " WHERE qq_id=? AND prize_id=? AND state IN ('RESERVED','REVIEW') ORDER BY created_at LIMIT 1", qq, key);
                     ResultSet rs = ps.executeQuery()) { if (rs.next()) pending = rs.getString(1); }
                if (pending != null) return new RedemptionReservation(MutationStatus.REVIEW, read(conn, pending));
                if (limit > 0) {
                    int oldCount = integer(conn, "SELECT COUNT(*) FROM " + legacyRedeems
                        + " WHERE qq_id=? AND LOWER(prize_id)=? AND redeem_date=?", 0, qq, key, date);
                    int newCount = integer(conn, "SELECT COUNT(*) FROM " + redemptions
                        + " WHERE qq_id=? AND prize_id=? AND redeem_date=? AND state<>'REFUNDED'", 0, qq, key, date);
                    if ((long) oldCount + newCount >= limit) return new RedemptionReservation(MutationStatus.LIMIT, null);
                }
                if (!debit(conn, qq, cost)) return new RedemptionReservation(MutationStatus.INSUFFICIENT, null);
                String id = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                execute(conn, "INSERT INTO " + redemptions
                    + " (id,qq_id,prize_id,player_uuid,player_name,cost,redeem_date,state,created_at,updated_at,note,owner_id,dispatch_active)"
                    + " VALUES (?,?,?,?,?,?,?,'RESERVED',?,?,'',?,1)", id, qq, key, playerUuid.toString(), playerName, cost, date, now, now, owner);
                for (int ordinal = 0; ordinal < presets.size(); ordinal++) {
                    execute(conn, "INSERT INTO " + items + " (redemption_id,ordinal,preset_id,state,detail) VALUES (?,?,?,'PENDING','')",
                        id, ordinal, presets.get(ordinal));
                }
                log(conn, id, qq, -cost, "redeem_reserve", key);
                return new RedemptionReservation(MutationStatus.SUCCESS, read(conn, id));
            });
        } catch (StorageFailure e) {
            return new RedemptionReservation(MutationStatus.FAILED, null);
        }
    }

    boolean beginItem(String id, int ordinal) {
        return transaction(conn -> {
            Redemption redemption = locked(conn, id);
            if (closed || redemption == null || redemption.state() != RedemptionState.RESERVED) return false;
            requireLiveOwner(conn);
            if (!ownedAndActive(conn, id)) return false;
            return execute(conn, "UPDATE " + items + " SET state='SENDING' WHERE redemption_id=? AND ordinal=? AND state='PENDING'",
                id, ordinal) == 1;
        });
    }

    void recordItem(String id, int ordinal, RedemptionItemState state, String detail) {
        if (state != RedemptionItemState.SENT && state != RedemptionItemState.FAILED && state != RedemptionItemState.UNKNOWN) {
            throw new IllegalArgumentException("非法发放结果");
        }
        transaction(conn -> {
            Redemption redemption = locked(conn, id);
            if (redemption == null || redemption.state() == RedemptionState.COMPLETE || redemption.state() == RedemptionState.REFUNDED) {
                throw new SQLException("兑换单不可更新");
            }
            if (!owned(conn, id)) throw new SQLException("兑换单不属于当前节点");
            if (execute(conn, "UPDATE " + items + " SET state=?, detail=? WHERE redemption_id=? AND ordinal=? AND state='SENDING'",
                state.name(), truncate(detail, 1000), id, ordinal) != 1) throw new SQLException("发放结果状态冲突");
            if (state == RedemptionItemState.UNKNOWN) {
                execute(conn, "UPDATE " + redemptions + " SET state='REVIEW',note=?,updated_at=? WHERE id=?",
                    "邮件结果未确认，禁止自动补发或退款", System.currentTimeMillis(), id);
            }
            return null;
        });
    }

    Redemption finish(String id) {
        return transaction(conn -> {
            Redemption current = locked(conn, id);
            if (current == null) throw new SQLException("兑换单不存在");
            if (!owned(conn, id)) throw new SQLException("兑换单不属于当前节点");
            if (current.state() == RedemptionState.COMPLETE || current.state() == RedemptionState.REFUNDED) return current;
            boolean allSent = current.items().stream().allMatch(i -> i.state() == RedemptionItemState.SENT);
            boolean allFailed = current.items().stream().allMatch(i -> i.state() == RedemptionItemState.FAILED);
            if (allSent) {
                complete(conn, id, "全部邮件已确认发放");
            } else if (allFailed) {
                refund(conn, current, "全部邮件明确失败，自动退款");
            } else {
                execute(conn, "UPDATE " + redemptions + " SET state='REVIEW',dispatch_active=0,note=?,updated_at=? WHERE id=?",
                    "部分邮件已发放或结果未确认，请人工核对", System.currentTimeMillis(), id);
            }
            return read(conn, id);
        });
    }

    void freeze(String id, String note) {
        transaction(conn -> {
            Redemption current = locked(conn, id);
            if (current != null && owned(conn, id) && (current.state() == RedemptionState.RESERVED || current.state() == RedemptionState.REVIEW)) {
                execute(conn, "UPDATE " + redemptions + " SET state='REVIEW',dispatch_active=0,note=?,updated_at=? WHERE id=?",
                    truncate(note, 1000), databaseTime(conn), id);
            }
            return null;
        });
    }

    Redemption get(String id) {
        if (id == null || id.length() > 36) return null;
        try (Connection conn = source.getConnection()) { return read(conn, id); }
        catch (SQLException e) { throw new StorageFailure("读取兑换单失败", e); }
    }

    List<Redemption> pending(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        try (Connection conn = source.getConnection(); PreparedStatement ps = prepare(conn,
            "SELECT id FROM " + redemptions + " WHERE state IN ('RESERVED','REVIEW') ORDER BY created_at LIMIT ?", size)) {
            List<String> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
            List<Redemption> result = new ArrayList<>();
            for (String id : ids) result.add(read(conn, id));
            return List.copyOf(result);
        } catch (SQLException e) { throw new StorageFailure("查询待核对兑换失败", e); }
    }

    MutationStatus resolve(String id, RedemptionResolution resolution, String note) {
        if (id == null || resolution == null || note == null || note.trim().length() < 4 || note.length() > 1000) {
            return MutationStatus.INVALID;
        }
        try {
            return transaction(conn -> {
                recoverAbandoned(conn, databaseTime(conn));
                Redemption current = locked(conn, id);
                if (current == null) return MutationStatus.NOT_FOUND;
                if (current.state() == RedemptionState.COMPLETE || current.state() == RedemptionState.REFUNDED) return MutationStatus.ALREADY;
                // Do not race a live dispatcher. Only REVIEW can be adjudicated by an administrator.
                if (current.state() != RedemptionState.REVIEW) return MutationStatus.REVIEW;
                if (integer(conn, "SELECT dispatch_active FROM " + redemptions + " WHERE id=?", 0, id) != 0) return MutationStatus.REVIEW;
                if (resolution == RedemptionResolution.CONFIRM_DELIVERED) {
                    complete(conn, id, "人工确认全部发放：" + note);
                } else {
                    if (current.items().stream().anyMatch(i -> i.state() == RedemptionItemState.SENT)) return MutationStatus.REVIEW;
                    refund(conn, current, "人工核实全部未发：" + note);
                }
                return MutationStatus.SUCCESS;
            });
        } catch (StorageFailure e) { return MutationStatus.FAILED; }
    }

    private void complete(Connection conn, String id, String note) throws SQLException {
        execute(conn, "UPDATE " + redemptions + " SET state='COMPLETE',dispatch_active=0,note=?,updated_at=? WHERE id=?",
            truncate(note, 1000), System.currentTimeMillis(), id);
    }

    private void refund(Connection conn, Redemption current, String note) throws SQLException {
        lockAccounts(conn, current.qqId());
        if (execute(conn, "UPDATE " + points + " SET balance=balance+?,total_spent=CASE WHEN total_spent>=? THEN total_spent-? ELSE 0 END,updated_at=?"
            + " WHERE qq_id=? AND balance<=?", current.cost(), current.cost(), current.cost(), System.currentTimeMillis(),
            current.qqId(), Integer.MAX_VALUE - current.cost()) != 1) throw new SQLException("退款积分溢出");
        execute(conn, "UPDATE " + redemptions + " SET state='REFUNDED',dispatch_active=0,note=?,updated_at=? WHERE id=?",
            truncate(note, 1000), System.currentTimeMillis(), current.id());
        log(conn, current.id(), current.qqId(), current.cost(), "redeem_refund", note);
    }

    private Redemption locked(Connection conn, String id) throws SQLException {
        // Parent row serializes dispatch, finalization and administrative adjudication.
        try (PreparedStatement ps = prepare(conn, "SELECT id FROM " + redemptions + " WHERE id=?" + (mysql ? " FOR UPDATE" : ""), id);
             ResultSet rs = ps.executeQuery()) { if (!rs.next()) return null; }
        return read(conn, id);
    }

    private void requireLiveOwner(Connection conn) throws SQLException {
        if (closed || integer(conn, "SELECT COUNT(*) FROM " + workers + " WHERE owner_id=? AND active=1 AND heartbeat_at>?",
            0, owner, databaseTime(conn) - OWNER_LEASE_MILLIS) != 1) throw new SQLException("发放节点租约已失效");
    }

    private boolean owned(Connection conn, String id) throws SQLException {
        return integer(conn, "SELECT COUNT(*) FROM " + redemptions + " WHERE id=? AND owner_id=?", 0, id, owner) == 1;
    }

    private boolean ownedAndActive(Connection conn, String id) throws SQLException {
        return integer(conn, "SELECT COUNT(*) FROM " + redemptions + " WHERE id=? AND owner_id=? AND dispatch_active=1", 0, id, owner) == 1;
    }

    private long databaseTime(Connection conn) throws SQLException {
        String sql = mysql ? "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)"
            : "SELECT CAST(strftime('%s','now') AS INTEGER)*1000";
        try (PreparedStatement ps = prepare(conn, sql); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("数据库时间不可用");
            return rs.getLong(1);
        }
    }

    private Redemption read(Connection conn, String id) throws SQLException {
        Redemption row;
        try (PreparedStatement ps = prepare(conn, "SELECT * FROM " + redemptions + " WHERE id=?", id);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            row = new Redemption(rs.getString("id"), rs.getLong("qq_id"), rs.getString("prize_id"),
                UUID.fromString(rs.getString("player_uuid")), rs.getString("player_name"), rs.getInt("cost"),
                rs.getString("redeem_date"), RedemptionState.valueOf(rs.getString("state")), rs.getLong("created_at"),
                rs.getLong("updated_at"), rs.getString("note"), List.of());
        }
        List<RedemptionItem> lines = new ArrayList<>();
        try (PreparedStatement ps = prepare(conn, "SELECT ordinal,preset_id,state,detail FROM " + items + " WHERE redemption_id=? ORDER BY ordinal", id);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lines.add(new RedemptionItem(rs.getInt(1), rs.getString(2), RedemptionItemState.valueOf(rs.getString(3)), rs.getString(4)));
        }
        return new Redemption(row.id(), row.qqId(), row.prizeId(), row.playerUuid(), row.playerName(), row.cost(), row.date(),
            row.state(), row.createdAt(), row.updatedAt(), row.note(), List.copyOf(lines));
    }

    private void lockAccounts(Connection conn, long... qqs) throws SQLException {
        for (long qq : qqs) {
            execute(conn, mysql ? "INSERT IGNORE INTO " + points + " (qq_id) VALUES (?)"
                : "INSERT INTO " + points + " (qq_id) VALUES (?) ON CONFLICT(qq_id) DO NOTHING", qq);
            if (mysql) {
                try (PreparedStatement ps = prepare(conn, "SELECT qq_id FROM " + points + " WHERE qq_id=? FOR UPDATE", qq);
                     ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("积分账户不存在"); }
            }
        }
    }

    private int balance(Connection conn, long qq) throws SQLException {
        return integer(conn, "SELECT balance FROM " + points + " WHERE qq_id=?", 0, qq);
    }

    private boolean debit(Connection conn, long qq, int amount) throws SQLException {
        if (amount == 0) return true;
        return execute(conn, "UPDATE " + points + " SET balance=balance-?,total_spent=total_spent+?,updated_at=?"
            + " WHERE qq_id=? AND balance>=? AND total_spent<=?", amount, amount, System.currentTimeMillis(), qq, amount,
            Integer.MAX_VALUE - amount) == 1;
    }

    private void credit(Connection conn, long qq, int amount, boolean earned) throws SQLException {
        int earnedAmount = earned ? amount : 0;
        if (execute(conn, "UPDATE " + points + " SET balance=balance+?,total_earned=total_earned+?,updated_at=?"
            + " WHERE qq_id=? AND balance<=? AND total_earned<=?", amount, earnedAmount, System.currentTimeMillis(), qq,
            Integer.MAX_VALUE - amount, Integer.MAX_VALUE - earnedAmount) != 1) throw new SQLException("积分入账失败或溢出");
    }

    private void log(Connection conn, String operation, long qq, int delta, String action, String detail) throws SQLException {
        execute(conn, "INSERT INTO " + ledger + " (id,operation_id,qq_id,delta,action,detail,created_at) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(), operation, qq, delta, truncate(action, 64), truncate(detail, 1000), System.currentTimeMillis());
    }

    private <T> T transaction(Work<T> work) {
        try (Connection conn = source.getConnection()) {
            boolean auto = conn.getAutoCommit();
            int isolation = conn.getTransactionIsolation();
            // The account guard serializes writers. Reads after waiting for it must see the
            // preceding commit, rather than an earlier MySQL REPEATABLE READ snapshot.
            if (mysql && isolation != Connection.TRANSACTION_READ_COMMITTED) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }
            if (!mysql) {
                try (var statement = conn.createStatement()) { statement.execute("PRAGMA busy_timeout=5000"); }
            }
            conn.setAutoCommit(false);
            try {
                // SQLite deferred transactions must acquire the write lock before their first read.
                if (!mysql) execute(conn, "UPDATE " + points + " SET updated_at=updated_at WHERE qq_id=-1");
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            } finally {
                conn.setAutoCommit(auto);
                if (mysql && isolation != Connection.TRANSACTION_READ_COMMITTED) conn.setTransactionIsolation(isolation);
            }
        } catch (SQLException | RuntimeException e) {
            if (e instanceof StorageFailure failure) throw failure;
            throw new StorageFailure("QQBot 积分事务失败", e);
        }
    }

    private static PreparedStatement prepare(Connection conn, String sql, Object... args) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        } catch (SQLException e) { ps.close(); throw e; }
    }

    private static int execute(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(conn, sql, args)) { return ps.executeUpdate(); }
    }

    private static int integer(Connection conn, String sql, int fallback, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(conn, sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : fallback;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @FunctionalInterface private interface Work<T> { T run(Connection conn) throws SQLException; }
}
