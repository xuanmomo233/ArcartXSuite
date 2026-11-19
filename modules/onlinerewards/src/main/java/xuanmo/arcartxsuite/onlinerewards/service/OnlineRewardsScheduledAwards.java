package xuanmo.arcartxsuite.onlinerewards.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Persists each frozen cohort without reading or saving player progress. */
final class OnlineRewardsScheduledAwards implements AutoCloseable {
    private static final int MAX_QUEUED_OCCURRENCES = 16;
    private static final int MAX_ATTEMPTS = 3;
    private final OnlineRewardsRepository repository;
    private final Logger logger;
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_QUEUED_OCCURRENCES), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Scheduled");
            thread.setDaemon(true);
            return thread;
        });

    OnlineRewardsScheduledAwards(OnlineRewardsRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    boolean submit(String batch, Map<UUID, List<Reward>> recipients, IntConsumer committed) {
        try {
            worker.execute(() -> enqueue(batch, recipients, committed));
            return true;
        } catch (RejectedExecutionException busy) {
            return false;
        }
    }

    private void enqueue(String batch, Map<UUID, List<Reward>> recipients, IntConsumer committed) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            int accepted;
            try {
                accepted = repository.enqueueRewardBatch(batch, recipients);
            } catch (SQLException failure) {
                if (attempt == MAX_ATTEMPTS) {
                    logger.log(Level.WARNING, "时刻奖励尚未确认入库 " + batch, failure);
                    return;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "时刻奖励计划无法保存 " + batch, failure);
                return;
            }
            committed.accept(accepted);
            return;
        }
    }

    @Override
    public void close() {
        worker.shutdown();
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return worker.stopped(); }
}
