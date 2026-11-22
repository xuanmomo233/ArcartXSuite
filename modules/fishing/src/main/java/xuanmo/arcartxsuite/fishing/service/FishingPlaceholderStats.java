package xuanmo.arcartxsuite.fishing.service;

import static java.util.Map.entry;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.fatigue.FatigueEngine;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.DisplayData;

/** Display-only snapshots. Database reads and formatting run outside the caller's thread. */
final class FishingPlaceholderStats {
    private static final int MAX_PROFILES = 1024;
    private static final long REFRESH_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long BUSY_RETRY_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final FishingRepository repository;
    private final FishingModuleConfiguration configuration;
    private final FatigueEngine fatigueEngine;
    private final Logger logger;
    private final Map<String, String> defaults;
    private final LinkedHashMap<UUID, Snapshot> snapshots = new LinkedHashMap<>(16, 0.75f, true);
    private final ModuleExecutor worker = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), work -> {
            var thread = new Thread(work, "AXS-Fishing-Placeholders");
            thread.setDaemon(true);
            return thread;
        });

    private boolean closed;

    FishingPlaceholderStats(FishingRepository repository, FishingModuleConfiguration configuration,
                            FatigueEngine fatigueEngine, Logger logger) {
        this.repository = repository;
        this.configuration = configuration;
        this.fatigueEngine = fatigueEngine;
        this.logger = logger;
        this.defaults = format(new DisplayData(0, 0, 0, 0, 0, 0, 0, 0, Map.of()));
    }

    synchronized String value(UUID uuid, String name) {
        String fallback = defaults.get(name);
        if (fallback == null || closed || fatigueEngine == null && name.startsWith("fatigue")) return fallback;

        long now = System.nanoTime();
        Snapshot snapshot = snapshots.get(uuid);
        if (snapshot == null) {
            if (snapshots.size() >= MAX_PROFILES) {
                var oldest = snapshots.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            snapshot = new Snapshot(now, defaults);
            snapshots.put(uuid, snapshot);
        }
        if (!snapshot.loading && now - snapshot.refreshAt >= 0) {
            Snapshot request = snapshot;
            request.loading = true;
            try {
                worker.execute(() -> refresh(uuid, request));
            } catch (RejectedExecutionException e) {
                request.loading = false;
                request.refreshAt = now + BUSY_RETRY_NANOS;
            }
        }
        return snapshot.values.get(name);
    }

    private void refresh(UUID uuid, Snapshot request) {
        synchronized (this) {
            if (closed || snapshots.get(uuid) != request) return;
        }
        Map<String, String> values = null;
        try {
            values = format(repository.loadDisplayData(uuid, configuration.season().seasonId()));
        } catch (SQLException | RuntimeException error) {
            logger.log(Level.FINE, "Could not refresh Fishing placeholders", error);
        } finally {
            synchronized (this) {
                if (!closed && snapshots.get(uuid) == request) {
                    if (values != null) request.values = values;
                    request.refreshAt = System.nanoTime() + REFRESH_NANOS;
                    request.loading = false;
                }
            }
        }
    }

    private Map<String, String> format(DisplayData data) {
        int fishTypes = configuration.fishes().size();
        int collected = data.catchCounts().size();
        int favoriteCount = 0;
        String favorite = "";
        if (collected != 0) {
            for (var fish : configuration.fishes()) {
                int count = data.catchCounts().getOrDefault(fish.id(), 0);
                if (count > favoriteCount) {
                    favorite = fish.displayName();
                    favoriteCount = count;
                }
            }
        }
        int nextXp = data.level() <= 0 ? 0
            : configuration.season().xpCurve().xpRequiredForLevelOr(data.level() + 1, Integer.MAX_VALUE);
        int fatigue = fatigueEngine == null ? 0 : data.fatigue();
        double efficiency = fatigueEngine == null ? 1.0 : fatigueEngine.efficiencyMultiplier(fatigue);
        String xp = Integer.toString(data.currentXp());
        return Map.ofEntries(
            entry("level", Integer.toString(data.level())),
            entry("current_xp", xp),
            entry("xp", xp),
            entry("xp_for_next", Integer.toString(nextXp)),
            entry("talent_points", Integer.toString(data.unspentTalentPoints())),
            entry("total_talent_points", Integer.toString(data.talentPoints())),
            entry("total_caught", Integer.toString(data.totalCaught())),
            entry("perfect_catches", Integer.toString(data.perfectCatches())),
            entry("treasure_caught", Integer.toString(data.treasureCaught())),
            entry("collection_count", Integer.toString(collected)),
            entry("collection", collected + "/" + fishTypes),
            entry("collection_percent", Long.toString(fishTypes == 0 ? 0 : collected * 100L / fishTypes)),
            entry("favorite_fish", favorite),
            entry("fatigue", Integer.toString(fatigue)),
            entry("fatigue_efficiency", String.format(Locale.ROOT, "%.0f%%", efficiency * 100))
        );
    }

    synchronized void forget(UUID uuid) {
        snapshots.remove(uuid);
    }

    synchronized void shutdown() {
        closed = true;
        snapshots.clear();
        worker.shutdownNow();
    }

    CompletionStage<Void> stopped() {
        return worker.stopped();
    }

    private static final class Snapshot {
        private long refreshAt;
        private Map<String, String> values;
        private boolean loading;

        private Snapshot(long refreshAt, Map<String, String> values) {
            this.refreshAt = refreshAt;
            this.values = values;
        }
    }
}
