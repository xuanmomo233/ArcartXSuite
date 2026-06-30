package xuanmo.arcartxsuite.entitytracker.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.config.CrossServerRankingSettings;
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.dao.CrossServerBossRankingDao;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerBossBestDamageDao;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * 定时聚合排行并写入 {@code cross_server_boss_rankings} 缓存表。
 */
public final class CrossServerRankingCacheService {

    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final CrossServerRankingSettings settings;
    private final CrossServerBossRankingDao rankingDao;
    private final PlayerBossBestDamageDao bestDamageDao;
    private final BossKillRecordDao killRecordDao;
    private final java.util.function.Supplier<PluginConfiguration> configurationSupplier;
    private final java.util.function.Supplier<String> nodeIdSupplier;
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    private BukkitTask refreshTask;

    public CrossServerRankingCacheService(
        JavaPlugin plugin,
        CrossServerRankingSettings settings,
        DataSource dataSource,
        java.util.function.Supplier<PluginConfiguration> configurationSupplier,
        java.util.function.Supplier<String> nodeIdSupplier
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.rankingDao = new CrossServerBossRankingDao(dataSource, plugin);
        this.bestDamageDao = new PlayerBossBestDamageDao(dataSource, plugin);
        this.killRecordDao = new BossKillRecordDao(dataSource, plugin);
        this.configurationSupplier = configurationSupplier;
        this.nodeIdSupplier = nodeIdSupplier;
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        long intervalTicks = settings.updateIntervalSeconds() * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::refreshAll, intervalTicks, intervalTicks
        );
        requestRefresh();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void requestRefresh() {
        if (!settings.enabled()) {
            return;
        }
        if (!refreshPending.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                refreshAll();
            } finally {
                refreshPending.set(false);
            }
        });
    }

    private void refreshAll() {
        if (!settings.enabled()) {
            return;
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        int limit = settings.maxEntries();
        LocalDateTime expire = end.plusSeconds(settings.updateIntervalSeconds() * 2L);

        try {
            for (String rankingType : settings.rankingTypes()) {
                switch (rankingType) {
                    case CrossServerRankingSettings.TYPE_BEST_DAMAGE -> cacheRanking(
                        rankingType, null, bestDamageDao.findCrossServerTopDamage(limit), expire
                    );
                    case CrossServerRankingSettings.TYPE_BOSS_DAMAGE -> refreshBossDamageRankings(limit, expire);
                    case CrossServerRankingSettings.TYPE_KILLS -> cacheRanking(
                        rankingType, null, killRecordDao.findKillRankings(start, end, limit), expire
                    );
                    case CrossServerRankingSettings.TYPE_PARTICIPATE -> cacheRanking(
                        rankingType, null, killRecordDao.findParticipateRankings(start, end, limit), expire
                    );
                    case CrossServerRankingSettings.TYPE_SERVER -> cacheRanking(
                        rankingType, null,
                        bestDamageDao.findServerTopDamage(nodeIdSupplier.get(), limit),
                        expire
                    );
                    default -> {
                    }
                }
            }
        } catch (SQLException exception) {
            AxsLog.logger().warning("[EntityTracker] 排行缓存刷新失败: " + exception.getMessage());
        }
    }

    private void refreshBossDamageRankings(int limit, LocalDateTime expire) throws SQLException {
        PluginConfiguration configuration = configurationSupplier.get();
        if (configuration == null) {
            return;
        }
        for (BossDefinition boss : configuration.bosses().values()) {
            if (!boss.enabled()) {
                continue;
            }
            List<PlayerBossBestDamage> rankings = bestDamageDao.findTopDamageByBoss(boss.mythicMobId(), limit);
            cacheRanking(CrossServerRankingSettings.TYPE_BOSS_DAMAGE, boss.mythicMobId(), rankings, expire);
        }
    }

    private void cacheRanking(
        String rankingType,
        String bossId,
        List<PlayerBossBestDamage> entries,
        LocalDateTime expire
    ) throws SQLException {
        JsonArray array = new JsonArray();
        int rank = 1;
        for (PlayerBossBestDamage entry : entries) {
            if (entry.getRank() <= 0) {
                entry.setRank(rank);
            }
            rank++;
            JsonObject row = new JsonObject();
            row.addProperty("uuid", entry.getPlayerUuid());
            row.addProperty("name", entry.getPlayerName());
            row.addProperty("score", entry.getBestDamage());
            row.addProperty("rank", entry.getRank());
            row.addProperty("bossId", entry.getBossId());
            row.addProperty("serverName", entry.getServerName());
            array.add(row);
        }
        rankingDao.upsert(rankingType, bossId, GSON.toJson(array), expire);
    }
}
