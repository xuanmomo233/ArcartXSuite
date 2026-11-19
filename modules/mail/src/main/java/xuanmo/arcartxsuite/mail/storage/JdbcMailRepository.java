package xuanmo.arcartxsuite.mail.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailPlayerProfile;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;

/**
 * 邮件 JDBC 仓储实现，继承 {@link AbstractModuleRepository}，提供邮件、附件、CDK、日志与玩家档案的持久化访问。
 * <p>
 * 关联模块：mail。支持 SQLite 与 MySQL 双方言，自动建表与 Schema 升级，
 * 实现 {@link MailRepository} 接口供 {@link xuanmo.arcartxsuite.mail.service.MailService} 调用。
 */
public final class JdbcMailRepository extends AbstractModuleRepository implements MailRepository {
    private static final int ATTACHMENT_READ_BATCH = 256;
    private static final int DELETE_BATCH = 128;
    private static final String DELETABLE_STATUS = "status IN ('UNREAD', 'READ', 'CLAIMED') AND claim_phase = 'NONE'";
    private static final String INSERT_LOG = "INSERT INTO mail_logs (player_uuid, log_type, content, created_at) VALUES (?, ?, ?, ?)";
    private static final String INBOX_VIEW_COLUMNS =
        "id, owner_uuid, sender_uuid, sender_name, source_type, preset_id, cdk_code, subject, body, status, "
        + "created_at, expires_at, updated_at, claimed_at, deleted_at";
    private final Logger mailLogger;

    /**
     * 构造邮件 JDBC 仓储。
     *
     * @param dataSource 数据源（由 {@link xuanmo.arcartxsuite.api.storage.StorageManager#resolveModuleDataSource} 解析）
     * @param descriptor 存储描述符（提供方言信息和表前缀）
     * @param dataFolder 模块数据目录（迁移 SQLite 目标文件时使用）
     * @param logger     日志记录器
     * @since 1.5.0
     */
    public JdbcMailRepository(DataSource dataSource, StorageDescriptor descriptor,
                              File dataFolder, Logger logger) {
        super("AXS-Mail", dataSource, descriptor, dataFolder, logger);
        this.mailLogger = logger;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        ensureClaimColumns(conn);
    }

