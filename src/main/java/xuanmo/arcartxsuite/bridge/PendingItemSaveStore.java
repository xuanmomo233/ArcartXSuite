package xuanmo.arcartxsuite.bridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/** Persistence for items staged before a player inventory is changed. */
final class PendingItemSaveStore {
    private static final int SAVING = -2;
    private static final int REVIEW = -1;
    static final String SHARED_MAIL = "shared-mail";
    static final String SHARED_WAREHOUSE = "shared-warehouse";
    private static final long ROUTED_RETRY_MS = 30_000;
    private final StorageManager storage;

    PendingItemSaveStore(StorageManager storage) {
        this.storage = storage;
    }

    enum Mode {
        NATURAL, CHECKPOINT, WAREHOUSE, MAIL;

        boolean routed() { return this == WAREHOUSE || this == MAIL; }
    }
    enum Resolution { STORED, UNAPPLIED, REVIEW }

    record Save(UUID player, String node, String receipt, long boot, Mode mode, List<Long> rewards) {}

    static boolean tracks(PendingReward reward) {
        return switch (reward.type()) {
            case ITEM_SERIALIZED -> switch (reward.sourceModule()) {
                case "fishing", "entitytracker", "chat", "afkreward", "lottery" -> true;
                default -> false;
            };
            case ITEM -> "entitytracker".equals(reward.sourceModule()) || "fishing".equals(reward.sourceModule());
            case ITEM_SPEC, ITEM_NATURAL -> "fishing".equals(reward.sourceModule());
            default -> false;
        };
    }

    static boolean requiresCheckpoint(PendingReward reward) {
        return reward.type() == PendingReward.Type.ITEM_SERIALIZED && "fishing".equals(reward.sourceModule());
    }

