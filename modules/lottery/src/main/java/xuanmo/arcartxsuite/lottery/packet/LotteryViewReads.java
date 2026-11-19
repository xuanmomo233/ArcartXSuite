package xuanmo.arcartxsuite.lottery.packet;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** One running read and at most its latest replacement per player. Queue changes run on the server thread. */
final class LotteryViewReads {
    @FunctionalInterface
    interface Query<T> {
        T load() throws SQLException;
    }

    private static final class Lane {
        Runnable replacement;
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Lane> lanes = new HashMap<>();
    private final ModuleExecutor worker = new ModuleExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, "AXS-Lottery-Views");
            thread.setDaemon(true);
            return thread;
        });
    private volatile boolean stopped;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});

    LotteryViewReads(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    <T> void submit(UUID playerId, Query<T> query, BiConsumer<T, Throwable> complete) {
        if (stopped) return;
        Lane active = lanes.get(playerId);
        if (active != null) {
            active.replacement = () -> launch(playerId, active, query, complete);
            return;
        }
        Lane lane = new Lane();
        lanes.put(playerId, lane);
        launch(playerId, lane, query, complete);
    }

    void discardReplacement(UUID playerId) {
        Lane lane = lanes.get(playerId);
        if (lane != null) lane.replacement = null;
    }

    private <T> void launch(UUID playerId, Lane lane, Query<T> query, BiConsumer<T, Throwable> complete) {
        try {
            worker.execute(() -> {
                if (stopped) return;
                T data = null;
                Throwable error = null;
                try {
                    data = query.load();
                } catch (SQLException | RuntimeException failure) {
                    error = failure;
                }
                T result = data;
                Throwable failure = error;
                if (stopped || !plugin.isEnabled()) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> callbacks.call(() -> {
                        finish(playerId, lane, () -> complete.accept(result, failure));
                        return null;
                    }, null));
                } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
                    // Plugin shutdown raced dispatch of the read result.
                }
            });
        } catch (RejectedExecutionException failure) {
            finish(playerId, lane, () -> complete.accept(null, failure));
        }
    }

    private void finish(UUID playerId, Lane lane, Runnable complete) {
        if (stopped) return;
        Runnable replacement = lane.replacement;
        lane.replacement = null;
        if (replacement != null) {
            replacement.run();
            return;
        }
        lanes.remove(playerId, lane);
        complete.run();
    }

    void stop() {
        stopped = true;
        callbacks.close();
        lanes.clear();
        worker.shutdown();
    }

    CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(worker.stopped().toCompletableFuture(), callbacks.stopped().toCompletableFuture());
    }
}
