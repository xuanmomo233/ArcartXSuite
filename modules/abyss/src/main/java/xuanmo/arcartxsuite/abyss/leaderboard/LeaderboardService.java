package xuanmo.arcartxsuite.abyss.leaderboard;

import java.util.List;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.LeaderboardEntry;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;

/**
 * 排行榜服务：按地牢最佳通关时间排序（数据源 = records 表）。
 */
public final class LeaderboardService {

    private final AbyssModuleConfiguration.LeaderboardSection cfg;
    private final PlayerDataManager data;

    public LeaderboardService(AbyssModuleConfiguration.LeaderboardSection cfg, PlayerDataManager data) {
        this.cfg = cfg;
        this.data = data;
    }

    public boolean enabled() { return cfg.enabled(); }

    /** 取地牢榜单（异步调用，勿在主线程跑）。 */
    public List<LeaderboardEntry> top(String dungeonId) {
        return data.loadLeaderboard(dungeonId, cfg.size());
    }

    /** 通关后记录成绩（异步落盘）。 */
    public void recordCompletion(java.util.UUID player, String dungeonId, long timeSeconds) {
        if (!cfg.enabled()) return;
        data.saveRecord(player, dungeonId, timeSeconds);
    }
}