    void initialize(Statement stmt, boolean mysql) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS axs_pending_item_saves (
                player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                node_id VARCHAR(36) NOT NULL,
                receipt VARCHAR(36) NOT NULL,
                server_boot BIGINT NOT NULL,
                save_mode VARCHAR(16) NOT NULL DEFAULT 'NATURAL',
                retry_at BIGINT NOT NULL DEFAULT 0,
                reward_ids VARCHAR(200) NOT NULL%s)
            """.formatted(mysql ? ", INDEX idx_pending_save_node (node_id, player_uuid)" : ""));
        if (!mysql) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_save_node ON axs_pending_item_saves(node_id, player_uuid)");
        }
        ensureColumn(stmt, "save_mode", "VARCHAR(16) NOT NULL DEFAULT 'NATURAL'");
        ensureColumn(stmt, "retry_at", "BIGINT NOT NULL DEFAULT 0");
    }

    boolean sharedHostStorage() {
        var descriptor = storage.getDescriptor();
        return descriptor != null && descriptor.isMysql();
    }

    private static void ensureColumn(Statement stmt, String name, String definition) throws SQLException {
        String probe = "SELECT " + name + " FROM axs_pending_item_saves WHERE 1=0";
        try (var ignored = stmt.executeQuery(probe)) {
            return;
        } catch (SQLException missing) {
            try {
                stmt.executeUpdate("ALTER TABLE axs_pending_item_saves ADD COLUMN " + name + " " + definition);
            } catch (SQLException alter) {
                // On shared MySQL another node can apply the same additive change first.
                try (var ignored = stmt.executeQuery(probe)) {
                    return;
                } catch (SQLException verification) {
                    alter.addSuppressed(verification);
                    throw alter;
                }
            }
        }
    }

    boolean reserve(Save save, String claim, long claimedAt) throws SQLException {
        return reserve(save, claim, claimedAt, Map.of());
    }

    boolean reserve(Save save, String claim, long claimedAt, Map<Long, String> frozen) throws SQLException {
        return transaction(conn -> {
            boolean mysql = sharedHostStorage();
            String upsert = mysql ? " ON DUPLICATE KEY UPDATE receipt=receipt" : " ON CONFLICT(player_uuid) DO NOTHING";
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO axs_pending_item_saves (player_uuid, node_id, receipt, server_boot, save_mode, reward_ids, retry_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """ + upsert)) {
                ps.setString(1, save.player().toString());
                ps.setString(2, save.node());
                ps.setString(3, save.receipt());
                ps.setLong(4, save.boot());
                ps.setString(5, save.mode().name());
                ps.setString(6, save.rewards().stream().map(String::valueOf).collect(Collectors.joining(",")));
                ps.setLong(7, save.mode().routed() ? System.currentTimeMillis() + ROUTED_RETRY_MS : 0);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT receipt FROM axs_pending_item_saves WHERE player_uuid=?")) {
                ps.setString(1, save.player().toString());
                try (var rs = ps.executeQuery()) {
                    if (!rs.next() || !save.receipt().equals(rs.getString(1))) return false;
                }
            }
            if (!frozen.isEmpty() && !freezeRewards(conn, save, claim, claimedAt, frozen)) return false;
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE axs_pending_rewards SET attempts=?
                    WHERE player_uuid=? AND claimed_by=? AND claimed_at=? AND attempts>=0
                    AND source_module IN ('fishing','entitytracker','chat','afkreward','lottery')
                    AND reward_type IN ('ITEM_SERIALIZED','ITEM_NATURAL') AND id IN (
                    """ + placeholders(save.rewards()) + ")")) {
                ps.setInt(1, SAVING);
                ps.setString(2, save.player().toString());
                ps.setString(3, claim);
                ps.setLong(4, claimedAt);
                bindIds(ps, 5, save.rewards());
                if (ps.executeUpdate() != save.rewards().size()) {
                    // A claim was released or replaced before the inventory could be staged.
                    return false;
                }
            }
            return true;
        });
    }

    private boolean freezeRewards(Connection conn, Save save, String claim, long claimedAt, Map<Long, String> frozen)
            throws SQLException {
        if (!save.rewards().containsAll(frozen.keySet())) return false;
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE axs_pending_rewards
                SET reward_type=CASE WHEN source_module='fishing' THEN 'ITEM_NATURAL' ELSE 'ITEM_SERIALIZED' END, payload=?
                WHERE player_uuid=? AND claimed_by=? AND claimed_at=? AND attempts>=0
                  AND ((source_module='entitytracker' AND reward_type='ITEM')
                    OR (source_module='fishing' AND reward_type IN ('ITEM','ITEM_SPEC'))) AND id=?
                """)) {
            for (var entry : frozen.entrySet()) {
                ps.setString(1, entry.getValue());
                ps.setString(2, save.player().toString());
                ps.setString(3, claim);
                ps.setLong(4, claimedAt);
                ps.setLong(5, entry.getKey());
                if (ps.executeUpdate() != 1) return false;
            }
        }
        return true;
    }

    List<Save> load(String node, String after, int limit) throws SQLException {
        return load(node, false, false, after, limit, Long.MAX_VALUE);
    }

    List<Save> load(String node, boolean sharedMail, boolean sharedWarehouse, String after, int limit, long now)
            throws SQLException {
        var saves = new ArrayList<Save>();
        String owners = BuildEdition.CROSS_SERVER_INCLUDED && (sharedMail || sharedWarehouse)
            ? "node_id IN (?,?,?)" : "node_id=?";
        try (Connection conn = storage.getHostConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT player_uuid, node_id, receipt, server_boot, save_mode, reward_ids FROM axs_pending_item_saves
                WHERE %s AND player_uuid>? AND retry_at<=? ORDER BY player_uuid LIMIT ?
                """.formatted(owners))) {
            int index = 1;
            ps.setString(index++, node);
            if (BuildEdition.CROSS_SERVER_INCLUDED && (sharedMail || sharedWarehouse)) {
                ps.setString(index++, sharedMail ? SHARED_MAIL : node);
                ps.setString(index++, sharedWarehouse ? SHARED_WAREHOUSE : node);
            }
            ps.setString(index++, after);
            ps.setLong(index++, now);
            ps.setInt(index, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) saves.add(readSave(rs));
            }
        }
        return saves;
    }

    Save loadLegacyRoute(UUID player, boolean mailAvailable, boolean warehouseAvailable, long now) throws SQLException {
        if (!mailAvailable && !warehouseAvailable) return null;
        try (Connection conn = storage.getHostConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT player_uuid, node_id, receipt, server_boot, save_mode, reward_ids FROM axs_pending_item_saves
                WHERE player_uuid=? AND retry_at<=? AND
                    ((save_mode='MAIL' AND node_id=?) OR (save_mode='WAREHOUSE' AND node_id=?))
                """)) {
            ps.setString(1, player.toString());
            ps.setLong(2, now);
            ps.setString(3, mailAvailable ? SHARED_MAIL : null);
            ps.setString(4, warehouseAvailable ? SHARED_WAREHOUSE : null);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? readSave(rs) : null;
            }
        }
    }

    private static Save readSave(ResultSet rs) throws SQLException {
        var ids = Arrays.stream(rs.getString("reward_ids").split(",")).map(Long::parseLong).toList();
        return new Save(UUID.fromString(rs.getString("player_uuid")), rs.getString("node_id"), rs.getString("receipt"),
            rs.getLong("server_boot"), Mode.valueOf(rs.getString("save_mode")), ids);
    }

    boolean claimRouted(Save save, long now) throws SQLException {
        if (!save.mode().routed()) return false;
        try (Connection conn = storage.getHostConnection(); PreparedStatement ps = conn.prepareStatement("""
                UPDATE axs_pending_item_saves SET retry_at=?
                WHERE player_uuid=? AND node_id=? AND receipt=? AND save_mode=? AND retry_at<=?
                """)) {
            ps.setLong(1, now + ROUTED_RETRY_MS);
            ps.setString(2, save.player().toString());
            ps.setString(3, save.node());
            ps.setString(4, save.receipt());
            ps.setString(5, save.mode().name());
            ps.setLong(6, now);
            return ps.executeUpdate() == 1;
        }
    }

    boolean routeWarehouseToMail(Save save, String owner) throws SQLException {
        try (Connection conn = storage.getHostConnection(); PreparedStatement ps = conn.prepareStatement("""
                UPDATE axs_pending_item_saves SET save_mode='MAIL', node_id=?, retry_at=0
                WHERE player_uuid=? AND node_id=? AND receipt=? AND save_mode='WAREHOUSE'
                """)) {
            ps.setString(1, owner);
            ps.setString(2, save.player().toString());
            ps.setString(3, save.node());
            ps.setString(4, save.receipt());
            return ps.executeUpdate() == 1;
        }
    }

    List<PendingReward> loadRoutedRewards(Save save) throws SQLException {
        var rewards = new HashMap<Long, PendingReward>();
        try (Connection conn = storage.getHostConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT id, source_module, reward_type, payload, description, created_at FROM axs_pending_rewards
                WHERE player_uuid=? AND attempts=? AND source_module IN ('fishing','entitytracker','chat','afkreward','lottery')
                  AND reward_type IN ('ITEM_SERIALIZED','ITEM_NATURAL') AND id IN (
                """ + placeholders(save.rewards()) + ")")) {
            ps.setString(1, save.player().toString());
            ps.setInt(2, SAVING);
            bindIds(ps, 3, save.rewards());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    rewards.put(id, new PendingReward(id, save.player(), rs.getString("source_module"),
                        PendingReward.Type.valueOf(rs.getString("reward_type")),
                        rs.getString("payload"), rs.getString("description"), rs.getLong("created_at"), SAVING));
                }
            }
        }
        if (rewards.size() != save.rewards().size()) throw new SQLException("溢出投递的待发记录不完整");
        return save.rewards().stream().map(rewards::get).toList();
    }

    boolean finish(Save save, Resolution resolution) throws SQLException {
        return transaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM axs_pending_item_saves WHERE player_uuid=? AND node_id=? AND receipt=? AND save_mode=?")) {
                ps.setString(1, save.player().toString());
                ps.setString(2, save.node());
                ps.setString(3, save.receipt());
                ps.setString(4, save.mode().name());
                if (ps.executeUpdate() != 1) return false;
            }
            String statement = switch (resolution) {
                case STORED -> "DELETE FROM axs_pending_rewards";
                case UNAPPLIED -> "UPDATE axs_pending_rewards SET attempts=0, claimed_by=NULL, claimed_at=NULL";
                case REVIEW -> "UPDATE axs_pending_rewards SET attempts=" + REVIEW + ", claimed_by=NULL, claimed_at=NULL";
            };
            try (PreparedStatement ps = conn.prepareStatement(statement
                    + " WHERE player_uuid=? AND attempts=? AND id IN (" + placeholders(save.rewards()) + ")")) {
                ps.setString(1, save.player().toString());
                ps.setInt(2, SAVING);
                bindIds(ps, 3, save.rewards());
                if (ps.executeUpdate() != save.rewards().size()) {
                    throw new SQLException("待发物品与玩家存档凭证的关联已变化");
                }
            }
            return true;
        });
    }

    private boolean transaction(Work work) throws SQLException {
        try (Connection conn = storage.getHostConnection()) {
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                boolean result = work.run(conn);
                if (result) conn.commit(); else conn.rollback();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            } finally {
                conn.setAutoCommit(auto);
            }
        }
    }

    private static String placeholders(List<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static void bindIds(PreparedStatement ps, int first, List<Long> ids) throws SQLException {
        for (int index = 0; index < ids.size(); index++) ps.setLong(first + index, ids.get(index));
    }

    @FunctionalInterface
    private interface Work {
        boolean run(Connection conn) throws SQLException;
    }
}
