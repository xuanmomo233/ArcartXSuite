package xuanmo.arcartxsuite.bridge;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/** Host-owned writer for confirmed command outcomes, including callbacks from retiring modules. */
public final class PendingCommandReceipts {
    private static final int MAX_PENDING = 10_000;
    private static final int BATCH_SIZE = 64;
    private static final long RETRY_DELAY_MILLIS = 1000;
    private static final long RETRY_LOG_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final String CLAIM = " WHERE id=? AND claimed_by=? AND claimed_at=? AND reward_type='COMMAND' AND attempts=1";

    private final StorageManager storage;
    private final Logger logger;
    private final ArrayDeque<Receipt> pending = new ArrayDeque<>();
    private final ModuleExecutor worker = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), action -> {
            Thread thread = new Thread(action, "AXS-PendingCommandReceipts");
            thread.setDaemon(true);
            return thread;
        });
    private boolean draining;
    private boolean closed;
    private volatile Throwable stopFailure;

    public PendingCommandReceipts(StorageManager storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    synchronized boolean record(long id, String owner, long stamp, boolean delivered) {
        if (closed || pending.size() >= MAX_PENDING) return false;
        pending.addLast(new Receipt(id, owner, stamp, delivered));
        if (!draining) {
            draining = true;
            try {
                worker.execute(this::drain);
            } catch (RejectedExecutionException failure) {
                draining = false;
                pending.removeLast();
                return false;
            }
        }
        return true;
    }

    public synchronized CompletionStage<Void> stop() {
        closed = true;
        notifyAll();
        worker.shutdown();
        return worker.stopped().thenRun(() -> {
            if (stopFailure != null) throw new CompletionException(stopFailure);
        });
    }

    private void drain() {
        long lastFailureLog = 0;
        while (true) {
            List<Receipt> batch;
            synchronized (this) {
                if (pending.isEmpty()) {
                    stopFailure = null;
                    draining = false;
                    return;
                }
                // A retry only replays the write against the original claim, never the command.
                batch = pending.stream().limit(BATCH_SIZE).toList();
            }
            try {
                write(batch);
            } catch (SQLException | RuntimeException | LinkageError failure) {
                long now = System.nanoTime();
                if (lastFailureLog == 0 || now - lastFailureLog >= RETRY_LOG_NANOS) {
                    logger.log(Level.WARNING, "命令奖励回执写入未确认，将重试写入", failure);
                    lastFailureLog = now;
                }
                if (!awaitRetry(failure)) return;
                continue;
            }
            synchronized (this) {
                for (int count = batch.size(); count > 0; count--) pending.removeFirst();
            }
        }
    }

    private synchronized boolean awaitRetry(Throwable failure) {
        if (!closed) {
            try {
                wait(RETRY_DELAY_MILLIS);
                return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failure.addSuppressed(interrupted);
            }
        }
        stopFailure = failure;
        draining = false;
        logger.log(Level.WARNING, "命令奖励回执写入未完成，保留原认领凭证供核对", failure);
        return false;
    }

    private void write(List<Receipt> receipts) throws SQLException {
        try (var connection = storage.getHostConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var delivered = connection.prepareStatement("DELETE FROM axs_pending_rewards" + CLAIM);
                 var notRun = connection.prepareStatement("UPDATE axs_pending_rewards SET attempts=0,"
                     + "claimed_at=NULL,claimed_by=NULL" + CLAIM)) {
                boolean completeAny = false;
                boolean releaseAny = false;
                for (Receipt receipt : receipts) {
                    if (receipt.delivered()) {
                        add(delivered, receipt);
                        completeAny = true;
                    } else {
                        add(notRun, receipt);
                        releaseAny = true;
                    }
                }
                if (completeAny) delivered.executeBatch();
                if (releaseAny) notRun.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void add(PreparedStatement statement, Receipt receipt) throws SQLException {
        statement.setLong(1, receipt.id());
        statement.setString(2, receipt.owner());
        statement.setLong(3, receipt.stamp());
        statement.addBatch();
    }

    private record Receipt(long id, String owner, long stamp, boolean delivered) {}
}
