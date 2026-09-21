package xuanmo.arcartxsuite.chat.storage;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketClaim;
import xuanmo.arcartxsuite.chat.model.RedPacketClaimOutcome;
import xuanmo.arcartxsuite.chat.model.RedPacketEntryRow;
import xuanmo.arcartxsuite.chat.model.RedPacketReward;
import xuanmo.arcartxsuite.chat.model.RedPacketRewardKind;
import xuanmo.arcartxsuite.chat.model.RedPacketType;

/**
 * 基于 JDBC 的聊天数据仓储实现，支持 SQLite 与 MySQL 两种后端。
 * <p>
 * 继承 {@link AbstractModuleRepository} 获得连接池管理与数据迁移能力，
 * 实现 {@link ChatRepository} 接口提供玩家状态、禁言记录与玩家档案的 CRUD 操作。
 * 建表语句根据存储模式自动适配方言。
 */
public final class JdbcChatRepository extends AbstractModuleRepository implements ChatRepository {

    public JdbcChatRepository(DataSource dataSource, StorageDescriptor descriptor,
                              File dataFolder, Logger logger) {
        super("AXS-Chat", dataSource, descriptor, dataFolder, logger);
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("chat_player_states", "chat_player_mutes", "chat_player_profiles", "chat_ignored_players", "chat_red_packet_claims");
    }

    @Override
    protected List<String> allTables() {
        return List.of("chat_player_states", "chat_player_mutes", "chat_player_profiles", "chat_ignored_players",
            "chat_red_packets", "chat_red_packet_entries", "chat_red_packet_claims");
    }

