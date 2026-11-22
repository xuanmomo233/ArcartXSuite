package xuanmo.arcartxsuite.onlinerewards.service;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Claims one effect at a time before the primary thread can start it. */
final class OnlineRewardsDeliveryWorker<S> implements AutoCloseable {
    private static final int MAX_OUTSTANDING = 32;
    private static final int PAGE_SIZE = 16;
    private final OnlineRewardsRepository repository;
    private final Logger logger;
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_OUTSTANDING + 2), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Delivery");
            thread.setDaemon(true);
            return thread;
        });
    private Claim<S> prepared;
    private boolean closed;
    private boolean fetching;
    private boolean fetched;
    private boolean more;
    private int outstanding;
    // The worker owns this read-only page. Only prepared has been claimed in the database.
    private final ArrayDeque<Delivery> candidates = new ArrayDeque<>(PAGE_SIZE);
    private long candidateAudience = Long.MIN_VALUE;
    private boolean fullCandidatePage;

    OnlineRewardsDeliveryWorker(OnlineRewardsRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    synchronized boolean canFetch() {
        return !closed && !fetching && !fetched && outstanding < MAX_OUTSTANDING;
    }

    synchronized boolean fetch(Map<UUID, S> online, long audience) {
        if (!canFetch() || online.isEmpty()) return false;
        fetching = true;
        worker.execute(() -> findReady(online, audience));
        return true;
    }

    private void findReady(Map<UUID, S> online, long audience) {
        synchronized (this) {
            if (closed) {
                fetching = false;
                return;
            }
        }
        Claim<S> selected = null;
        boolean full = false;
        try {
            if (candidateAudience != audience) {
                candidates.clear();
                candidateAudience = audience;
            }
            boolean fromCache = !candidates.isEmpty();
            if (!fromCache) {
                List<Delivery> rows = repository.readyDeliveries(online.keySet(), PAGE_SIZE);
                candidates.addAll(rows);
                fullCandidatePage = rows.size() == PAGE_SIZE;
            }
            Delivery row;
            while ((row = candidates.pollFirst()) != null) {
                UUID attempt = UUID.randomUUID();
                try {
                    if (repository.beginDelivery(row, attempt)) {
                        selected = new Claim<>(row, attempt, online.get(row.playerUuid()));
                        break;
                    }
                } catch (SQLException | RuntimeException failedClaim) {
                    // A lost acknowledgement may hide a committed claim. This attempt never reached a callback.
                    release(row, attempt);
                    throw failedClaim;
                }
            }
            full = selected != null || fullCandidatePage || fromCache;
        } catch (SQLException | RuntimeException failure) {
            candidates.clear();
            logger.log(Level.WARNING, "后台领取在线奖励失败", failure);
        }
        synchronized (this) {
            fetching = false;
            if (!closed) {
                if (selected != null) {
                    prepared = selected;
                    outstanding++;
                }
                fetched = true;
                more = full;
                return;
            }
        }
        if (selected != null) release(selected.delivery(), selected.attempt());
    }

    synchronized Claim<S> next() {
        Claim<S> claim = prepared;
        prepared = null;
        return claim;
    }

    synchronized boolean finishPage() {
        if (!fetched || prepared != null) return false;
        fetched = false;
        return more;
    }

    synchronized boolean pagePending() {
        return fetching || fetched;
    }

    void skipped(Claim<S> claim) {
        settle(claim, () -> release(claim.delivery(), claim.attempt()));
    }

    void settle(Claim<S> claim, Runnable writeReceipt) {
        if (!claim.settling().compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    writeReceipt.run();
                } finally {
                    synchronized (this) {
                        outstanding--;
                    }
                }
            });
        } catch (RejectedExecutionException stopped) {
            synchronized (this) {
                outstanding--;
            }
            logger.warning("在线奖励回执未写入，保留人工核对: " + claim.delivery().batchId() + "/" + claim.delivery().step());
        }
    }

    private void release(Delivery delivery, UUID attempt) {
        try {
            repository.releaseUnstartedDelivery(delivery, attempt);
        } catch (SQLException | RuntimeException failure) {
            logger.log(Level.WARNING, "退回未发放在线奖励失败，保留人工核对: " + delivery.batchId() + "/" + delivery.step(), failure);
        }
    }

    @Override
    public void close() {
        Claim<S> unstarted;
        synchronized (this) {
            if (closed) return;
            closed = true;
            unstarted = prepared;
            prepared = null;
        }
        if (unstarted != null) skipped(unstarted);
        worker.shutdown();
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return worker.stopped(); }

    record Claim<S>(Delivery delivery, UUID attempt, S session, AtomicBoolean settling) {
        private Claim(Delivery delivery, UUID attempt, S session) {
            this(delivery, attempt, session, new AtomicBoolean());
        }
    }
}
