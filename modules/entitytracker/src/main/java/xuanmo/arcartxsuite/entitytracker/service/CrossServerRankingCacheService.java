package xuanmo.arcartxsuite.entitytracker.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.config.CrossServerRankingSettings;
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.dao.CrossServerBossRankingDao;
import xuanmo.arcartxsuite.entitytracker.dao.CrossServerBossRankingDao.Snapshot;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerBossBestDamageDao;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import java.util.logging.Logger;

/**
 * 定时聚合排行并写入 {@code cross_server_boss_rankings} 缓存表。
 */
public final class CrossServerRankingCacheService {

    private static final Gson GSON = new Gson();
    private static final long COALESCE_TICKS = 20L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CrossServerRankingSettings settings;
    private final Set<String> enabledTypes;
    private final CrossServerBossRankingDao rankingDao;
    private final PlayerBossBestDamageDao bestDamageDao;
    private final BossKillRecordDao killRecordDao;
    private final java.util.function.Supplier<PluginConfiguration> configurationSupplier;
    private final java.util.function.Supplier<String> nodeIdSupplier;
    private final BooleanSupplier crossServerRankingAllowed;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback refresh =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::runRefresh);

    private BukkitTask refreshTask;
    private BukkitTask queuedRefresh;
    private boolean closed;
    private boolean running;
    private final Set<String> dirtyTypes = new LinkedHashSet<>();
    private final Set<String> dirtyBosses = new LinkedHashSet<>();

    public CrossServerRankingCacheService(
        JavaPlugin plugin,
        Logger logger,
        CrossServerRankingSettings settings,
        DataSource dataSource,
        java.util.function.Supplier<PluginConfiguration> configurationSupplier,
        java.util.function.Supplier<String> nodeIdSupplier,
        BooleanSupplier crossServerRankingAllowed
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
        this.enabledTypes = new LinkedHashSet<>(settings.rankingTypes());
        this.rankingDao = new CrossServerBossRankingDao(dataSource, plugin);
        this.bestDamageDao = new PlayerBossBestDamageDao(dataSource, plugin);
        this.killRecordDao = new BossKillRecordDao(dataSource, this.logger);
        this.configurationSupplier = configurationSupplier;
        this.nodeIdSupplier = nodeIdSupplier;
        this.crossServerRankingAllowed = crossServerRankingAllowed;
    }

    public synchronized void start() {
        if (closed || !settings.enabled() || refreshTask != null) {
            return;
        }
        long intervalTicks = settings.updateIntervalSeconds() * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::requestRefresh, intervalTicks, intervalTicks
        );
        dirtyTypes.addAll(enabledTypes);
        queueRefresh(1L);
    }

    public synchronized void shutdown() {
        closed = true;
        refresh.close();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (queuedRefresh != null) {
            queuedRefresh.cancel();
            queuedRefresh = null;
        }
    }

    public synchronized void requestRefresh() {
        if (closed || !settings.enabled()) return;
        dirtyTypes.addAll(enabledTypes);
        dirtyBosses.clear();
        queueRefresh(COALESCE_TICKS);
    }

    public synchronized void requestKillRefresh() {
        if (closed || !settings.enabled()) return;
        markType(CrossServerRankingSettings.TYPE_KILLS);
        markType(CrossServerRankingSettings.TYPE_PARTICIPATE);
        queueRefresh(COALESCE_TICKS);
    }

    public synchronized void requestDamageRefresh(String bossId) {
        if (closed || !settings.enabled()) return;
        markType(CrossServerRankingSettings.TYPE_BEST_DAMAGE);
        markType(CrossServerRankingSettings.TYPE_SERVER);
        if (enabledTypes.contains(CrossServerRankingSettings.TYPE_BOSS_DAMAGE)
            && !dirtyTypes.contains(CrossServerRankingSettings.TYPE_BOSS_DAMAGE)) {
            var configuration = configurationSupplier.get();
            var boss = configuration == null ? null : configuration.findBoss(bossId);
            if (boss != null && boss.enabled()) dirtyBosses.add(boss.mythicMobId());
        }
        queueRefresh(COALESCE_TICKS);
    }

    private void markType(String type) {
        if (enabledTypes.contains(type)) dirtyTypes.add(type);
    }

    private void queueRefresh(long delay) {
        if (closed || running || queuedRefresh != null || (dirtyTypes.isEmpty() && dirtyBosses.isEmpty())) return;
        if (!crossServerRankingAllowed.getAsBoolean()) return;
        try {
            queuedRefresh = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, refresh, delay);
        } catch (RuntimeException | LinkageError failure) {
            logger.fine("排行刷新调度未受理: " + failure.getMessage());
        }
    }

    private void runRefresh() {
        List<String> types;
        Set<String> bosses;
        synchronized (this) {
            queuedRefresh = null;
            if (closed || (dirtyTypes.isEmpty() && dirtyBosses.isEmpty())) return;
            if (!crossServerRankingAllowed.getAsBoolean()) return;
            running = true;
            types = List.copyOf(dirtyTypes);
            bosses = Set.copyOf(dirtyBosses);
            dirtyTypes.clear();
            dirtyBosses.clear();
        }
        try {
            refreshRankings(types, bosses);
        } finally {
            synchronized (this) {
                running = false;
                queueRefresh(COALESCE_TICKS);
            }
        }
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped() { return refresh.stopped(); }

    private void refreshRankings(List<String> types, Set<String> bosses) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        int limit = settings.maxEntries();
        LocalDateTime expire = end.plusSeconds(settings.updateIntervalSeconds() * 2L);

        try {
            var snapshots = new ArrayList<Snapshot>();
            boolean kills = types.contains(CrossServerRankingSettings.TYPE_KILLS);
            boolean participation = types.contains(CrossServerRankingSettings.TYPE_PARTICIPATE);
            if (kills || participation) {
                var activity = killRecordDao.findActivityRankings(start, end, limit, kills, participation);
                if (kills) snapshots.add(snapshot(CrossServerRankingSettings.TYPE_KILLS, null, activity.kills()));
                if (participation) snapshots.add(snapshot(CrossServerRankingSettings.TYPE_PARTICIPATE, null, activity.participation()));
            }
            for (String rankingType : types) {
                switch (rankingType) {
                    case CrossServerRankingSettings.TYPE_BEST_DAMAGE -> snapshots.add(snapshot(
                        rankingType, null, bestDamageDao.findCrossServerTopDamage(limit)));
                    case CrossServerRankingSettings.TYPE_BOSS_DAMAGE -> addBossDamageRankings(limit, snapshots);
                    case CrossServerRankingSettings.TYPE_SERVER -> snapshots.add(snapshot(
                        rankingType, null, bestDamageDao.findServerTopDamage(nodeIdSupplier.get(), limit)));
                    default -> {
                    }
                }
            }
            for (String bossId : bosses) addBossDamageRanking(limit, snapshots, bossId);
            if (crossServerRankingAllowed.getAsBoolean()) rankingDao.replaceAll(snapshots, expire);
        } catch (SQLException exception) {
            this.logger.warning("排行缓存刷新失败: " + exception.getMessage());
        }
    }

    private void addBossDamageRankings(int limit, List<Snapshot> snapshots) throws SQLException {
        PluginConfiguration configuration = configurationSupplier.get();
        if (configuration == null) {
            return;
        }
        for (BossDefinition boss : configuration.bosses().values()) {
            if (!boss.enabled()) {
                continue;
            }
            addBossDamageRanking(limit, snapshots, boss.mythicMobId());
        }
    }

    private void addBossDamageRanking(int limit, List<Snapshot> snapshots, String bossId) throws SQLException {
        snapshots.add(snapshot(CrossServerRankingSettings.TYPE_BOSS_DAMAGE, bossId,
            bestDamageDao.findTopDamageByBoss(bossId, limit)));
    }

    private static Snapshot snapshot(
        String rankingType,
        String bossId,
        List<PlayerBossBestDamage> entries
    ) {
        JsonArray array = new JsonArray();
        int rank = 1;
        for (PlayerBossBestDamage entry : entries) {
            JsonObject row = new JsonObject();
            row.addProperty("uuid", entry.getPlayerUuid());
            row.addProperty("name", entry.getPlayerName());
            row.addProperty("score", entry.getBestDamage());
            row.addProperty("rank", entry.getRank() <= 0 ? rank : entry.getRank());
            row.addProperty("bossId", entry.getBossId());
            row.addProperty("serverName", entry.getServerName());
            array.add(row);
            rank++;
        }
        return new Snapshot(rankingType, bossId, GSON.toJson(array));
    }
}



