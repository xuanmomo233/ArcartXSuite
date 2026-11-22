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
import xuanmo.arcartxsuite.mail.model.MailStatus;

public interface MailRepository {

    record MailInsertResult(long id) {
    }

    record CdkRedemption(MailMessage mail, String rejectionKey) {
        public boolean success() { return mail != null; }
    }

    record PresetCdk(MailCdkDefinition definition, boolean relativeExpiry) {}

    void initialize() throws SQLException;

    void upsertPlayerProfile(MailPlayerProfile profile) throws SQLException;

    Optional<MailPlayerProfile> loadPlayerProfile(UUID playerUuid) throws SQLException;

    Optional<MailPlayerProfile> findPlayerProfileByName(String playerName) throws SQLException;

    List<MailPlayerProfile> loadAllProfiles() throws SQLException;

    MailInsertResult insertMail(MailMessage message) throws SQLException;

    record DurableInsert(long id, boolean duplicate) {}

    DurableInsert insertDurableMail(String operationKey, MailMessage message) throws SQLException;

    /** Returns the committed mail ID, or seals the operation as unsent and returns zero. */
    long resolvePlayerSend(String operationKey, UUID recipient) throws SQLException;

    /** Positive is sent, negative is sealed unsent, and zero is still unknown. */
    long readPlayerSendReceipt(String operationKey, UUID recipient) throws SQLException;

    boolean transitionMailState(MailMessage message, MailStatus next, Instant now) throws SQLException;

    /**
     * 原子领取：仅当邮件当前为 UNREAD/READ 时标记为 CLAIMED。
     *
     * @return {@code true} 表示本次成功抢占领取权
     */
    boolean tryClaimMail(UUID ownerUuid, long mailId, Instant now) throws SQLException;

    enum ClaimPhase { NONE, READY, EXECUTING, REVIEW }

    record ClaimProgress(int step, ClaimPhase phase, UUID attempt) {}

    record ClaimMail(MailMessage mail, ClaimProgress progress) {}

    record ItemClaim(long mailId, int step, UUID attempt, boolean last) {}

    ClaimProgress loadClaimProgress(UUID ownerUuid, long mailId) throws SQLException;

    boolean reserveClaimStep(UUID ownerUuid, long mailId, int step, UUID attempt, Instant now) throws SQLException;

    boolean reserveItemClaims(UUID ownerUuid, List<ItemClaim> items, List<UUID> previousReceipts, Instant now) throws SQLException;

    boolean finishItemClaims(UUID ownerUuid, List<ItemClaim> items, boolean delivered, Instant now) throws SQLException;

    boolean markClaimReview(UUID ownerUuid, long mailId, int step, UUID attempt, Instant now) throws SQLException;

    boolean completeClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, boolean finished, Instant now) throws SQLException;

    boolean releaseReviewedClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, Instant now) throws SQLException;

    boolean releaseUnstartedClaimStep(UUID ownerUuid, long mailId, ClaimProgress expected, Instant now) throws SQLException;

    Optional<MailMessage> loadMail(UUID ownerUuid, long mailId) throws SQLException;

    List<MailMessage> loadInbox(UUID ownerUuid) throws SQLException;

    Optional<ClaimMail> loadClaimMail(UUID ownerUuid, long mailId) throws SQLException;

    List<ClaimMail> loadClaimablePage(UUID ownerUuid, long beforeId, int limit, Instant now) throws SQLException;

    MailPage<MailMessage> loadInboxPage(UUID ownerUuid, MailInboxQuery query) throws SQLException;

    /** The selected row has full attachment previews. Other rows only carry attachment summaries. */
    record InboxView(MailPage<MailMessage> page, MailMailboxStats stats, long selectedMailId) {}

    InboxView loadInboxView(UUID ownerUuid, MailInboxQuery query, long selectedMailId) throws SQLException;

    boolean markUnreadRead(UUID ownerUuid, long mailId, Instant now) throws SQLException;

    int deleteInbox(UUID ownerUuid, boolean allowUnclaimed, Instant now) throws SQLException;

    int countInbox(UUID ownerUuid, MailInboxFilter filter) throws SQLException;

    MailMailboxStats loadStats(UUID ownerUuid) throws SQLException;

    void appendLog(MailLogEntry entry) throws SQLException;

    MailPage<MailLogEntry> loadLogPage(UUID playerUuid, int page, int pageSize) throws SQLException;

    int cleanupMail(Instant now, Instant claimedBefore, Instant deletedBefore) throws SQLException;

    boolean createCdk(MailCdkDefinition definition) throws SQLException;

    void syncPresetCdks(List<PresetCdk> definitions) throws SQLException;

    Optional<MailCdkDefinition> loadCdk(String code) throws SQLException;

    List<MailCdkDefinition> loadCdks(int page, int pageSize) throws SQLException;

    boolean deleteCdk(String code) throws SQLException;

    /** Consumes the quota and inserts the reward mail in one transaction. The factory only builds data. */
    CdkRedemption redeemCdk(String code, UUID playerUuid, Instant now,
                            java.util.function.Function<String, MailMessage> mailForPreset) throws SQLException;

    int cleanupCdks(Instant now) throws SQLException;

    void close();
}
