package xuanmo.arcartxsuite.qqbot.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.ActivityKey;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.StorageFailure;

/** Batches activity counters without delaying the processing of ordinary group messages. */
final class QQBotActivityRecorder {
    private static final int MAX_PENDING_MESSAGES = 1024;

    private final QQBotRepository repository;
    private final Logger logger;
    private final Object writeLock = new Object();
    private final ModuleScheduledExecutor writer = new ModuleScheduledExecutor(1, task -> {
        var thread = new Thread(task, "QQBot-Activity");
        thread.setDaemon(true);
        return thread;
    });
    private Map<ActivityKey, Integer> pending = new HashMap<>();
    private int pendingMessages;
    private Batch retry;
    private long skippedMessages;
    private ScheduledFuture<?> nextWrite;
    private boolean closed;

    QQBotActivityRecorder(QQBotRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    synchronized void record(long qqId, long groupId, LocalDate date) {
        if (closed) return;
        if (pendingMessages == MAX_PENDING_MESSAGES) {
            skippedMessages++;
            return;
        }
        pending.merge(new ActivityKey(qqId, groupId, date), 1, Integer::sum);
        pendingMessages++;
        scheduleWrite();
    }

    /** Called by the background command handler before reading the ranking. */
    void flush() { flush(false, false); }

    private synchronized void scheduleWrite() {
        if (!closed && nextWrite == null && (retry != null || !pending.isEmpty())) {
            nextWrite = writer.schedule(() -> flush(true, false), 1, TimeUnit.SECONDS);
        }
    }

    private void flush(boolean scheduled, boolean retiring) {
        synchronized (writeLock) {
            Batch retrying;
            synchronized (this) {
                if (scheduled) nextWrite = null;
                if (closed && !retiring) return;
                retrying = retry;
                retry = null;
            }
            try {
                if (retrying != null && !writeBatch(retrying, retiring)) return;
                var batch = takePending();
                if (batch != null) writeBatch(batch, retiring);
            } finally {
                scheduleWrite();
            }
        }
    }

    private Batch takePending() {
        Batch batch;
        long skipped;
        synchronized (this) {
            if (pending.isEmpty()) return null;
            batch = new Batch(pending, pendingMessages);
            skipped = skippedMessages;
            pending = new HashMap<>();
            pendingMessages = 0;
            skippedMessages = 0;
        }
        if (skipped > 0) logger.warning("QQBot 活跃度队列已满，跳过 " + skipped + " 条统计");
        return batch;
    }

    private boolean writeBatch(Batch batch, boolean retiring) {
        try {
            repository.recordActivities(batch.increments());
        } catch (RuntimeException failure) {
            if (failure instanceof StorageFailure storage && storage.uncommitted()) {
                if (!retiring) {
                    synchronized (this) { retry = batch; }
                    logger.log(Level.FINE, "QQBot 活跃度批次未提交，将重试", failure);
                    return false;
                }
                logger.log(Level.WARNING, "QQBot 停机时仍有 " + batch.messageCount() + " 条活跃度未提交", failure);
            } else {
                logger.log(Level.WARNING, "QQBot " + batch.messageCount() + " 条活跃度统计提交未确认", failure);
            }
        }
        return true;
    }

    synchronized CompletionStage<Void> retire() {
        if (!closed) {
            closed = true;
            if (nextWrite != null) nextWrite.cancel(false);
            writer.execute(() -> {
                try { flush(true, true); }
                finally { writer.shutdown(); }
            });
        }
        return writer.stopped();
    }

    private record Batch(Map<ActivityKey, Integer> increments, int messageCount) { }
}