    private void ensureClaimColumns(Connection connection) throws SQLException {
        Map<String, String> required = Map.of(
            "claim_step", "INTEGER NOT NULL DEFAULT 0",
            "claim_phase", "VARCHAR(16) NOT NULL DEFAULT 'NONE'",
            "claim_attempt", "VARCHAR(36) NULL");
        for (var entry : required.entrySet()) {
            if (hasMailColumn(connection, entry.getKey())) continue;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE mail_entries ADD COLUMN " + entry.getKey() + " " + entry.getValue());
            } catch (SQLException failure) {
                // Another node can finish the same initialization while this node waits for the DDL lock.
                if (!hasMailColumn(connection, entry.getKey())) throw failure;
            }
        }
    }

    private boolean hasMailColumn(Connection connection, String name) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "mail_entries", name)) {
            while (columns.next()) {
                if ("mail_entries".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && name.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("mail_player_profiles", "mail_entries", "mail_logs", "mail_cdk_claims", "mail_delivery_receipts");
    }

    @Override
    protected List<String> allTables() {
        // 注意顺序：先父表后子表，避免目标端存在外键约束时插入失败
        return List.of(
            "mail_player_profiles",
            "mail_entries",
            "mail_attachments",
            "mail_logs",
            "mail_cdk_definitions",
            "mail_cdk_claims",
            "mail_delivery_receipts"
        );
    }

    @Override
    public void upsertPlayerProfile(MailPlayerProfile profile) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(playerProfileUpsertSql())) {
            statement.setString(1, profile.playerUuid().toString());
            statement.setString(2, nullToEmpty(profile.lastKnownName()));
            statement.setString(3, nullToEmpty(profile.lastKnownName()).toLowerCase());
            statement.setLong(4, epochMilli(profile.lastSendAt()));
            statement.setLong(5, epochMilli(profile.lastSeenAt()));
            statement.setString(6, nullToEmpty(profile.lastServer()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<MailPlayerProfile> loadPlayerProfile(UUID playerUuid) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_send_at, last_seen_at, last_server "
                     + "FROM mail_player_profiles WHERE player_uuid = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readProfile(resultSet));
            }
        }
    }

    @Override
    public Optional<MailPlayerProfile> findPlayerProfileByName(String playerName) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_send_at, last_seen_at, last_server "
                     + "FROM mail_player_profiles WHERE last_known_name_lower = ? LIMIT 1"
             )) {
            statement.setString(1, nullToEmpty(playerName).trim().toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readProfile(resultSet));
            }
        }
    }

    @Override
    public List<MailPlayerProfile> loadAllProfiles() throws SQLException {
        List<MailPlayerProfile> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT player_uuid, last_known_name, last_send_at, last_seen_at, last_server "
                     + "FROM mail_player_profiles ORDER BY last_seen_at DESC"
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(readProfile(resultSet));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public MailInsertResult insertMail(MailMessage message) throws SQLException {
        return new MailInsertResult(insertMail(null, message).id());
    }

    @Override
    public DurableInsert insertDurableMail(String operationKey, MailMessage message) throws SQLException {
        if (operationKey == null || !operationKey.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid mail operation key");
        }
        return insertMail(operationKey, message);
    }

    @Override
    public long resolvePlayerSend(String operationKey, UUID recipient) throws SQLException {
        try (Connection connection = getConnection()) {
            // The unique key serializes this decision with the original transaction, even after a lost commit reply.
            try {
                reserveDeliveryReceipt(connection, operationKey, recipient, -1L);
                return 0L;
            } catch (SQLException failure) {
                long existing = deliveryReceipt(connection, operationKey, recipient);
                if (existing != 0L) return Math.max(0L, existing);
                throw failure;
            }
        }
    }

    @Override
    public long readPlayerSendReceipt(String operationKey, UUID recipient) throws SQLException {
        try (Connection connection = getConnection()) {
            return deliveryReceipt(connection, operationKey, recipient);
        }
    }

    private DurableInsert insertMail(String operationKey, MailMessage message) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (operationKey != null) {
                    try {
                        reserveDeliveryReceipt(connection, operationKey, message.ownerUuid(), 0L);
                    } catch (SQLException failure) {
                        connection.rollback();
                        long existing = deliveryReceipt(connection, operationKey, message.ownerUuid());
                        if (existing > 0) return new DurableInsert(existing, true);
                        throw failure;
                    }
                }
                long mailId = insertMailAndAttachments(connection, message);
                if (operationKey != null) {
                    try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mail_delivery_receipts SET mail_id=? WHERE player_uuid=? AND operation_key=?"
                    )) {
                        update.setLong(1, mailId);
                        update.setString(2, message.ownerUuid().toString());
                        update.setString(3, operationKey);
                        if (update.executeUpdate() != 1) throw new SQLException("Mail receipt reservation is missing");
                    }
                }
                connection.commit();
                return new DurableInsert(mailId, false);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void reserveDeliveryReceipt(Connection connection, String key, UUID owner, long mailId) throws SQLException {
        try (PreparedStatement reserve = connection.prepareStatement(
            "INSERT INTO mail_delivery_receipts (player_uuid, operation_key, mail_id) VALUES (?, ?, ?)"
        )) {
            reserve.setString(1, owner.toString());
            reserve.setString(2, key);
            reserve.setLong(3, mailId);
            if (reserve.executeUpdate() != 1) throw new SQLException("Mail receipt reservation failed");
        }
    }

    private long deliveryReceipt(Connection connection, String operationKey, UUID player) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT mail_id FROM mail_delivery_receipts WHERE player_uuid=? AND operation_key=?"
        )) {
            query.setString(1, player.toString());
            query.setString(2, operationKey);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    private long insertMailAndAttachments(Connection connection, MailMessage message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(mailInsertSql(), Statement.RETURN_GENERATED_KEYS)) {
                writeMail(statement, message);
                statement.executeUpdate();
                long mailId;
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new SQLException("插入邮件失败，未返回主键。");
                    }
                    mailId = generatedKeys.getLong(1);
                }

                if (message.attachments() != null && !message.attachments().isEmpty()) {
                    try (PreparedStatement attachmentStatement = connection.prepareStatement(attachmentInsertSql())) {
                        for (MailAttachment attachment : message.attachments()) {
                            attachmentStatement.setLong(1, mailId);
                            attachmentStatement.setInt(2, attachment.sortOrder());
                            attachmentStatement.setString(3, persistedAttachmentType(attachment).name());
                            attachmentStatement.setString(4, nullToEmpty(attachment.itemData()));
                            attachmentStatement.setDouble(5, attachment.vaultAmount());
                            attachmentStatement.setString(6, nullToEmpty(attachment.normalizedCurrencyId()));
                            attachmentStatement.setDouble(7, attachment.amount());
                            attachmentStatement.setString(8, nullToEmpty(attachment.description()));
                            attachmentStatement.setString(9, xuanmo.arcartxsuite.mail.util.MailIconCodec.toYamlString(attachment.icon()));
                            attachmentStatement.addBatch();
                        }
                        attachmentStatement.executeBatch();
                    }
                }

            return mailId;
        }
    }

    @Override
    public boolean transitionMailState(MailMessage message, MailStatus next, Instant now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE mail_entries SET status = ?, updated_at = ?, deleted_at = ?
                 WHERE id = ? AND owner_uuid = ? AND status = ? AND claim_phase = 'NONE'
                 """)) {
            statement.setString(1, next.name());
            statement.setLong(2, now.toEpochMilli());
            if (next == MailStatus.DELETED) statement.setLong(3, now.toEpochMilli());
            else statement.setNull(3, java.sql.Types.BIGINT);
            statement.setLong(4, message.id());
            statement.setString(5, message.ownerUuid().toString());
            statement.setString(6, message.status().name());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean markUnreadRead(UUID ownerUuid, long mailId, Instant now) throws SQLException {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(
            "UPDATE mail_entries SET status = 'READ', updated_at = ?, deleted_at = NULL "
                + "WHERE owner_uuid = ? AND id = ? AND status = 'UNREAD' AND claim_phase = 'NONE'")) {
            statement.setLong(1, now.toEpochMilli());
            statement.setString(2, ownerUuid.toString());
            statement.setLong(3, mailId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public int deleteInbox(UUID ownerUuid, boolean allowUnclaimed, Instant now) throws SQLException {
        try (Connection connection = getConnection()) {
            if (isMysql()) {
                // Limit this to the next transaction. MariaDB's snapshot isolation rejects
                // locking a row changed since an earlier consistent read at REPEATABLE READ.
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED");
                }
            }
            connection.setAutoCommit(false);
            int deleted = 0;
            try {
                if (!isMysql()) {
                    // Take SQLite's writer lock before reading the candidates.
                    try (Statement writer = connection.createStatement()) {
                        writer.executeUpdate("UPDATE mail_entries SET id = id WHERE 0 = 1");
                    }
                }
                long upperId = newestInboxId(connection, ownerUuid);
                DeleteCandidate before = null;
                while (upperId > 0) {
                    var rows = deleteCandidates(connection, ownerUuid, allowUnclaimed, upperId, now.toEpochMilli(), before);
                    if (rows.isEmpty()) break;
                    before = rows.get(rows.size() - 1);
                    var allowed = rows.stream().filter(row -> allowUnclaimed || row.claimed() || row.commands().isBlank()).toList();
                    if (!allowed.isEmpty()) deleted += deleteMailboxBatch(connection, ownerUuid, allowed, now);
                    if (rows.size() < DELETE_BATCH) break;
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            // As with individual log writes, pruning runs only after the audit rows are committed.
            if (deleted > 0) {
                try {
                    connection.setAutoCommit(true);
                    pruneLog(connection, ownerUuid);
                } catch (SQLException failure) {
                    mailLogger.warning("邮件删除日志已提交，历史裁剪暂未完成: " + failure.getMessage());
                }
            }
            return deleted;
        }
    }

    private long newestInboxId(Connection connection, UUID owner) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT MAX(id) FROM mail_entries WHERE owner_uuid = ?")) {
            query.setString(1, owner.toString());
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    private record DeleteCandidate(long id, long createdAt, String subject, String commands, boolean claimed) {}

    private List<DeleteCandidate> deleteCandidates(Connection connection, UUID owner, boolean allowUnclaimed,
                                                   long upperId, long issuedAt, DeleteCandidate before) throws SQLException {
        String sql = "SELECT id, created_at, subject, claim_commands, status FROM mail_entries WHERE owner_uuid = ?"
            + " AND id <= ? AND created_at <= ? AND " + DELETABLE_STATUS
            + (allowUnclaimed ? "" : " AND (status = 'CLAIMED' OR NOT EXISTS "
                + "(SELECT 1 FROM mail_attachments a WHERE a.mail_id = mail_entries.id))")
            + (before == null ? "" : " AND (created_at < ? OR (created_at = ? AND id < ?))")
            + " ORDER BY created_at DESC, id DESC LIMIT ?" + (isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, owner.toString());
            query.setLong(2, upperId);
            query.setLong(3, issuedAt);
            int parameter = 4;
            if (before != null) {
                query.setLong(parameter++, before.createdAt());
                query.setLong(parameter++, before.createdAt());
                query.setLong(parameter++, before.id());
            }
            query.setInt(parameter, DELETE_BATCH);
            var result = new ArrayList<DeleteCandidate>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new DeleteCandidate(rows.getLong("id"), rows.getLong("created_at"),
                        nullToEmpty(rows.getString("subject")), nullToEmpty(rows.getString("claim_commands")),
                        "CLAIMED".equalsIgnoreCase(rows.getString("status"))));
                }
            }
            return result;
        }
    }

    private int deleteMailboxBatch(Connection connection, UUID owner, List<DeleteCandidate> mails, Instant now) throws SQLException {
        String markers = String.join(",", java.util.Collections.nCopies(mails.size(), "?"));
        String sql = "UPDATE mail_entries SET status = 'DELETED', deleted_at = ?, updated_at = ? "
            + "WHERE owner_uuid = ? AND " + DELETABLE_STATUS + " AND id IN (" + markers + ")";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setLong(1, now.toEpochMilli());
            update.setLong(2, now.toEpochMilli());
            update.setString(3, owner.toString());
            for (int i = 0; i < mails.size(); i++) update.setLong(i + 4, mails.get(i).id());
            if (update.executeUpdate() != mails.size()) throw new SQLException("Mail deletion candidates changed while locked");
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_LOG)) {
            for (var mail : mails) {
                bindLog(insert, new MailLogEntry(0, owner, "delete", "删除邮件: " + mail.subject(), now));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return mails.size();
    }

    @Override
    public boolean tryClaimMail(UUID ownerUuid, long mailId, Instant now) throws SQLException {
        String sql = """
            UPDATE mail_entries SET status = 'CLAIMED', claimed_at = ?, updated_at = ?
            WHERE id = ? AND owner_uuid = ? AND status IN ('UNREAD', 'READ') AND claim_phase = 'NONE'
                AND (expires_at IS NULL OR expires_at > ?)
            """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setLong(2, now.toEpochMilli());
            statement.setLong(3, mailId);
            statement.setString(4, ownerUuid.toString());
            statement.setLong(5, now.toEpochMilli());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public ClaimProgress loadClaimProgress(UUID ownerUuid, long mailId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT claim_step, claim_phase, claim_attempt FROM mail_entries WHERE id = ? AND owner_uuid = ?")) {
            statement.setLong(1, mailId);
            statement.setString(2, ownerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing mail " + mailId);
                return readClaimProgress(result);
            }
        }
    }

    @Override
    public boolean reserveClaimStep(UUID ownerUuid, long mailId, int step, UUID attempt, Instant now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(RESERVE_CLAIM)) {
            return reserveClaim(statement, ownerUuid, mailId, step, attempt, now);
        }
    }

    private static final String RESERVE_CLAIM = """
                 UPDATE mail_entries SET status = 'CLAIM_REVIEW', claim_phase = 'EXECUTING',
                     claim_attempt = ?, claimed_at = NULL, updated_at = ?
                 WHERE id = ? AND owner_uuid = ? AND claim_step = ?
                   AND ((status = 'CLAIM_PENDING' AND claim_phase = 'READY')
                       OR (status IN ('UNREAD', 'READ') AND claim_phase = 'NONE'
                       AND (expires_at IS NULL OR expires_at > ?)))
                 """;

    private static boolean reserveClaim(PreparedStatement statement, UUID owner, long mailId, int step,
                                        UUID attempt, Instant now) throws SQLException {
        statement.setString(1, attempt.toString());
        statement.setLong(2, now.toEpochMilli());
        statement.setLong(3, mailId);
        statement.setString(4, owner.toString());
        statement.setInt(5, step);
        statement.setLong(6, now.toEpochMilli());
        return statement.executeUpdate() == 1;
    }

    @Override
    public boolean reserveItemClaims(UUID owner, List<ItemClaim> items, List<UUID> previousReceipts, Instant now) throws SQLException {
        return itemClaimTransaction(connection -> {
            if (!previousReceipts.isEmpty() && hasUnconfirmedItemReceipts(connection, owner, previousReceipts)) return false;
            try (PreparedStatement statement = connection.prepareStatement(RESERVE_CLAIM)) {
                for (var item : items) {
                    if (!reserveClaim(statement, owner, item.mailId(), item.step(), item.attempt(), now)) return false;
                }
            }
            return true;
        });
    }

    private boolean hasUnconfirmedItemReceipts(Connection connection, UUID owner, List<UUID> attempts) throws SQLException {
        String markers = String.join(",", java.util.Collections.nCopies(attempts.size(), "?"));
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT 1 FROM mail_entries WHERE owner_uuid=? AND claim_attempt IN (" + markers + ") LIMIT 1")) {
            query.setString(1, owner.toString());
            for (int i = 0; i < attempts.size(); i++) query.setString(i + 2, attempts.get(i).toString());
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    @Override
    public boolean finishItemClaims(UUID owner, List<ItemClaim> items, boolean delivered, Instant now) throws SQLException {
        return itemClaimTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_CLAIM)) {
                for (var item : items) {
                    var claim = new ClaimProgress(item.step(), ClaimPhase.EXECUTING, item.attempt());
                    if (!updateClaim(statement, owner, item.mailId(), claim, item.step() + (delivered ? 1 : 0),
                        delivered && item.last(), now)) return false;
                }
            }
            return true;
        });
    }

    @FunctionalInterface
    private interface ItemClaimWork {
        boolean apply(Connection connection) throws SQLException;
    }

    private boolean itemClaimTransaction(ItemClaimWork work) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean updated = work.apply(connection);
                if (updated) connection.commit();
                else connection.rollback();
                return updated;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean markClaimReview(UUID ownerUuid, long mailId, int step, UUID attempt, Instant now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE mail_entries SET claim_phase = 'REVIEW', updated_at = ?
                 WHERE id = ? AND owner_uuid = ? AND status = 'CLAIM_REVIEW'
                   AND claim_step = ? AND claim_phase = 'EXECUTING' AND claim_attempt = ?
                 """)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setLong(2, mailId);
            statement.setString(3, ownerUuid.toString());
            statement.setInt(4, step);
            statement.setString(5, attempt.toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean completeClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, boolean finished, Instant now) throws SQLException {
        if (expected.phase() != ClaimPhase.EXECUTING && expected.phase() != ClaimPhase.REVIEW) return false;
        return updateClaimProgress(ownerUuid, mailId, expected, expected.step() + 1, finished, now);
    }

    @Override
    public boolean releaseReviewedClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, Instant now) throws SQLException {
        if (expected.phase() != ClaimPhase.REVIEW) return false;
        return updateClaimProgress(ownerUuid, mailId, expected, expected.step(), false, now);
    }

    @Override
    public boolean releaseUnstartedClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, Instant now) throws SQLException {
        if (expected.phase() != ClaimPhase.EXECUTING) return false;
        return updateClaimProgress(ownerUuid, mailId, expected, expected.step(), false, now);
    }

    private boolean updateClaimProgress(UUID ownerUuid, long mailId, ClaimProgress expected, int nextStep,
                                        boolean finished, Instant now) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_CLAIM)) {
            return updateClaim(statement, ownerUuid, mailId, expected, nextStep, finished, now);
        }
    }

    private static final String UPDATE_CLAIM = """
                 UPDATE mail_entries SET status = ?, claim_phase = ?, claim_step = ?, claim_attempt = NULL,
                     claimed_at = ?, updated_at = ?
                 WHERE id = ? AND owner_uuid = ? AND status = 'CLAIM_REVIEW'
                   AND claim_step = ? AND claim_phase = ? AND claim_attempt = ?
                 """;

    private static boolean updateClaim(PreparedStatement statement, UUID owner, long mailId, ClaimProgress expected,
                                       int nextStep, boolean finished, Instant now) throws SQLException {
        statement.setString(1, finished ? MailStatus.CLAIMED.name() : MailStatus.CLAIM_PENDING.name());
        statement.setString(2, finished ? ClaimPhase.NONE.name() : ClaimPhase.READY.name());
        statement.setInt(3, nextStep);
        if (finished) statement.setLong(4, now.toEpochMilli());
        else statement.setNull(4, java.sql.Types.BIGINT);
        statement.setLong(5, now.toEpochMilli());
        statement.setLong(6, mailId);
        statement.setString(7, owner.toString());
        statement.setInt(8, expected.step());
        statement.setString(9, expected.phase().name());
        statement.setString(10, expected.attempt().toString());
        return statement.executeUpdate() == 1;
    }

    @Override
    public Optional<MailMessage> loadMail(UUID ownerUuid, long mailId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM mail_entries WHERE owner_uuid = ? AND id = ? LIMIT 1"
             )) {
            statement.setString(1, ownerUuid.toString());
            statement.setLong(2, mailId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMail(connection, resultSet));
            }
        }
    }

    @Override
    public List<MailMessage> loadInbox(UUID ownerUuid) throws SQLException {
        List<MailMessage> messages = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM mail_entries WHERE owner_uuid = ? AND status <> 'DELETED' ORDER BY created_at DESC, id DESC"
             )) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(readMail(resultSet, List.of()));
                }
            }
            return hydrateAttachments(connection, messages, null);
        }
    }

    @Override
    public Optional<ClaimMail> loadClaimMail(UUID ownerUuid, long mailId) throws SQLException {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM mail_entries WHERE owner_uuid=? AND id=?")) {
            statement.setString(1, ownerUuid.toString());
            statement.setLong(2, mailId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new ClaimMail(readMail(connection, rows), readClaimProgress(rows)));
            }
        }
    }

    @Override
    public List<ClaimMail> loadClaimablePage(UUID ownerUuid, long beforeId, int limit, Instant now) throws SQLException {
        String sql = "SELECT * FROM mail_entries WHERE owner_uuid=? AND id<? AND " + claimableSql()
            + " AND (claim_phase='READY' OR expires_at IS NULL OR expires_at>?)"
            + " AND (claim_commands<>'' OR EXISTS (SELECT 1 FROM mail_attachments WHERE mail_id=mail_entries.id))"
            + " ORDER BY id DESC LIMIT ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.setLong(2, beforeId);
            statement.setLong(3, now.toEpochMilli());
            statement.setInt(4, limit);
            List<ClaimMail> mails = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    mails.add(new ClaimMail(readMail(rows, List.of()), readClaimProgress(rows)));
                }
            }
            if (mails.isEmpty()) return List.of();
            Map<Long, List<MailAttachment>> attachments = loadAttachments(connection,
                mails.stream().map(entry -> entry.mail().id()).toList(), null);
            return mails.stream().map(entry -> new ClaimMail(
                entry.mail().withAttachments(attachments.getOrDefault(entry.mail().id(), List.of())), entry.progress())).toList();
        }
    }

    private static ClaimProgress readClaimProgress(ResultSet rows) throws SQLException {
        return new ClaimProgress(rows.getInt("claim_step"), ClaimPhase.valueOf(rows.getString("claim_phase")),
            parseUuid(rows.getString("claim_attempt")));
    }

    private List<MailMessage> hydrateAttachments(Connection connection, List<MailMessage> mails, Long previewMailId) throws SQLException {
        if (mails.isEmpty()) return List.of();
        var attachments = loadAttachments(connection, mails.stream().map(MailMessage::id).toList(), previewMailId);
        return mails.stream().map(mail -> mail.withAttachments(attachments.getOrDefault(mail.id(), List.of()))).toList();
    }

    private Map<Long, List<MailAttachment>> loadAttachments(Connection connection, List<Long> ids, Long previewMailId) throws SQLException {
        String columns = previewMailId == null
            ? "item_data, icon" : "CASE WHEN mail_id = ? THEN item_data END AS item_data, CASE WHEN mail_id = ? THEN icon END AS icon";
        Map<Long, List<MailAttachment>> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += ATTACHMENT_READ_BATCH) {
            int end = Math.min(ids.size(), start + ATTACHMENT_READ_BATCH);
            String markers = String.join(",", java.util.Collections.nCopies(end - start, "?"));
            String sql = "SELECT mail_id, id, sort_order, attachment_type, vault_amount, currency_id, currency_amount, description, "
                + columns + " FROM mail_attachments WHERE mail_id IN (" + markers + ") ORDER BY mail_id, sort_order, id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameter = 1;
                if (previewMailId != null) {
                    statement.setLong(parameter++, previewMailId);
                    statement.setLong(parameter++, previewMailId);
                }
                for (int i = start; i < end; i++) statement.setLong(parameter++, ids.get(i));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.computeIfAbsent(rows.getLong("mail_id"), ignored -> new ArrayList<>()).add(readAttachment(rows));
                    }
                }
            }
        }
        result.replaceAll((id, values) -> List.copyOf(values));
        return result;
    }

    @Override
    public MailPage<MailMessage> loadInboxPage(UUID ownerUuid, MailInboxQuery query) throws SQLException {
        try (Connection connection = getConnection()) {
            return loadInboxPage(connection, ownerUuid, query, countInbox(connection, ownerUuid, query.filter()), null);
        }
    }

    @Override
    public InboxView loadInboxView(UUID ownerUuid, MailInboxQuery query, long selectedMailId) throws SQLException {
        try (Connection connection = getConnection()) {
            var counts = loadInboxCounts(connection, ownerUuid, query.filter());
            var page = loadInboxPage(connection, ownerUuid, query, counts.filteredCount(), selectedMailId);
            return new InboxView(page, counts.stats(), selectedMailId(page.entries(), selectedMailId));
        }
    }

    private MailPage<MailMessage> loadInboxPage(Connection connection, UUID ownerUuid, MailInboxQuery query,
                                                int totalItems, Long previewMailId) throws SQLException {
        if (totalItems <= 0) {
            return MailPage.empty(query.page(), query.pageSize());
        }
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) query.pageSize()));
        int page = Math.min(Math.max(1, query.page()), totalPages);
        int offset = (page - 1) * query.pageSize();
        String condition = filterCondition(query.filter());
        List<MailMessage> messages = new ArrayList<>();
        String columns = previewMailId == null ? "*" : INBOX_VIEW_COLUMNS;
        String sql = "SELECT " + columns + " FROM mail_entries WHERE owner_uuid = ?" + condition
            + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.setInt(2, query.pageSize());
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(readMail(resultSet, List.of(), previewMailId == null));
                }
            }
        }
        Long selected = previewMailId == null ? null : selectedMailId(messages, previewMailId);
        return new MailPage<>(hydrateAttachments(connection, messages, selected), page, query.pageSize(), totalItems, totalPages);
    }

    private static long selectedMailId(List<MailMessage> messages, long requested) {
        for (MailMessage message : messages) if (message.id() == requested) return requested;
        return messages.isEmpty() ? 0 : messages.get(0).id();
    }

    @Override
    public int countInbox(UUID ownerUuid, MailInboxFilter filter) throws SQLException {
        try (Connection connection = getConnection()) { return countInbox(connection, ownerUuid, filter); }
    }

    private int countInbox(Connection connection, UUID ownerUuid, MailInboxFilter filter) throws SQLException {
        String sql = "SELECT COUNT(*) AS total_count FROM mail_entries WHERE owner_uuid = ?" + filterCondition(filter);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("total_count") : 0;
            }
        }
    }

    @Override
    public MailMailboxStats loadStats(UUID ownerUuid) throws SQLException {
        try (Connection connection = getConnection()) { return loadInboxCounts(connection, ownerUuid, MailInboxFilter.ALL).stats(); }
    }

    private record InboxCounts(MailMailboxStats stats, int filteredCount) {}

    private InboxCounts loadInboxCounts(Connection connection, UUID ownerUuid, MailInboxFilter filter) throws SQLException {
        String source = switch (filter) {
            case SYSTEM, PLAYER, PRESET, CDK -> filter.name();
            default -> null;
        };
        int total = 0;
        int unread = 0;
        int claimable = 0;
        int sourceCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) AS total_count, "
                     + "SUM(CASE WHEN status = 'UNREAD' THEN 1 ELSE 0 END) AS unread_count, "
                      + "SUM(CASE WHEN " + claimableSql() + " "
                     + "AND (claim_commands <> '' OR EXISTS (SELECT 1 FROM mail_attachments a WHERE a.mail_id = mail_entries.id)) "
                     + "THEN 1 ELSE 0 END) AS claimable_count"
                     + (source == null ? "" : ", SUM(CASE WHEN source_type = ? THEN 1 ELSE 0 END) AS source_count")
                     + " FROM mail_entries WHERE owner_uuid = ? AND status <> 'DELETED'"
             )) {
            int parameter = 1;
            if (source != null) statement.setString(parameter++, source);
            statement.setString(parameter, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    total = resultSet.getInt("total_count");
                    unread = resultSet.getInt("unread_count");
                    claimable = resultSet.getInt("claimable_count");
                    if (source != null) sourceCount = resultSet.getInt("source_count");
                }
            }
        }
        int filtered = switch (filter) {
            case ALL -> total;
            case UNREAD -> unread;
            case CLAIMABLE -> claimable;
            default -> sourceCount;
        };
        return new InboxCounts(new MailMailboxStats(total, unread, claimable), filtered);
    }

    @Override
    public void appendLog(MailLogEntry entry) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(true);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_LOG)) {
                bindLog(insert, entry);
                insert.executeUpdate();
            }
            // Commit the new audit entry before pruning. Concurrent appenders must not hold
            // their inserted-row locks while deleting older rows for the same player.
            try { pruneLog(connection,entry.playerUuid()); }
            catch (SQLException failure) { mailLogger.warning("邮件日志已保存，历史裁剪暂未完成: " + failure.getMessage()); }
        }
    }

    private static void bindLog(PreparedStatement insert, MailLogEntry entry) throws SQLException {
        insert.setString(1, entry.playerUuid().toString());
        insert.setString(2, nullToEmpty(entry.type()));
        insert.setString(3, nullToEmpty(entry.content()));
        insert.setLong(4, epochMilli(entry.createdAt()));
    }

    @Override
    public MailPage<MailLogEntry> loadLogPage(UUID playerUuid, int page, int pageSize) throws SQLException {
        int normalizedPageSize = Math.max(1, pageSize);
        int totalItems;
        try (Connection connection = getConnection();
             PreparedStatement countStatement = connection.prepareStatement(
                 "SELECT COUNT(*) AS total_count FROM mail_logs WHERE player_uuid = ?"
             )) {
            countStatement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = countStatement.executeQuery()) {
                totalItems = resultSet.next() ? resultSet.getInt("total_count") : 0;
            }
        }
        if (totalItems <= 0) {
            return MailPage.empty(page, normalizedPageSize);
        }
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) normalizedPageSize));
        int normalizedPage = Math.min(Math.max(1, page), totalPages);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        List<MailLogEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, player_uuid, log_type, content, created_at FROM mail_logs WHERE player_uuid = ? "
                     + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, normalizedPageSize);
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(
                        new MailLogEntry(
                            resultSet.getLong("id"),
                            UUID.fromString(resultSet.getString("player_uuid")),
                            nullToEmpty(resultSet.getString("log_type")),
                            nullToEmpty(resultSet.getString("content")),
                            readInstant(resultSet, "created_at")
                        )
                    );
                }
            }
        }
        return new MailPage<>(List.copyOf(entries), normalizedPage, normalizedPageSize, totalItems, totalPages);
    }

    @Override
    public int cleanupMail(Instant now, Instant claimedBefore, Instant deletedBefore) throws SQLException {
        int cleaned = 0;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement expireStatement = connection.prepareStatement(
                    "UPDATE mail_entries SET status = 'EXPIRED', updated_at = ? "
                        + "WHERE status IN ('UNREAD', 'READ') AND claim_phase = 'NONE' AND expires_at IS NOT NULL AND expires_at <= ?"
                )) {
                    expireStatement.setLong(1, epochMilli(now));
                    expireStatement.setLong(2, epochMilli(now));
                    expireStatement.executeUpdate();
                }

                List<Long> deletableIds = new ArrayList<>();
                try (PreparedStatement collectStatement = connection.prepareStatement(
                    "SELECT id FROM mail_entries WHERE "
                        + "(status = 'CLAIMED' AND claimed_at IS NOT NULL AND claimed_at <= ?) "
                        + "OR (status = 'DELETED' AND deleted_at IS NOT NULL AND deleted_at <= ?) "
                        + "OR (status = 'EXPIRED' AND expires_at IS NOT NULL AND expires_at <= ?)"
                )) {
                    collectStatement.setLong(1, epochMilli(claimedBefore));
                    collectStatement.setLong(2, epochMilli(deletedBefore));
                    collectStatement.setLong(3, epochMilli(now));
                    try (ResultSet resultSet = collectStatement.executeQuery()) {
                        while (resultSet.next()) {
                            deletableIds.add(resultSet.getLong("id"));
                        }
                    }
                }

                if (!deletableIds.isEmpty()) {
                    try (PreparedStatement deleteAttachments = connection.prepareStatement("DELETE FROM mail_attachments WHERE mail_id = ?");
                         PreparedStatement deleteEntries = connection.prepareStatement("DELETE FROM mail_entries WHERE id = ?")) {
                        for (Long id : deletableIds) {
                            deleteAttachments.setLong(1, id);
                            deleteAttachments.addBatch();
                            deleteEntries.setLong(1, id);
                            deleteEntries.addBatch();
                        }
                        deleteAttachments.executeBatch();
                        int[] deleted = deleteEntries.executeBatch();
                        for (int count : deleted) {
                            if (count > 0) {
                                cleaned += count;
                            }
                        }
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
        return cleaned;
    }

    @Override
    public boolean createCdk(MailCdkDefinition definition) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(CDK_INSERT)) {
            bindCdk(statement, definition);
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            if (duplicateCdk(failure)) return false;
            throw failure;
        }
    }

    @Override
    public void syncPresetCdks(List<PresetCdk> definitions) throws SQLException {
        if (definitions.isEmpty()) return;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(cdkUpsertSql())) {
                for (var entry : definitions) {
                    bindCdk(statement, entry.definition());
                    statement.setBoolean(10, entry.relativeExpiry());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    private void bindCdk(PreparedStatement statement, MailCdkDefinition definition) throws SQLException {
        statement.setString(1, definition.code());
        statement.setString(2, definition.presetId());
        statement.setInt(3, definition.maxClaims());
        statement.setInt(4, definition.claimedCount());
        if (definition.expiresAt() == null) statement.setNull(5, Types.BIGINT);
        else statement.setLong(5, definition.expiresAt().toEpochMilli());
        statement.setBoolean(6, definition.enabled());
        statement.setString(7, nullToEmpty(definition.createdBy()));
        statement.setLong(8, epochMilli(definition.createdAt()));
        statement.setLong(9, epochMilli(definition.updatedAt()));
    }

    private boolean duplicateCdk(SQLException failure) {
        int code = failure.getErrorCode();
        return isMysql() ? code == 1062 : code == 19 || code == 1555 || code == 2067;
    }

    @Override
    public Optional<MailCdkDefinition> loadCdk(String code) throws SQLException {
        try (Connection connection = getConnection()) {
            return loadCdk(connection, code);
        }
    }

    @Override
    public List<MailCdkDefinition> loadCdks(int page, int pageSize) throws SQLException {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT code, preset_id, max_claims, claimed_count, expires_at, enabled, created_by, created_at, updated_at "
                     + "FROM mail_cdk_definitions "
                     + "ORDER BY enabled DESC, CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END ASC, expires_at ASC, code ASC "
                     + "LIMIT ? OFFSET ?"
             )) {
            statement.setInt(1, safePageSize);
            statement.setInt(2, (safePage - 1) * safePageSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MailCdkDefinition> definitions = new ArrayList<>();
                while (resultSet.next()) {
                    definitions.add(readCdk(resultSet));
                }
                return List.copyOf(definitions);
            }
        }
    }

    @Override
    public boolean deleteCdk(String code) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteClaims = connection.prepareStatement("DELETE FROM mail_cdk_claims WHERE code = ?");
                 PreparedStatement deleteDefinition = connection.prepareStatement("DELETE FROM mail_cdk_definitions WHERE code = ?")) {
                deleteClaims.setString(1, nullToEmpty(code));
                deleteClaims.executeUpdate();
                deleteDefinition.setString(1, nullToEmpty(code));
                boolean deleted = deleteDefinition.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public CdkRedemption redeemCdk(String code, UUID playerUuid, Instant now,
                                   java.util.function.Function<String, MailMessage> mailForPreset) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Write first so SQLite obtains the writer lock before any reads. MySQL holds the CDK row
                // through mail insertion, keeping a concurrent preset change out of this redemption.
                boolean reserved;
                try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE mail_cdk_definitions SET claimed_count = claimed_count + 1, updated_at = ?
                    WHERE code = ? AND enabled = ? AND (expires_at IS NULL OR expires_at > ?) AND claimed_count < max_claims
                    """)) {
                    update.setLong(1, epochMilli(now));
                    update.setString(2, code);
                    update.setBoolean(3, true);
                    update.setLong(4, epochMilli(now));
                    reserved = update.executeUpdate() == 1;
                }
                var definition = loadCdk(connection, code).orElse(null);
                if (!reserved) {
                    connection.rollback();
                    return new CdkRedemption(null, cdkRejection(definition, now));
                }
                if (definition == null) throw new SQLException("Missing reserved CDK");
                MailMessage mail = mailForPreset.apply(definition.presetId());
                if (mail == null) {
                    connection.rollback();
                    return new CdkRedemption(null, "player.cdk-preset-unavailable");
                }
                if (!playerUuid.equals(mail.ownerUuid()) || !code.equals(mail.cdkCode())
                    || !definition.presetId().equals(mail.presetId()) || mail.sourceType() != MailSourceType.CDK) {
                    throw new SQLException("CDK mail does not match the reserved redemption");
                }

                try (PreparedStatement claim = connection.prepareStatement(
                    "INSERT INTO mail_cdk_claims (code, player_uuid, claimed_at) VALUES (?, ?, ?)"
                )) {
                    claim.setString(1, code);
                    claim.setString(2, playerUuid.toString());
                    claim.setLong(3, epochMilli(now));
                    claim.executeUpdate();
                } catch (SQLException failure) {
                    if (!duplicateCdk(failure)) throw failure;
                    connection.rollback();
                    return new CdkRedemption(null, "player.cdk-already-redeemed");
                }

                insertMailAndAttachments(connection, mail);
                connection.commit();
                return new CdkRedemption(mail, null);
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    private String cdkRejection(MailCdkDefinition definition, Instant now) {
        if (definition == null) return "player.cdk-not-found";
        if (!definition.enabled()) return "player.cdk-disabled";
        if (definition.expired(now)) return "player.cdk-expired";
        if (definition.claimedCount() >= definition.maxClaims()) return "player.cdk-exhausted";
        return "player.cdk-claim-failed";
    }

    @Override
    public int cleanupCdks(Instant now) throws SQLException {
        int deleted;
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteClaims = connection.prepareStatement(
                    "DELETE FROM mail_cdk_claims WHERE code IN (SELECT code FROM mail_cdk_definitions WHERE expires_at IS NOT NULL AND expires_at <= ?)"
                )) {
                    deleteClaims.setLong(1, epochMilli(now));
                    deleteClaims.executeUpdate();
                }
                try (PreparedStatement deleteDefinitions = connection.prepareStatement(
                    "DELETE FROM mail_cdk_definitions WHERE expires_at IS NOT NULL AND expires_at <= ?"
                )) {
                    deleteDefinitions.setLong(1, epochMilli(now));
                    deleted = deleteDefinitions.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return deleted;
    }

    @Override
    public void close() {
        shutdown();
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS mail_delivery_receipts (
                    player_uuid VARCHAR(36) NOT NULL,
                    operation_key CHAR(64) NOT NULL,
                    mail_id BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, operation_key)
                )
                """ + (isMysql() ? " ENGINE=InnoDB" : ""));
            if (!isMysql()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_player_profiles (
                        player_uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        last_known_name_lower TEXT NOT NULL,
                        last_send_at INTEGER NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        last_server TEXT NOT NULL
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_profiles_name ON mail_player_profiles(last_known_name_lower);");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        sender_uuid TEXT NULL,
                        sender_name TEXT NOT NULL,
                        source_type TEXT NOT NULL,
                        preset_id TEXT NOT NULL,
                        cdk_code TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        body TEXT NOT NULL,
                        status TEXT NOT NULL,
                        claim_step INTEGER NOT NULL DEFAULT 0,
                        claim_phase VARCHAR(16) NOT NULL DEFAULT 'NONE',
                        claim_attempt VARCHAR(36) NULL,
                        claim_commands TEXT NOT NULL,
                        claim_conditions TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NULL,
                        updated_at INTEGER NOT NULL,
                        claimed_at INTEGER NULL,
                        deleted_at INTEGER NULL
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_entries_owner ON mail_entries(owner_uuid, created_at DESC);");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_entries_status ON mail_entries(owner_uuid, status, created_at DESC);");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mail_id INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL,
                        attachment_type TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        vault_amount DOUBLE NOT NULL,
                        currency_id TEXT NULL,
                        currency_amount DOUBLE NOT NULL DEFAULT 0,
                        description TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(mail_id) REFERENCES mail_entries(id) ON DELETE CASCADE
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_attachments_mail_id ON mail_attachments(mail_id, sort_order);");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        log_type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    );
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_mail_logs_player ON mail_logs(player_uuid, created_at DESC);");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_cdk_definitions (
                        code TEXT PRIMARY KEY,
                        preset_id TEXT NOT NULL,
                        max_claims INTEGER NOT NULL,
                        claimed_count INTEGER NOT NULL,
                        expires_at INTEGER NULL,
                        enabled INTEGER NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    );
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_cdk_claims (
                        code TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY (code, player_uuid),
                        FOREIGN KEY(code) REFERENCES mail_cdk_definitions(code) ON DELETE CASCADE
                    );
                    """);
            } else {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_player_profiles (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        last_known_name VARCHAR(64) NOT NULL,
                        last_known_name_lower VARCHAR(64) NOT NULL,
                        last_send_at BIGINT NOT NULL,
                        last_seen_at BIGINT NOT NULL,
                        last_server VARCHAR(128) NOT NULL,
                        INDEX idx_mail_profiles_name (last_known_name_lower)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_entries (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        owner_uuid VARCHAR(36) NOT NULL,
                        sender_uuid VARCHAR(36) NULL,
                        sender_name VARCHAR(64) NOT NULL,
                        source_type VARCHAR(32) NOT NULL,
                        preset_id VARCHAR(128) NOT NULL,
                        cdk_code VARCHAR(128) NOT NULL,
                        subject TEXT NOT NULL,
                        body TEXT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        claim_step INTEGER NOT NULL DEFAULT 0,
                        claim_phase VARCHAR(16) NOT NULL DEFAULT 'NONE',
                        claim_attempt VARCHAR(36) NULL,
                        claim_commands TEXT NOT NULL,
                        claim_conditions TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NULL,
                        updated_at BIGINT NOT NULL,
                        claimed_at BIGINT NULL,
                        deleted_at BIGINT NULL,
                        INDEX idx_mail_entries_owner (owner_uuid, created_at),
                        INDEX idx_mail_entries_status (owner_uuid, status, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_attachments (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        mail_id BIGINT NOT NULL,
                        sort_order INT NOT NULL,
                        attachment_type VARCHAR(16) NOT NULL,
                        item_data LONGTEXT NOT NULL,
                        vault_amount DOUBLE NOT NULL,
                        currency_id VARCHAR(64) NULL,
                        currency_amount DOUBLE NOT NULL DEFAULT 0,
                        description TEXT NOT NULL,
                        icon VARCHAR(255) NOT NULL DEFAULT '',
                        INDEX idx_mail_attachments_mail_id (mail_id, sort_order),
                        CONSTRAINT fk_mail_attachments_mail_id FOREIGN KEY(mail_id) REFERENCES mail_entries(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        player_uuid VARCHAR(36) NOT NULL,
                        log_type VARCHAR(32) NOT NULL,
                        content TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        INDEX idx_mail_logs_player (player_uuid, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_cdk_definitions (
                        code VARCHAR(128) PRIMARY KEY,
                        preset_id VARCHAR(128) NOT NULL,
                        max_claims INT NOT NULL,
                        claimed_count INT NOT NULL,
                        expires_at BIGINT NULL,
                        enabled BOOLEAN NOT NULL,
                        created_by VARCHAR(64) NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_cdk_claims (
                        code VARCHAR(128) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (code, player_uuid),
                        CONSTRAINT fk_mail_cdk_claims_code FOREIGN KEY(code) REFERENCES mail_cdk_definitions(code) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            }
        }
    }

    private Optional<MailCdkDefinition> loadCdk(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT code, preset_id, max_claims, claimed_count, expires_at, enabled, created_by, created_at, updated_at "
                + "FROM mail_cdk_definitions WHERE code = ? LIMIT 1"
        )) {
            statement.setString(1, nullToEmpty(code));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readCdk(resultSet));
            }
        }
    }

    private MailPlayerProfile readProfile(ResultSet resultSet) throws SQLException {
        return new MailPlayerProfile(
            UUID.fromString(resultSet.getString("player_uuid")),
            nullToEmpty(resultSet.getString("last_known_name")),
            readInstant(resultSet, "last_send_at"),
            readInstant(resultSet, "last_seen_at"),
            nullToEmpty(resultSet.getString("last_server"))
        );
    }

    private MailCdkDefinition readCdk(ResultSet resultSet) throws SQLException {
        return new MailCdkDefinition(
            resultSet.getString("code"),
            nullToEmpty(resultSet.getString("preset_id")),
            resultSet.getInt("max_claims"),
            resultSet.getInt("claimed_count"),
            readNullableInstant(resultSet, "expires_at"),
            resultSet.getBoolean("enabled"),
            nullToEmpty(resultSet.getString("created_by")),
            readInstant(resultSet, "created_at"),
            readInstant(resultSet, "updated_at")
        );
    }

    private MailMessage readMail(Connection connection, ResultSet resultSet) throws SQLException {
        return readMail(resultSet, loadAttachments(connection, resultSet.getLong("id")));
    }

    private MailMessage readMail(ResultSet resultSet, List<MailAttachment> attachments) throws SQLException {
        return readMail(resultSet, attachments, true);
    }

    private MailMessage readMail(ResultSet resultSet, List<MailAttachment> attachments, boolean includeClaimRules) throws SQLException {
        return new MailMessage(
            resultSet.getLong("id"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            parseUuid(resultSet.getString("sender_uuid")),
            nullToEmpty(resultSet.getString("sender_name")),
            MailSourceType.parse(resultSet.getString("source_type")),
            nullToEmpty(resultSet.getString("preset_id")),
            nullToEmpty(resultSet.getString("cdk_code")),
            nullToEmpty(resultSet.getString("subject")),
            nullToEmpty(resultSet.getString("body")),
            MailStatus.parse(resultSet.getString("status")),
            attachments,
            includeClaimRules ? deserializeLines(resultSet.getString("claim_commands")) : List.of(),
            includeClaimRules ? deserializeConditions(resultSet.getString("claim_conditions")) : List.of(),
            readInstant(resultSet, "created_at"),
            readNullableInstant(resultSet, "expires_at"),
            readInstant(resultSet, "updated_at"),
            readNullableInstant(resultSet, "claimed_at"),
            readNullableInstant(resultSet, "deleted_at")
        );
    }

    private List<MailAttachment> loadAttachments(Connection connection, long mailId) throws SQLException {
        List<MailAttachment> attachments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id, sort_order, attachment_type, item_data, vault_amount, currency_id, currency_amount, description, icon "
                + "FROM mail_attachments WHERE mail_id = ? ORDER BY sort_order ASC, id ASC"
        )) {
            statement.setLong(1, mailId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    attachments.add(readAttachment(resultSet));
                }
            }
        }
        return List.copyOf(attachments);
    }

    private MailAttachment readAttachment(ResultSet rows) throws SQLException {
        MailAttachmentType type = MailAttachmentType.parse(rows.getString("attachment_type"));
        String currency = nullToEmpty(rows.getString("currency_id"));
        double amount = rows.getDouble("currency_amount");
        if ((type == MailAttachmentType.CURRENCY || type == MailAttachmentType.VAULT) && amount <= 0.0D) {
            amount = rows.getDouble("vault_amount");
        }
        if (type == MailAttachmentType.VAULT) {
            type = MailAttachmentType.CURRENCY;
            if (currency.isBlank()) currency = "money";
        }
        return new MailAttachment(rows.getLong("id"), rows.getInt("sort_order"), type,
            nullToEmpty(rows.getString("item_data")), currency, amount, nullToEmpty(rows.getString("description")),
            xuanmo.arcartxsuite.mail.util.MailIconCodec.fromYamlString(rows.getString("icon")));
    }

    private void writeMail(PreparedStatement statement, MailMessage message) throws SQLException {
        statement.setString(1, message.ownerUuid().toString());
        setNullableString(statement, 2, message.senderUuid() == null ? null : message.senderUuid().toString());
        statement.setString(3, nullToEmpty(message.senderName()));
        statement.setString(4, message.sourceType().name());
        statement.setString(5, nullToEmpty(message.presetId()));
        statement.setString(6, nullToEmpty(message.cdkCode()));
        statement.setString(7, nullToEmpty(message.subject()));
        statement.setString(8, nullToEmpty(message.body()));
        statement.setString(9, message.status().name());
        statement.setString(10, serializeLines(message.claimCommands()));
        statement.setString(11, serializeConditions(message.claimConditions()));
        statement.setLong(12, epochMilli(message.createdAt()));
        if (message.expiresAt() == null) {
            statement.setNull(13, Types.BIGINT);
        } else {
            statement.setLong(13, message.expiresAt().toEpochMilli());
        }
        statement.setLong(14, epochMilli(message.updatedAt()));
        if (message.claimedAt() == null) {
            statement.setNull(15, Types.BIGINT);
        } else {
            statement.setLong(15, message.claimedAt().toEpochMilli());
        }
        if (message.deletedAt() == null) {
            statement.setNull(16, Types.BIGINT);
        } else {
            statement.setLong(16, message.deletedAt().toEpochMilli());
        }
    }

    private String playerProfileUpsertSql() {
        if (!isMysql()) {
            return """
                INSERT INTO mail_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_send_at, last_seen_at, last_server)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    last_known_name = CASE WHEN excluded.last_seen_at >= last_seen_at THEN excluded.last_known_name ELSE last_known_name END,
                    last_known_name_lower = CASE WHEN excluded.last_seen_at >= last_seen_at THEN excluded.last_known_name_lower ELSE last_known_name_lower END,
                    last_server = CASE WHEN excluded.last_seen_at >= last_seen_at THEN excluded.last_server ELSE last_server END,
                    last_send_at = MAX(last_send_at, excluded.last_send_at),
                    last_seen_at = MAX(last_seen_at, excluded.last_seen_at)
                """;
        }
        return """
            INSERT INTO mail_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_send_at, last_seen_at, last_server)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name = IF(VALUES(last_seen_at) >= last_seen_at, VALUES(last_known_name), last_known_name),
                last_known_name_lower = IF(VALUES(last_seen_at) >= last_seen_at, VALUES(last_known_name_lower), last_known_name_lower),
                last_server = IF(VALUES(last_seen_at) >= last_seen_at, VALUES(last_server), last_server),
                last_send_at = GREATEST(last_send_at, VALUES(last_send_at)),
                last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at))
            """;
    }

    private String mailInsertSql() {
        return """
            INSERT INTO mail_entries (
                owner_uuid, sender_uuid, sender_name, source_type, preset_id, cdk_code,
                subject, body, status, claim_commands, claim_conditions,
                created_at, expires_at, updated_at, claimed_at, deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }

    private String attachmentInsertSql() {
        return """
            INSERT INTO mail_attachments (mail_id, sort_order, attachment_type, item_data, vault_amount, currency_id, currency_amount, description, icon)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }

    private static final String CDK_INSERT = """
        INSERT INTO mail_cdk_definitions (code, preset_id, max_claims, claimed_count, expires_at, enabled, created_by, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private String cdkUpsertSql() {
        if (!isMysql()) {
            return CDK_INSERT + """
                ON CONFLICT(code) DO UPDATE SET
                    preset_id = excluded.preset_id,
                    max_claims = excluded.max_claims,
                    expires_at = CASE WHEN ? THEN mail_cdk_definitions.created_at + (excluded.expires_at - excluded.created_at)
                        ELSE excluded.expires_at END,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """;
        }
        return CDK_INSERT + """
            ON DUPLICATE KEY UPDATE
                preset_id = VALUES(preset_id),
                max_claims = VALUES(max_claims),
                expires_at = CASE WHEN ? THEN created_at + (VALUES(expires_at) - VALUES(created_at))
                    ELSE VALUES(expires_at) END,
                enabled = VALUES(enabled),
                updated_at = VALUES(updated_at)
            """;
    }

    private void pruneLog(Connection connection,UUID player) throws SQLException {
        long cutoffTime,cutoffId;
        try (PreparedStatement query=connection.prepareStatement(
                "SELECT created_at,id FROM mail_logs WHERE player_uuid=? ORDER BY created_at DESC,id DESC LIMIT 1 OFFSET 99")) {
            query.setString(1,player.toString());
            try (ResultSet rows=query.executeQuery()) {
                if (!rows.next()) return;
                cutoffTime=rows.getLong(1);cutoffId=rows.getLong(2);
            }
        }
        try (PreparedStatement cleanup=connection.prepareStatement(
                "DELETE FROM mail_logs WHERE player_uuid=? AND (created_at<? OR (created_at=? AND id<?))")) {
            cleanup.setString(1,player.toString());cleanup.setLong(2,cutoffTime);cleanup.setLong(3,cutoffTime);cleanup.setLong(4,cutoffId);
            cleanup.executeUpdate();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static long epochMilli(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private static Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        return Instant.ofEpochMilli(resultSet.getLong(column));
    }

    private static Instant readNullableInstant(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static UUID parseUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return UUID.fromString(rawValue);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static MailAttachmentType persistedAttachmentType(MailAttachment attachment) {
        return attachment.isCurrency() ? MailAttachmentType.CURRENCY : attachment.type();
    }

    private static String filterCondition(MailInboxFilter filter) {
        MailInboxFilter normalized = filter == null ? MailInboxFilter.ALL : filter;
        String activeOnly = " AND status <> 'DELETED'";
        return activeOnly + switch (normalized) {
            case UNREAD -> " AND status = 'UNREAD'";
            case CLAIMABLE -> " AND " + claimableSql() + " AND "
                + "(claim_commands <> '' OR EXISTS (SELECT 1 FROM mail_attachments a WHERE a.mail_id = mail_entries.id))";
            case SYSTEM -> " AND source_type = 'SYSTEM'";
            case PLAYER -> " AND source_type = 'PLAYER'";
            case PRESET -> " AND source_type = 'PRESET'";
            case CDK -> " AND source_type = 'CDK'";
            case ALL -> "";
        };
    }

    private static String claimableSql() {
        return "((status IN ('UNREAD', 'READ') AND claim_phase = 'NONE') "
            + "OR (status = 'CLAIM_PENDING' AND claim_phase = 'READY'))";
    }

    private static String serializeLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values);
    }

    private static List<String> deserializeLines(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return List.of(rawValue.split("\n"));
    }

    private static String serializeConditions(List<ScriptCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (ScriptCondition condition : conditions) {
            if (condition != null) {
                values.add(condition.serialize());
            }
        }
        return String.join("\n", values);
    }

    private static List<ScriptCondition> deserializeConditions(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        List<ScriptCondition> conditions = new ArrayList<>();
        for (String line : rawValue.split("\n")) {
            ScriptCondition condition = ScriptCondition.deserialize(line);
            if (condition != null) {
                conditions.add(condition);
            }
        }
        return List.copyOf(conditions);
    }

}