    @Override
    public ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException {
        String channelId = defaultChannelId;
        boolean acceptsPrivate = true;
        boolean acceptsMentions = true;
        boolean socialSpy = false;
        Instant updatedAt = Instant.now();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at "
                     + "FROM chat_player_states WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    channelId = nullToEmpty(resultSet.getString("current_channel_id"));
                    acceptsPrivate = resultSet.getBoolean("accepts_private_messages");
                    acceptsMentions = resultSet.getBoolean("accepts_mentions");
                    socialSpy = resultSet.getBoolean("social_spy_enabled");
                    updatedAt = Instant.ofEpochMilli(resultSet.getLong("updated_at"));
                }
            }
        }

        Set<UUID> ignored = new LinkedHashSet<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT ignored_uuid FROM chat_ignored_players WHERE player_uuid = ? ORDER BY ignored_uuid ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ignored.add(UUID.fromString(resultSet.getString("ignored_uuid")));
                }
            }
        }
        return new ChatPlayerState(playerUuid, channelId.isBlank() ? defaultChannelId : channelId, acceptsPrivate, acceptsMentions, socialSpy, ignored, updatedAt);
    }

    @Override
    public void saveState(ChatPlayerState state) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(stateUpsertSql())) {
            statement.setString(1, state.playerUuid().toString());
            statement.setString(2, state.currentChannelId());
            statement.setBoolean(3, state.acceptsPrivateMessages());
            statement.setBoolean(4, state.acceptsMentions());
            statement.setBoolean(5, state.socialSpyEnabled());
            statement.setLong(6, state.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void saveIgnoredPlayers(UUID playerUuid, Set<UUID> ignoredPlayers) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM chat_ignored_players WHERE player_uuid = ?"
            )) {
                delete.setString(1, playerUuid.toString());
                delete.executeUpdate();
            }
            if (ignoredPlayers != null && !ignoredPlayers.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chat_ignored_players (player_uuid, ignored_uuid) VALUES (?, ?)"
                )) {
                    for (UUID ignoredUuid : ignoredPlayers) {
                        insert.setString(1, playerUuid.toString());
                        insert.setString(2, ignoredUuid.toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    @Override
    public Optional<ChatMuteRecord> loadMute(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, muted_by, reason, created_at, expires_at "
                     + "FROM chat_player_mutes WHERE player_uuid = ? LIMIT 1"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long expiresAt = resultSet.getLong("expires_at");
                return Optional.of(new ChatMuteRecord(
                    UUID.fromString(resultSet.getString("player_uuid")),
                    nullToEmpty(resultSet.getString("muted_by")),
                    nullToEmpty(resultSet.getString("reason")),
                    Instant.ofEpochMilli(resultSet.getLong("created_at")),
                    resultSet.wasNull() ? null : Instant.ofEpochMilli(expiresAt)
                ));
            }
        }
    }

    @Override
    public void saveMute(ChatMuteRecord muteRecord) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(muteUpsertSql())) {
            statement.setString(1, muteRecord.playerUuid().toString());
            statement.setString(2, nullToEmpty(muteRecord.mutedBy()));
            statement.setString(3, nullToEmpty(muteRecord.reason()));
            statement.setLong(4, muteRecord.createdAt().toEpochMilli());
            if (muteRecord.expiresAt() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, muteRecord.expiresAt().toEpochMilli());
            }
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteMute(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM chat_player_mutes WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void upsertProfile(ChatPlayerProfile profile) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(profileUpsertSql())) {
            statement.setString(1, profile.playerUuid().toString());
            statement.setString(2, nullToEmpty(profile.lastKnownName()));
            statement.setString(3, nullToEmpty(profile.lastKnownName()).toLowerCase());
            statement.setLong(4, profile.lastSeenAt().toEpochMilli());
            statement.setString(5, nullToEmpty(profile.lastServer()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<ChatPlayerProfile> loadProfile(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_seen_at, last_server "
                     + "FROM chat_player_profiles WHERE player_uuid = ? LIMIT 1"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readProfile(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<ChatPlayerProfile> findProfileByName(String playerName) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_seen_at, last_server "
                     + "FROM chat_player_profiles WHERE last_known_name_lower = ? LIMIT 1"
             )) {
            statement.setString(1, nullToEmpty(playerName).trim().toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readProfile(resultSet)) : Optional.empty();
            }
        }
    }

    // ── 红包 ─────────────────────────────────────────────

    @Override
    public void insertRedPacket(RedPacket packet, List<RedPacketEntryRow> entries) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chat_red_packets (packet_id, packet_type, sender_uuid, sender_name, sender_display, channel_id,"
                        + " currency_id, total_amount, per_amounts, total_count, claimed_count, remaining_amount, wish, preset_id,"
                        + " admin_packet, status, created_at, expires_at, origin_node)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    statement.setString(1, packet.id());
                    statement.setString(2, packet.type().name());
                    statement.setString(3, nullToEmpty(packet.senderUuid()));
                    statement.setString(4, nullToEmpty(packet.senderName()));
                    statement.setString(5, nullToEmpty(packet.senderDisplay()));
                    statement.setString(6, nullToEmpty(packet.channelId()));
                    statement.setString(7, nullToEmpty(packet.currencyId()));
                    statement.setString(8, packet.totalAmount() == null ? "0" : packet.totalAmount().toPlainString());
                    statement.setString(9, encodeAmounts(packet.amounts()));
                    statement.setInt(10, packet.totalCount());
                    statement.setInt(11, packet.claimedCount());
                    statement.setString(12, packet.remainingAmount() == null ? "0" : packet.remainingAmount().toPlainString());
                    statement.setString(13, nullToEmpty(packet.wish()));
                    statement.setString(14, nullToEmpty(packet.presetId()));
                    statement.setBoolean(15, packet.admin());
                    statement.setString(16, packet.status());
                    statement.setLong(17, packet.createdAt().toEpochMilli());
                    statement.setLong(18, packet.expiresAt().toEpochMilli());
                    statement.setString(19, nullToEmpty(packet.originNode()));
                    statement.executeUpdate();
                }
                if (entries != null && !entries.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO chat_red_packet_entries (packet_id, entry_index, reward_type, currency_id, amount,"
                            + " item_data, command_text, description, weight, remaining) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    )) {
                        for (RedPacketEntryRow entry : entries) {
                            RedPacketReward reward = entry.reward();
                            statement.setString(1, packet.id());
                            statement.setInt(2, entry.entryIndex());
                            statement.setString(3, reward.kind().name());
                            statement.setString(4, nullToEmpty(reward.currencyId()));
                            statement.setString(5, reward.amount() == null ? "" : reward.amount().toPlainString());
                            statement.setString(6, nullToEmpty(reward.itemData()));
                            statement.setString(7, nullToEmpty(reward.command()));
                            statement.setString(8, nullToEmpty(reward.description()));
                            statement.setInt(9, entry.weight());
                            statement.setInt(10, entry.remaining());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public Optional<RedPacket> findRedPacket(String packetId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT packet_id, packet_type, sender_uuid, sender_name, sender_display, channel_id, currency_id,"
                     + " total_amount, per_amounts, total_count, claimed_count, remaining_amount, wish, preset_id,"
                     + " admin_packet, status, created_at, expires_at, origin_node"
                     + " FROM chat_red_packets WHERE packet_id = ? LIMIT 1"
             )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readPacket(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<RedPacket> loadActiveRedPackets() throws SQLException {
        List<RedPacket> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT packet_id, packet_type, sender_uuid, sender_name, sender_display, channel_id, currency_id,"
                     + " total_amount, per_amounts, total_count, claimed_count, remaining_amount, wish, preset_id,"
                     + " admin_packet, status, created_at, expires_at, origin_node"
                     + " FROM chat_red_packets WHERE status = 'active' ORDER BY created_at ASC"
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(readPacket(resultSet));
            }
        }
        return result;
    }

    @Override
    public RedPacketClaimOutcome claimRedPacket(String packetId, UUID playerUuid, String playerName,
                                              boolean senderCanClaim, Instant now) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                RedPacket packet = selectPacketForUpdate(connection, packetId);
                if (packet == null) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.NOT_FOUND);
                }
                if (packet.expired(now)) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXPIRED, packet);
                }
                if (!RedPacket.STATUS_ACTIVE.equals(packet.status()) || packet.claimedCount() >= packet.totalCount()) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                }
                if (!senderCanClaim && packet.senderUuid() != null
                        && packet.senderUuid().equals(playerUuid.toString())) {
                    connection.rollback();
                    return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.SENDER_DENIED, packet);
                }
                try (PreparedStatement dupCheck = connection.prepareStatement(
                    "SELECT 1 FROM chat_red_packet_claims WHERE packet_id = ? AND player_uuid = ? LIMIT 1"
                )) {
                    dupCheck.setString(1, packetId);
                    dupCheck.setString(2, playerUuid.toString());
                    try (ResultSet rs = dupCheck.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.ALREADY_CLAIMED, packet);
                        }
                    }
                }

                int slotIndex = packet.claimedCount();
                RedPacketReward reward;
                BigDecimal currencyAmount = null;
                boolean poolDrained = false;
                if (packet.type() == RedPacketType.POOL) {
                    List<RedPacketEntryRow> entries = selectPacketEntries(connection, packetId);
                    RedPacketEntryRow picked = pickWeighted(entries);
                    if (picked == null) {
                        markPacketStatus(connection, packetId, RedPacket.STATUS_EXHAUSTED);
                        connection.commit();
                        return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                    }
                    if (picked.remaining() > 0) {
                        try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE chat_red_packet_entries SET remaining = remaining - 1 WHERE packet_id = ? AND entry_index = ?"
                        )) {
                            update.setString(1, packetId);
                            update.setInt(2, picked.entryIndex());
                            update.executeUpdate();
                        }
                    }
                    // 扣减后检查池是否仍有可抽奖项
                    List<RedPacketEntryRow> after = selectPacketEntries(connection, packetId);
                    poolDrained = pickWeighted(after) == null;
                    reward = picked.reward();
                    if (reward.kind() == RedPacketRewardKind.CURRENCY && reward.amount() != null) {
                        currencyAmount = reward.amount();
                    }
                } else {
                    List<BigDecimal> amounts = packet.amounts();
                    if (slotIndex >= amounts.size()) {
                        connection.rollback();
                        return RedPacketClaimOutcome.of(RedPacketClaimOutcome.Status.EXHAUSTED, packet);
                    }
                    currencyAmount = amounts.get(slotIndex);
                    reward = RedPacketReward.currency(packet.currencyId(), currencyAmount,
                        currencyAmount.toPlainString() + " " + packet.currencyId());
                }

                String rewardDesc = reward.description().isBlank()
                    ? reward.kind().name().toLowerCase(java.util.Locale.ROOT)
                    : reward.description();
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chat_red_packet_claims (packet_id, player_uuid, player_name, reward_desc, amount, claimed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, packetId);
                    insert.setString(2, playerUuid.toString());
                    insert.setString(3, nullToEmpty(playerName));
                    insert.setString(4, rewardDesc);
                    insert.setString(5, currencyAmount == null ? null : currencyAmount.toPlainString());
                    insert.setLong(6, now.toEpochMilli());
                    insert.executeUpdate();
                }

                int newClaimed = packet.claimedCount() + 1;
                BigDecimal newRemaining = packet.remainingAmount()
                    .subtract(currencyAmount == null ? BigDecimal.ZERO : currencyAmount);
                String newStatus = (newClaimed >= packet.totalCount() || poolDrained)
                    ? RedPacket.STATUS_EXHAUSTED : RedPacket.STATUS_ACTIVE;
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE chat_red_packets SET claimed_count = ?, remaining_amount = ?, status = ? WHERE packet_id = ?"
                )) {
                    update.setInt(1, newClaimed);
                    update.setString(2, newRemaining.toPlainString());
                    update.setString(3, newStatus);
                    update.setString(4, packetId);
                    update.executeUpdate();
                }
                connection.commit();

                RedPacket updated = new RedPacket(
                    packet.id(), packet.type(), packet.senderUuid(), packet.senderName(), packet.senderDisplay(),
                    packet.channelId(), packet.currencyId(), packet.totalAmount(), newRemaining, packet.amounts(),
                    packet.totalCount(), newClaimed, packet.wish(), packet.presetId(), packet.admin(),
                    newStatus, packet.createdAt(), packet.expiresAt(), packet.originNode()
                );
                return RedPacketClaimOutcome.success(updated, reward, slotIndex, currencyAmount);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean updateRedPacketStatus(String packetId, String status) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE chat_red_packets SET status = ? WHERE packet_id = ? AND status = 'active'"
             )) {
            statement.setString(1, status);
            statement.setString(2, packetId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public void updateRedPacketRemaining(String packetId, BigDecimal remainingAmount) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE chat_red_packets SET remaining_amount = ? WHERE packet_id = ?"
             )) {
            statement.setString(1, remainingAmount == null ? "0" : remainingAmount.toPlainString());
            statement.setString(2, packetId);
            statement.executeUpdate();
        }
    }

    @Override
    public List<RedPacketClaim> listRedPacketClaims(String packetId) throws SQLException {
        List<RedPacketClaim> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT packet_id, player_uuid, player_name, reward_desc, amount, claimed_at"
                     + " FROM chat_red_packet_claims WHERE packet_id = ? ORDER BY claimed_at ASC"
             )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new RedPacketClaim(
                        resultSet.getString("packet_id"),
                        resultSet.getString("player_uuid"),
                        nullToEmpty(resultSet.getString("player_name")),
                        nullToEmpty(resultSet.getString("reward_desc")),
                        parseAmountOrNull(resultSet.getString("amount")),
                        Instant.ofEpochMilli(resultSet.getLong("claimed_at"))
                    ));
                }
            }
        }
        return result;
    }

    /** 读取并锁定红包行（MySQL 使用 FOR UPDATE，SQLite 由单写连接串行化）。 */
    private RedPacket selectPacketForUpdate(Connection connection, String packetId) throws SQLException {
        String sql = "SELECT packet_id, packet_type, sender_uuid, sender_name, sender_display, channel_id, currency_id,"
            + " total_amount, per_amounts, total_count, claimed_count, remaining_amount, wish, preset_id,"
            + " admin_packet, status, created_at, expires_at, origin_node"
            + " FROM chat_red_packets WHERE packet_id = ?" + (isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readPacket(resultSet) : null;
            }
        }
    }

    private List<RedPacketEntryRow> selectPacketEntries(Connection connection, String packetId) throws SQLException {
        List<RedPacketEntryRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT entry_index, reward_type, currency_id, amount, item_data, command_text, description, weight, remaining"
                + " FROM chat_red_packet_entries WHERE packet_id = ? ORDER BY entry_index ASC"
        )) {
            statement.setString(1, packetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    RedPacketRewardKind kind = RedPacketRewardKind.parse(resultSet.getString("reward_type"));
                    if (kind == null) {
                        continue;
                    }
                    RedPacketReward reward = new RedPacketReward(
                        kind,
                        nullToEmpty(resultSet.getString("currency_id")),
                        parseAmountOrNull(resultSet.getString("amount")),
                        nullToEmpty(resultSet.getString("item_data")),
                        nullToEmpty(resultSet.getString("command_text")),
                        nullToEmpty(resultSet.getString("description"))
                    );
                    result.add(new RedPacketEntryRow(
                        resultSet.getInt("entry_index"), reward,
                        resultSet.getInt("weight"), resultSet.getInt("remaining")
                    ));
                }
            }
        }
        return result;
    }

    /** 在可抽取条目（remaining != 0）中按权重随机选取。 */
    private static RedPacketEntryRow pickWeighted(List<RedPacketEntryRow> entries) {
        int totalWeight = 0;
        for (RedPacketEntryRow entry : entries) {
            if (entry.drawable()) {
                totalWeight += Math.max(1, entry.weight());
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (RedPacketEntryRow entry : entries) {
            if (!entry.drawable()) {
                continue;
            }
            roll -= Math.max(1, entry.weight());
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }

    private void markPacketStatus(Connection connection, String packetId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE chat_red_packets SET status = ? WHERE packet_id = ?"
        )) {
            statement.setString(1, status);
            statement.setString(2, packetId);
            statement.executeUpdate();
        }
    }

    private static RedPacket readPacket(ResultSet resultSet) throws SQLException {
        String senderUuid = nullToEmpty(resultSet.getString("sender_uuid"));
        RedPacketType type = RedPacketType.parse(resultSet.getString("packet_type"));
        return new RedPacket(
            resultSet.getString("packet_id"),
            type == null ? RedPacketType.NORMAL : type,
            senderUuid.isBlank() ? null : senderUuid,
            nullToEmpty(resultSet.getString("sender_name")),
            nullToEmpty(resultSet.getString("sender_display")),
            nullToEmpty(resultSet.getString("channel_id")),
            nullToEmpty(resultSet.getString("currency_id")),
            parseAmount(resultSet.getString("total_amount")),
            parseAmount(resultSet.getString("remaining_amount")),
            decodeAmounts(resultSet.getString("per_amounts")),
            resultSet.getInt("total_count"),
            resultSet.getInt("claimed_count"),
            nullToEmpty(resultSet.getString("wish")),
            nullToEmpty(resultSet.getString("preset_id")),
            resultSet.getBoolean("admin_packet"),
            nullToEmpty(resultSet.getString("status")),
            Instant.ofEpochMilli(resultSet.getLong("created_at")),
            Instant.ofEpochMilli(resultSet.getLong("expires_at")),
            nullToEmpty(resultSet.getString("origin_node"))
        );
    }

    /** 金额列以纯文本存储（VARCHAR），读取时还原为 BigDecimal，空串/非法值回退 0。 */
    private static BigDecimal parseAmount(String raw) {
        BigDecimal value = parseAmountOrNull(raw);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 解析可空金额列，空串/NULL/非法值返回 {@code null}。 */
    private static BigDecimal parseAmountOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String encodeAmounts(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < amounts.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(amounts.get(i).toPlainString());
        }
        return builder.toString();
    }

    private static List<BigDecimal> decodeAmounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<BigDecimal> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                result.add(new BigDecimal(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_states (
                        player_uuid TEXT PRIMARY KEY,
                        current_channel_id TEXT NOT NULL,
                        accepts_private_messages INTEGER NOT NULL DEFAULT 1,
                        accepts_mentions INTEGER NOT NULL DEFAULT 1,
                        social_spy_enabled INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_ignored_players (
                        player_uuid TEXT NOT NULL,
                        ignored_uuid TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, ignored_uuid)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_mutes (
                        player_uuid TEXT PRIMARY KEY,
                        muted_by TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_profiles (
                        player_uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        last_known_name_lower TEXT NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        last_server TEXT NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packets (
                        packet_id TEXT PRIMARY KEY,
                        packet_type TEXT NOT NULL,
                        sender_uuid TEXT NOT NULL,
                        sender_name TEXT NOT NULL,
                        sender_display TEXT NOT NULL,
                        channel_id TEXT NOT NULL,
                        currency_id TEXT NOT NULL,
                        total_amount TEXT NOT NULL DEFAULT '0',
                        per_amounts TEXT NOT NULL,
                        total_count INTEGER NOT NULL,
                        claimed_count INTEGER NOT NULL DEFAULT 0,
                        remaining_amount TEXT NOT NULL DEFAULT '0',
                        wish TEXT NOT NULL,
                        preset_id TEXT NOT NULL,
                        admin_packet INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        origin_node TEXT NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_entries (
                        packet_id TEXT NOT NULL,
                        entry_index INTEGER NOT NULL,
                        reward_type TEXT NOT NULL,
                        currency_id TEXT NOT NULL,
                        amount TEXT NOT NULL DEFAULT '',
                        item_data TEXT NOT NULL,
                        command_text TEXT NOT NULL,
                        description TEXT NOT NULL,
                        weight INTEGER NOT NULL,
                        remaining INTEGER NOT NULL,
                        PRIMARY KEY (packet_id, entry_index)
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_claims (
                        packet_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        reward_desc TEXT NOT NULL,
                        amount TEXT,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY (packet_id, player_uuid)
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_chat_profiles_name ON chat_player_profiles(last_known_name_lower);");
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_states (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        current_channel_id VARCHAR(64) NOT NULL,
                        accepts_private_messages BOOLEAN NOT NULL DEFAULT TRUE,
                        accepts_mentions BOOLEAN NOT NULL DEFAULT TRUE,
                        social_spy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_ignored_players (
                        player_uuid VARCHAR(36) NOT NULL,
                        ignored_uuid VARCHAR(36) NOT NULL,
                        PRIMARY KEY (player_uuid, ignored_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_mutes (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        muted_by VARCHAR(64) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_player_profiles (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        last_known_name VARCHAR(64) NOT NULL,
                        last_known_name_lower VARCHAR(64) NOT NULL,
                        last_seen_at BIGINT NOT NULL,
                        last_server VARCHAR(64) NOT NULL,
                        INDEX idx_chat_profiles_name (last_known_name_lower)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packets (
                        packet_id VARCHAR(16) PRIMARY KEY,
                        packet_type VARCHAR(16) NOT NULL,
                        sender_uuid VARCHAR(36) NOT NULL,
                        sender_name VARCHAR(64) NOT NULL,
                        sender_display VARCHAR(191) NOT NULL,
                        channel_id VARCHAR(64) NOT NULL,
                        currency_id VARCHAR(64) NOT NULL,
                        total_amount VARCHAR(64) NOT NULL DEFAULT '0',
                        per_amounts MEDIUMTEXT NOT NULL,
                        total_count INT NOT NULL,
                        claimed_count INT NOT NULL DEFAULT 0,
                        remaining_amount VARCHAR(64) NOT NULL DEFAULT '0',
                        wish VARCHAR(255) NOT NULL,
                        preset_id VARCHAR(64) NOT NULL,
                        admin_packet BOOLEAN NOT NULL DEFAULT FALSE,
                        status VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        origin_node VARCHAR(64) NOT NULL,
                        INDEX idx_rp_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_entries (
                        packet_id VARCHAR(16) NOT NULL,
                        entry_index INT NOT NULL,
                        reward_type VARCHAR(16) NOT NULL,
                        currency_id VARCHAR(64) NOT NULL,
                        amount VARCHAR(64) NOT NULL DEFAULT '',
                        item_data MEDIUMTEXT NOT NULL,
                        command_text MEDIUMTEXT NOT NULL,
                        description VARCHAR(255) NOT NULL,
                        weight INT NOT NULL,
                        remaining INT NOT NULL,
                        PRIMARY KEY (packet_id, entry_index)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_red_packet_claims (
                        packet_id VARCHAR(16) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        reward_desc VARCHAR(255) NOT NULL,
                        amount VARCHAR(64) NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (packet_id, player_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private String stateUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    current_channel_id = excluded.current_channel_id,
                    accepts_private_messages = excluded.accepts_private_messages,
                    accepts_mentions = excluded.accepts_mentions,
                    social_spy_enabled = excluded.social_spy_enabled,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO chat_player_states (player_uuid, current_channel_id, accepts_private_messages, accepts_mentions, social_spy_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                current_channel_id = VALUES(current_channel_id),
                accepts_private_messages = VALUES(accepts_private_messages),
                accepts_mentions = VALUES(accepts_mentions),
                social_spy_enabled = VALUES(social_spy_enabled),
                updated_at = VALUES(updated_at)
            """;
    }

    private String muteUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO chat_player_mutes (player_uuid, muted_by, reason, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    muted_by = excluded.muted_by,
                    reason = excluded.reason,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                """;
        }
        return """
            INSERT INTO chat_player_mutes (player_uuid, muted_by, reason, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                muted_by = VALUES(muted_by),
                reason = VALUES(reason),
                created_at = VALUES(created_at),
                expires_at = VALUES(expires_at)
            """;
    }

    private String profileUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO chat_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_seen_at, last_server)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    last_known_name = excluded.last_known_name,
                    last_known_name_lower = excluded.last_known_name_lower,
                    last_seen_at = excluded.last_seen_at,
                    last_server = excluded.last_server
                """;
        }
        return """
            INSERT INTO chat_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_seen_at, last_server)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name = VALUES(last_known_name),
                last_known_name_lower = VALUES(last_known_name_lower),
                last_seen_at = VALUES(last_seen_at),
                last_server = VALUES(last_server)
            """;
    }

    private static ChatPlayerProfile readProfile(ResultSet resultSet) throws SQLException {
        return new ChatPlayerProfile(
            UUID.fromString(resultSet.getString("player_uuid")),
            nullToEmpty(resultSet.getString("last_known_name")),
            Instant.ofEpochMilli(resultSet.getLong("last_seen_at")),
            nullToEmpty(resultSet.getString("last_server"))
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
