package xuanmo.arcartxsuite.onlinerewards.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;

public interface OnlineRewardsRepository {

    void initialize() throws SQLException;

    OnlineRewardsPlayerState loadState(UUID playerUuid) throws SQLException;

    void saveState(UUID playerUuid, OnlineRewardsPlayerState state) throws SQLException;

    Set<String> loadSignInDates(UUID playerUuid, String fromDate, String toDate) throws SQLException;

    Set<String> loadAllSignInDates(UUID playerUuid) throws SQLException;

    boolean hasSignInRecord(UUID playerUuid, String date) throws SQLException;

    void saveSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException;

    /**
     * 原子插入签到记录，已存在则返回 {@code false}（防并发双签）。
     */
    boolean tryInsertSignInRecord(UUID playerUuid, String playerName, String date, boolean makeup) throws SQLException;

    List<OnlineRewardsLeaderboardEntry> loadLeaderboard(
        OnlineRewardsLeaderboardScope scope,
        String periodKey,
        int offset,
        int limit
    ) throws SQLException;

    int countSignInRecords(String datePrefix) throws SQLException;

    boolean isServerGoalTriggered(String date, String goalId) throws SQLException;

    void saveServerGoalTriggered(String date, String goalId) throws SQLException;

    void close();
}
