package xuanmo.arcartxsuite.mail.storage;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailPlayerProfile;

public interface MailRepository {

    record MailInsertResult(long id) {
    }

    record CdkClaimResult(boolean success, String message, MailCdkDefinition definition) {
        public static CdkClaimResult success(MailCdkDefinition definition) {
            return new CdkClaimResult(true, "", definition);
        }

        public static CdkClaimResult failure(String message, MailCdkDefinition definition) {
            return new CdkClaimResult(false, message, definition);
        }
    }

    void initialize() throws SQLException;

    void upsertPlayerProfile(MailPlayerProfile profile) throws SQLException;

    Optional<MailPlayerProfile> loadPlayerProfile(UUID playerUuid) throws SQLException;

    Optional<MailPlayerProfile> findPlayerProfileByName(String playerName) throws SQLException;

    List<MailPlayerProfile> loadAllProfiles() throws SQLException;

    MailInsertResult insertMail(MailMessage message) throws SQLException;

    void updateMailState(MailMessage message) throws SQLException;

    Optional<MailMessage> loadMail(UUID ownerUuid, long mailId) throws SQLException;

    List<MailMessage> loadInbox(UUID ownerUuid) throws SQLException;

    MailPage<MailMessage> loadInboxPage(UUID ownerUuid, MailInboxQuery query) throws SQLException;

    int countInbox(UUID ownerUuid, MailInboxFilter filter) throws SQLException;

    MailMailboxStats loadStats(UUID ownerUuid) throws SQLException;

    void appendLog(MailLogEntry entry) throws SQLException;

    MailPage<MailLogEntry> loadLogPage(UUID playerUuid, int page, int pageSize) throws SQLException;

    int cleanupMail(Instant now, Instant claimedBefore, Instant deletedBefore) throws SQLException;

    void saveCdk(MailCdkDefinition definition) throws SQLException;

    Optional<MailCdkDefinition> loadCdk(String code) throws SQLException;

    List<MailCdkDefinition> loadCdks(int page, int pageSize) throws SQLException;

    boolean deleteCdk(String code) throws SQLException;

    CdkClaimResult claimCdk(String code, UUID playerUuid, Instant now) throws SQLException;

    int cleanupCdks(Instant now) throws SQLException;

    void close();
}
