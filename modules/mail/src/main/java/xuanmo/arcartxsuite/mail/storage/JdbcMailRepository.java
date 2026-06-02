package xuanmo.arcartxsuite.mail.storage;

import java.io.File;
import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.mail.config.MailPersistenceDialect;
import xuanmo.arcartxsuite.mail.config.MailStorageConfiguration;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailCondition;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailPlayerProfile;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;

public final class JdbcMailRepository extends AbstractModuleRepository implements MailRepository {

    private final MailStorageConfiguration configuration;

    public JdbcMailRepository(File dataFolder, MailStorageConfiguration configuration, Logger logger) {
        super("AXS-Mail", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        createTables(conn);
        ensureSchemaUpgrade(conn);
    }

    @Override
    protected List<String> playerDataTables() {
        return List.of("mail_player_profiles", "mail_entries", "mail_logs", "mail_cdk_claims");
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
            "mail_cdk_claims"
        );
    }

    @Override
    public void upsertPlayerProfile(MailPlayerProfile profile) throws SQLException {
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(mailInsertSql(), Statement.RETURN_GENERATED_KEYS)) {
                writeMail(statement, message, false);
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
                            attachmentStatement.addBatch();
                        }
                        attachmentStatement.executeBatch();
                    }
                }

                connection.commit();
                return new MailInsertResult(mailId);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void updateMailState(MailMessage message) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(mailUpdateSql())) {
            writeMail(statement, message, true);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<MailMessage> loadMail(UUID ownerUuid, long mailId) throws SQLException {
        try (Connection connection = connection();
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
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM mail_entries WHERE owner_uuid = ? AND status <> 'DELETED' ORDER BY created_at DESC, id DESC"
             )) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(readMail(connection, resultSet));
                }
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public MailPage<MailMessage> loadInboxPage(UUID ownerUuid, MailInboxQuery query) throws SQLException {
        int totalItems = countInbox(ownerUuid, query.filter());
        if (totalItems <= 0) {
            return MailPage.empty(query.page(), query.pageSize());
        }
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) query.pageSize()));
        int page = Math.min(Math.max(1, query.page()), totalPages);
        int offset = (page - 1) * query.pageSize();
        String condition = filterCondition(query.filter());
        List<MailMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM mail_entries WHERE owner_uuid = ?" + condition + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            statement.setInt(2, query.pageSize());
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(readMail(connection, resultSet));
                }
            }
        }
        return new MailPage<>(List.copyOf(messages), page, query.pageSize(), totalItems, totalPages);
    }

    @Override
    public int countInbox(UUID ownerUuid, MailInboxFilter filter) throws SQLException {
        String sql = "SELECT COUNT(*) AS total_count FROM mail_entries WHERE owner_uuid = ?" + filterCondition(filter);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("total_count") : 0;
            }
        }
    }

    @Override
    public MailMailboxStats loadStats(UUID ownerUuid) throws SQLException {
        int total = 0;
        int unread = 0;
        int claimable = 0;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) AS total_count, "
                     + "SUM(CASE WHEN status = 'UNREAD' THEN 1 ELSE 0 END) AS unread_count, "
                     + "SUM(CASE WHEN status IN ('UNREAD', 'READ') "
                     + "AND (claim_commands <> '' OR EXISTS (SELECT 1 FROM mail_attachments a WHERE a.mail_id = mail_entries.id)) "
                     + "THEN 1 ELSE 0 END) AS claimable_count "
                     + "FROM mail_entries WHERE owner_uuid = ? AND status <> 'DELETED'"
             )) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    total = resultSet.getInt("total_count");
                    unread = resultSet.getInt("unread_count");
                    claimable = resultSet.getInt("claimable_count");
                }
            }
        }
        return new MailMailboxStats(total, unread, claimable);
    }

    @Override
    public void appendLog(MailLogEntry entry) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mail_logs (player_uuid, log_type, content, created_at) VALUES (?, ?, ?, ?)"
            );
                 PreparedStatement cleanup = connection.prepareStatement(logCleanupSql())) {
                insert.setString(1, entry.playerUuid().toString());
                insert.setString(2, nullToEmpty(entry.type()));
                insert.setString(3, nullToEmpty(entry.content()));
                insert.setLong(4, epochMilli(entry.createdAt()));
                insert.executeUpdate();

                cleanup.setString(1, entry.playerUuid().toString());
                cleanup.setString(2, entry.playerUuid().toString());
                cleanup.executeUpdate();
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
    public MailPage<MailLogEntry> loadLogPage(UUID playerUuid, int page, int pageSize) throws SQLException {
        int normalizedPageSize = Math.max(1, pageSize);
        int totalItems;
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement expireStatement = connection.prepareStatement(
                    "UPDATE mail_entries SET status = 'EXPIRED', updated_at = ? "
                        + "WHERE status IN ('UNREAD', 'READ') AND expires_at IS NOT NULL AND expires_at <= ?"
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
    public void saveCdk(MailCdkDefinition definition) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(cdkUpsertSql())) {
            statement.setString(1, definition.code());
            statement.setString(2, definition.presetId());
            statement.setInt(3, definition.maxClaims());
            statement.setInt(4, definition.claimedCount());
            if (definition.expiresAt() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, definition.expiresAt().toEpochMilli());
            }
            statement.setBoolean(6, definition.enabled());
            statement.setString(7, nullToEmpty(definition.createdBy()));
            statement.setLong(8, epochMilli(definition.createdAt()));
            statement.setLong(9, epochMilli(definition.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<MailCdkDefinition> loadCdk(String code) throws SQLException {
        try (Connection connection = connection()) {
            return loadCdk(connection, code);
        }
    }

    @Override
    public List<MailCdkDefinition> loadCdks(int page, int pageSize) throws SQLException {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        try (Connection connection = connection();
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
        try (Connection connection = connection()) {
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
    public CdkClaimResult claimCdk(String code, UUID playerUuid, Instant now) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                Optional<MailCdkDefinition> optionalDefinition = loadCdk(connection, code);
                if (optionalDefinition.isEmpty()) {
                    connection.rollback();
                    return CdkClaimResult.failure("CDK 不存在。", null);
                }
                MailCdkDefinition definition = optionalDefinition.get();
                if (!definition.enabled()) {
                    connection.rollback();
                    return CdkClaimResult.failure("该 CDK 已禁用。", definition);
                }
                if (definition.expired(now)) {
                    connection.rollback();
                    return CdkClaimResult.failure("该 CDK 已过期。", definition);
                }
                if (definition.claimedCount() >= definition.maxClaims()) {
                    connection.rollback();
                    return CdkClaimResult.failure("该 CDK 已被领完。", definition);
                }

                try (PreparedStatement insertClaim = connection.prepareStatement(
                    "INSERT INTO mail_cdk_claims (code, player_uuid, claimed_at) VALUES (?, ?, ?)"
                )) {
                    insertClaim.setString(1, code);
                    insertClaim.setString(2, playerUuid.toString());
                    insertClaim.setLong(3, epochMilli(now));
                    insertClaim.executeUpdate();
                } catch (SQLException exception) {
                    connection.rollback();
                    return CdkClaimResult.failure("你已经兑换过这个 CDK。", definition);
                }

                try (PreparedStatement updateDefinition = connection.prepareStatement(
                    "UPDATE mail_cdk_definitions SET claimed_count = claimed_count + 1, updated_at = ? "
                        + "WHERE code = ? AND enabled = ? AND (expires_at IS NULL OR expires_at > ?) AND claimed_count < max_claims"
                )) {
                    updateDefinition.setLong(1, epochMilli(now));
                    updateDefinition.setString(2, code);
                    updateDefinition.setBoolean(3, true);
                    updateDefinition.setLong(4, epochMilli(now));
                    if (updateDefinition.executeUpdate() <= 0) {
                        connection.rollback();
                        return CdkClaimResult.failure("该 CDK 当前无法兑换。", definition);
                    }
                }

                connection.commit();
                return CdkClaimResult.success(
                    new MailCdkDefinition(
                        definition.code(),
                        definition.presetId(),
                        definition.maxClaims(),
                        definition.claimedCount() + 1,
                        definition.expiresAt(),
                        definition.enabled(),
                        definition.createdBy(),
                        definition.createdAt(),
                        now
                    )
                );
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public int cleanupCdks(Instant now) throws SQLException {
        int deleted;
        try (Connection connection = connection()) {
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
            if (configuration.dialect() == MailPersistenceDialect.SQLITE) {
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

    private void ensureSchemaUpgrade(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection, "mail_attachments", "currency_id")) {
                statement.execute(alterTableAddColumnSql("mail_attachments", "currency_id", configuration.dialect() == MailPersistenceDialect.SQLITE ? "TEXT NULL" : "VARCHAR(64) NULL"));
            }
            if (!columnExists(connection, "mail_attachments", "currency_amount")) {
                statement.execute(alterTableAddColumnSql("mail_attachments", "currency_amount", "DOUBLE NOT NULL DEFAULT 0"));
                statement.execute("UPDATE mail_attachments SET currency_amount = vault_amount WHERE attachment_type = 'VAULT'");
            }
            if (!tableExists(connection, "mail_logs")) {
                createTables(connection);
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
        long id = resultSet.getLong("id");
        return new MailMessage(
            id,
            UUID.fromString(resultSet.getString("owner_uuid")),
            parseUuid(resultSet.getString("sender_uuid")),
            nullToEmpty(resultSet.getString("sender_name")),
            MailSourceType.parse(resultSet.getString("source_type")),
            nullToEmpty(resultSet.getString("preset_id")),
            nullToEmpty(resultSet.getString("cdk_code")),
            nullToEmpty(resultSet.getString("subject")),
            nullToEmpty(resultSet.getString("body")),
            MailStatus.parse(resultSet.getString("status")),
            loadAttachments(connection, id),
            deserializeLines(resultSet.getString("claim_commands")),
            deserializeConditions(resultSet.getString("claim_conditions")),
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
            "SELECT id, sort_order, attachment_type, item_data, vault_amount, currency_id, currency_amount, description "
                + "FROM mail_attachments WHERE mail_id = ? ORDER BY sort_order ASC, id ASC"
        )) {
            statement.setLong(1, mailId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    MailAttachmentType attachmentType = MailAttachmentType.parse(resultSet.getString("attachment_type"));
                    String currencyId = nullToEmpty(resultSet.getString("currency_id"));
                    double amount = resultSet.getDouble("currency_amount");
                    if ((attachmentType == MailAttachmentType.CURRENCY || attachmentType == MailAttachmentType.VAULT) && amount <= 0.0D) {
                        amount = resultSet.getDouble("vault_amount");
                    }
                    if (attachmentType == MailAttachmentType.VAULT) {
                        attachmentType = MailAttachmentType.CURRENCY;
                        currencyId = currencyId.isBlank() ? "money" : currencyId;
                    }
                    attachments.add(
                        new MailAttachment(
                            resultSet.getLong("id"),
                            resultSet.getInt("sort_order"),
                            attachmentType,
                            nullToEmpty(resultSet.getString("item_data")),
                            currencyId,
                            amount,
                            nullToEmpty(resultSet.getString("description"))
                        )
                    );
                }
            }
        }
        return List.copyOf(attachments);
    }

    private void writeMail(PreparedStatement statement, MailMessage message, boolean update) throws SQLException {
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
        if (update) {
            statement.setLong(17, message.id());
            statement.setString(18, message.ownerUuid().toString());
        }
    }

    private String playerProfileUpsertSql() {
        if (configuration.dialect() == MailPersistenceDialect.SQLITE) {
            return """
                INSERT INTO mail_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_send_at, last_seen_at, last_server)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    last_known_name = excluded.last_known_name,
                    last_known_name_lower = excluded.last_known_name_lower,
                    last_send_at = excluded.last_send_at,
                    last_seen_at = excluded.last_seen_at,
                    last_server = excluded.last_server
                """;
        }
        return """
            INSERT INTO mail_player_profiles (player_uuid, last_known_name, last_known_name_lower, last_send_at, last_seen_at, last_server)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_known_name = VALUES(last_known_name),
                last_known_name_lower = VALUES(last_known_name_lower),
                last_send_at = VALUES(last_send_at),
                last_seen_at = VALUES(last_seen_at),
                last_server = VALUES(last_server)
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
            INSERT INTO mail_attachments (mail_id, sort_order, attachment_type, item_data, vault_amount, currency_id, currency_amount, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }

    private String mailUpdateSql() {
        return """
            UPDATE mail_entries SET
                owner_uuid = ?, sender_uuid = ?, sender_name = ?, source_type = ?, preset_id = ?, cdk_code = ?,
                subject = ?, body = ?, status = ?, claim_commands = ?, claim_conditions = ?,
                created_at = ?, expires_at = ?, updated_at = ?, claimed_at = ?, deleted_at = ?
            WHERE id = ? AND owner_uuid = ?
            """;
    }

    private String cdkUpsertSql() {
        if (configuration.dialect() == MailPersistenceDialect.SQLITE) {
            return """
                INSERT INTO mail_cdk_definitions (code, preset_id, max_claims, claimed_count, expires_at, enabled, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(code) DO UPDATE SET
                    preset_id = excluded.preset_id,
                    max_claims = excluded.max_claims,
                    claimed_count = excluded.claimed_count,
                    expires_at = excluded.expires_at,
                    enabled = excluded.enabled,
                    created_by = excluded.created_by,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at
                """;
        }
        return """
            INSERT INTO mail_cdk_definitions (code, preset_id, max_claims, claimed_count, expires_at, enabled, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                preset_id = VALUES(preset_id),
                max_claims = VALUES(max_claims),
                claimed_count = VALUES(claimed_count),
                expires_at = VALUES(expires_at),
                enabled = VALUES(enabled),
                created_by = VALUES(created_by),
                created_at = VALUES(created_at),
                updated_at = VALUES(updated_at)
            """;
    }

    private String logCleanupSql() {
        return """
            DELETE FROM mail_logs
            WHERE player_uuid = ?
              AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM mail_logs
                    WHERE player_uuid = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 100
                ) retained
              )
            """;
    }

    private Connection connection() throws SQLException {
        return getConnection();
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
            case CLAIMABLE -> " AND status IN ('UNREAD', 'READ') AND "
                + "(claim_commands <> '' OR EXISTS (SELECT 1 FROM mail_attachments a WHERE a.mail_id = mail_entries.id))";
            case SYSTEM -> " AND source_type = 'SYSTEM'";
            case PLAYER -> " AND source_type = 'PLAYER'";
            case PRESET -> " AND source_type = 'PRESET'";
            case CDK -> " AND source_type = 'CDK'";
            case ALL -> "";
        };
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

    private static String serializeConditions(List<MailCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (MailCondition condition : conditions) {
            if (condition != null) {
                values.add(condition.serialize());
            }
        }
        return String.join("\n", values);
    }

    private static List<MailCondition> deserializeConditions(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        List<MailCondition> conditions = new ArrayList<>();
        for (String line : rawValue.split("\n")) {
            MailCondition condition = MailCondition.deserialize(line);
            if (condition != null) {
                conditions.add(condition);
            }
        }
        return List.copyOf(conditions);
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), null)) {
            return resultSet.next();
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            return resultSet.next();
        }
    }

    private String alterTableAddColumnSql(String tableName, String columnName, String columnDefinition) {
        return "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
    }
}
