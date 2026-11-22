package xuanmo.arcartxsuite.onlinerewards.service;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInGoalStatus;

/** Loads plain data for the player HUD and menu, sharing the daily goal query across readers. */
final class OnlineRewardsMenuReads implements AutoCloseable {
    static final int CAPACITY = 16;
    private static final long GOAL_CACHE_NANOS = TimeUnit.SECONDS.toNanos(2);
    private final OnlineRewardsRepository repository;
    private final AtomicLong goalRevision = new AtomicLong();
    private final AtomicBoolean warmingGoals = new AtomicBoolean();
    private final ModuleExecutor worker = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64), task -> {
            var thread = new Thread(task, "AXS-OnlineRewards-Menu");
            thread.setDaemon(true);
            return thread;
        });
    private volatile Goals goals;
    private volatile long goalRetryAfter;

    OnlineRewardsMenuReads(OnlineRewardsRepository repository) {
        this.repository = repository;
    }

    CompletionStage<Loaded> load(UUID player, boolean loadState, boolean loadDates, String goalDate, BooleanSupplier current) {
        return query(() -> {
            if (!current.getAsBoolean()) throw new RejectedExecutionException("OnlineRewards view expired");
            var state = loadState ? repository.loadState(player) : null;
            var dates = loadDates ? Set.copyOf(repository.loadAllSignInDates(player)) : null;
            return new Loaded(state, dates, goalDate != null ? loadGoals(goalDate) : null);
        });
    }

    <T> CompletionStage<T> query(Callable<T> read) {
        var future = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(read.call());
                } catch (Exception | LinkageError failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException busy) {
            future.completeExceptionally(busy);
        }
        return future;
    }

    private Goals loadGoals(String date) throws SQLException {
        long revision = goalRevision.get();
        var cached = goals;
        if (fresh(cached, date, revision)) return cached;
        var status = repository.signInGoalStatus(date);
        goals = new Goals(date, revision, System.nanoTime(), status);
        return goals;
    }

    SignInGoalStatus goalsForDisplay(String date) {
        var cached = goals;
        long backoff = goalRetryAfter;
        if (!fresh(cached, date, goalRevision.get()) && (backoff == 0 || System.nanoTime() - backoff >= 0)
                && warmingGoals.compareAndSet(false, true)) {
            query(() -> loadGoals(date)).whenComplete((ignored, failure) -> {
                goalRetryAfter = failure == null ? 0 : System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                warmingGoals.set(false);
            });
        }
        return cached != null && cached.date().equals(date) ? cached.status() : new SignInGoalStatus(0, Set.of());
    }

    private static boolean fresh(Goals value, String date, long revision) {
        return value != null && value.date().equals(date) && value.revision() == revision
            && System.nanoTime() - value.loadedAt() < GOAL_CACHE_NANOS;
    }

    void invalidateGoals() { goalRevision.incrementAndGet(); }

    @Override public void close() { worker.shutdown(); }
    CompletionStage<Void> stopped() { return worker.stopped(); }

    record Goals(String date, long revision, long loadedAt, SignInGoalStatus status) {}
    record Loaded(OnlineRewardsPlayerState state, Set<String> dates, Goals goals) {}
}
