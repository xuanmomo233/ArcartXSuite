package xuanmo.arcartxsuite.onlinerewards.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public interface OnlineRewardsRepository {

    void initialize() throws SQLException;

    /**
     * 关闭仓储，释放数据库连接等资源。
     */
    void shutdown();

    OnlineRewardsPlayerState loadState(UUID playerUuid) throws SQLException;

    void saveState(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException;

    /** Save counters and progress without treating the cached makeup-card balance as authoritative. */
    void saveProgress(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException;

    record RewardBatch(String batchId, List<Reward> rewards) {
        public RewardBatch {
            if (batchId == null || batchId.isBlank() || batchId.length() > 128) {
                throw new IllegalArgumentException("Invalid online reward batch ID");
            }
            rewards = List.copyOf(rewards);
        }
    }

    record ProgressCommit(UUID playerUuid, OnlineRewardsPlayerState state, List<RewardBatch> rewards) {
        public ProgressCommit {
            java.util.Objects.requireNonNull(playerUuid);
            state = state.copy();
            rewards = List.copyOf(rewards);
        }
    }

    /** Save staged progress plus newly earned delivery plans as one transaction. */
    void saveProgressBatch(List<ProgressCommit> commits) throws SQLException;

    record Attendance(UUID connectionId, long eligibleMinutes, long initialMinutes) {
        public Attendance(UUID connectionId, long eligibleMinutes) { this(connectionId, eligibleMinutes, 0); }
    }
    record ProgressRequest(UUID playerUuid, String playerName, Attendance attendance, ProgressMutation mutation) {}
    record ProgressPlan(boolean changed, List<RewardBatch> rewards) {
        public static final ProgressPlan UNCHANGED = new ProgressPlan(false, List.of());
    }
    record ProgressResult(UUID playerUuid, OnlineRewardsPlayerState state, boolean changed, boolean queuedRewards, long accountedMinutes) {}

    @FunctionalInterface
    interface ProgressMutation {
        ProgressPlan apply(OnlineRewardsPlayerState current, long newEligibleMinutes);
    }

    /** Mutate the locked current rows and commit progress, the attendance cursor and new rewards together. */
    List<ProgressResult> updateProgress(List<ProgressRequest> requests) throws SQLException;

    /** Discard the receipt after the server has drained the final jobs for a closed connection. */
    void forgetAttendance(UUID playerUuid, UUID connectionId) throws SQLException;

    /** Freeze one scheduled reward batch per recipient without modifying player progress. */
    int enqueueRewardBatch(String batchId, Map<UUID, List<Reward>> recipients) throws SQLException;

    enum MakeupCardOperation { ADD, REMOVE, SET }

    int adjustMakeupCards(UUID playerUuid, String playerName, MakeupCardOperation operation, int amount) throws SQLException;

    Set<String> loadSignInDates(UUID playerUuid, String fromDate, String toDate) throws SQLException;

    Set<String> loadAllSignInDates(UUID playerUuid) throws SQLException;

    boolean hasSignInRecord(UUID playerUuid, String date) throws SQLException;

    void saveSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException;

    enum SignInStatus { APPLIED, REPEATED, NO_CARDS }

    record SignInCommit(SignInStatus status, Set<String> dates, int makeupCards) {}

    /** Commit the sign-in date, optional makeup debit, and durable rewards as one transaction. */
    SignInCommit commitSignInWithRewards(UUID playerUuid, String playerName, String date, boolean makeup,
                                         Function<Set<String>, List<Reward>> plan) throws SQLException;

    default Set<String> recordSignInWithRewards(UUID playerUuid, String playerName, String date, boolean makeup,
                                                 Function<Set<String>, List<Reward>> plan) throws SQLException {
        SignInCommit result = commitSignInWithRewards(playerUuid, playerName, date, makeup, plan);
        return result.status() == SignInStatus.APPLIED ? result.dates() : Set.of();
    }

    record Reward(String kind, String value, String context) {}

    record Delivery(UUID playerUuid, String batchId, int step, Reward reward) {}
    record DeliveryReview(Delivery delivery, UUID attempt, String status) {}

    enum ItemSink { INVENTORY, MAIL }

    record ItemCheckpoint(Delivery delivery, UUID attempt, String node, String itemData, ItemSink sink) {
        public ItemCheckpoint {
            Objects.requireNonNull(delivery);
            Objects.requireNonNull(attempt);
            Objects.requireNonNull(sink);
            if (node == null || node.isBlank() || node.length() > 128 || itemData == null || itemData.isBlank()) {
                throw new IllegalArgumentException("Invalid online reward item checkpoint");
            }
        }
    }

    /** Freeze an item and its sink before any delivery side effect starts. */
    boolean stageItemCheckpoint(ItemCheckpoint checkpoint) throws SQLException;

    Optional<ItemCheckpoint> itemCheckpoint(UUID playerUuid) throws SQLException;

    /** Finish only the matching persisted item attempt, after its sink is durable. */
    boolean completeItemCheckpoint(ItemCheckpoint checkpoint) throws SQLException;

    List<Delivery> readyDeliveries(UUID playerUuid, int limit) throws SQLException;

    List<Delivery> readyDeliveries(Set<UUID> onlinePlayers, int limit) throws SQLException;

    boolean beginDelivery(Delivery delivery, UUID attempt) throws SQLException;

    /** Return this attempt to READY only when the caller knows its effect did not start. */
    boolean releaseUnstartedDelivery(Delivery delivery, UUID attempt) throws SQLException;

    void finishDelivery(Delivery delivery, UUID attempt, boolean success) throws SQLException;

    List<DeliveryReview> deliveriesForReview(UUID playerUuid, int limit) throws SQLException;

    boolean resolveDelivery(UUID playerUuid, String batch, int step, UUID attempt, boolean confirmed,
                            boolean retryPreset) throws SQLException;

    void savePresence(String nodeId, UUID serviceId, Map<UUID, String> online, long expiresAt) throws SQLException;

    void removePresence(String nodeId, UUID serviceId, UUID playerUuid) throws SQLException;

    Map<UUID, String> loadPresence(long now) throws SQLException;

    List<OnlineRewardsLeaderboardEntry> loadLeaderboard(
        OnlineRewardsLeaderboardScope scope,
        String periodKey,
        int offset,
        int limit
    ) throws SQLException;

    int countSignInRecords(String date) throws SQLException;
    record SignInGoalStatus(int signedCount, Set<String> triggeredGoalIds) {
        public SignInGoalStatus { triggeredGoalIds = Set.copyOf(triggeredGoalIds); }
    }
    SignInGoalStatus signInGoalStatus(String date) throws SQLException;

    boolean isServerGoalTriggered(String date, String goalId) throws SQLException;

    boolean recordServerGoalWithRewards(String date, String goalId, Map<UUID, List<Reward>> recipients) throws SQLException;
}
