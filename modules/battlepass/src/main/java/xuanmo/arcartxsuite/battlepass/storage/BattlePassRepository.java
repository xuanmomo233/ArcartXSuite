package xuanmo.arcartxsuite.battlepass.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

public interface BattlePassRepository {

    BattlePassPlayerProgress loadProgress(UUID playerUuid, String seasonId) throws SQLException;

    void saveProgress(BattlePassPlayerProgress progress) throws SQLException;

    Map<String, Integer> loadTaskProgresses(UUID playerUuid, String seasonId) throws SQLException;

    void saveTaskProgress(UUID playerUuid, String seasonId, String taskId, int count) throws SQLException;

    Set<String> loadClaimedRewards(UUID playerUuid, String seasonId) throws SQLException;

    void saveClaimedReward(UUID playerUuid, String seasonId, String rewardId) throws SQLException;

    void resetPlayerProgress(UUID playerUuid, String seasonId) throws SQLException;

    int countActivePlayers(String seasonId) throws SQLException;

    List<PlayerTaskInstance> loadPlayerTasks(UUID playerUuid, String seasonId) throws SQLException;

    void savePlayerTask(UUID playerUuid, String seasonId, PlayerTaskInstance task) throws SQLException;

    void deletePlayerTasksByCategory(UUID playerUuid, String seasonId, BattlePassTask.TaskCategory category) throws SQLException;

    void deletePlayerTasks(UUID playerUuid, String seasonId) throws SQLException;
}
