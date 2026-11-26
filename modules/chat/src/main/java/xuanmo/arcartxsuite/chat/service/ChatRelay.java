package xuanmo.arcartxsuite.chat.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.chat.storage.ChatRelayJournal;

/** Uses Chat's shared database for ordered cross-server messages and remembers the last admitted local audience. */
final class ChatRelay implements AutoCloseable {
    private static final long RECONNECT_MILLIS = 120_000;
    private static final long MESSAGE_MILLIS = 600_000;
    private static final int MAX_PROXY_BYTES = Short.MAX_VALUE;
    private final ChatRelayJournal journal;
    private final String node;
    private final Consumer<Runnable> onMain;
    private final Delivery delivery;
    private final Runnable dispatchAdmitted;
    private final Logger logger;
    private final BooleanSupplier authorized;
    private final long startedAt = System.currentTimeMillis();
    private final long reconnectUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RECONNECT_MILLIS);
    private final xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor receiver =
        new xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor(1, task -> daemon("AXS-Chat-Relay-In", task));
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor writer = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(255), task -> daemon("AXS-Chat-Relay-Out", task));
    private volatile Set<UUID> previousAudience;
    private volatile Set<UUID> currentAudience;
    private Set<UUID> persistedAudience;
    private Long cursor;
    private boolean cleanedRecoveryAudience;
    private long nextPull;
    private long nextPurge;
    private long nextWarning;
    private long retryAt;
    private volatile boolean closed;
    private CompletableFuture<List<Receipt>> submitted;
    private List<Receipt> pending = List.of();

    ChatRelay(ChatRelayJournal journal, String node, Consumer<Runnable> onMain,
              Delivery delivery, Runnable dispatchAdmitted, Logger logger, BooleanSupplier authorized) {
        this.journal = journal;
        this.node = node;
        this.onMain = onMain;
        this.delivery = delivery;
        this.dispatchAdmitted = dispatchAdmitted;
        this.logger = logger;
        this.authorized = authorized;
    }

    void start(Set<UUID> localAudience) {
        audienceChanged(localAudience);
        receiver.scheduleWithFixedDelay(this::tick, 0, 50, TimeUnit.MILLISECONDS);
    }

    void audienceChanged(Set<UUID> localAudience) {
        currentAudience = Set.copyOf(localAudience);
    }

    boolean isRecovery(long timestamp) {
        return timestamp > 0 && timestamp < startedAt;
    }

    Set<UUID> previousAudience() {
        return previousAudience;
    }

    boolean waitingForRejoin(Set<UUID> online) {
        Set<UUID> previous = previousAudience;
        return previous == null || (withinRejoinWindow() && !online.containsAll(previous));
    }

    static boolean fits(String payload) {
        return payload.length() <= MAX_PROXY_BYTES / 3
            || (payload.length() <= MAX_PROXY_BYTES && payload.getBytes(StandardCharsets.UTF_8).length <= MAX_PROXY_BYTES);
    }

    CompletionStage<Boolean> publish(UUID id, String payload) {
        if (!available()) {
            return CompletableFuture.completedFuture(false);
        }
        var submission = new Submission(id, payload);
        try {
            writer.execute(submission);
        } catch (RejectedExecutionException full) {
            submission.result.complete(false);
        }
        return submission.result;
    }

    private void tick() {
        if (!available() || System.nanoTime() < retryAt) return;
        try {
            saveAudience();
            if (available()) poll();
        } catch (SQLException | RuntimeException failure) {
            long now = System.nanoTime();
            if (now >= nextWarning && !closed) {
                nextWarning = now + TimeUnit.SECONDS.toNanos(10);
                logger.log(Level.WARNING, "Chat 跨服收件暂缓，将保留游标重试", failure);
            }
            retryAt = now + TimeUnit.MILLISECONDS.toNanos(500);
        }
    }

    private void saveAudience() throws SQLException {
        if (!available()) return;
        if (previousAudience == null) {
            persistedAudience = journal.loadAudience(node);
            previousAudience = persistedAudience;
        }
        Set<UUID> latest = currentAudience;
        if (!cleanedRecoveryAudience) {
            if (withinRejoinWindow() && !latest.containsAll(previousAudience)) {
                var recovering = new java.util.HashSet<>(previousAudience);
                recovering.addAll(latest);
                latest = recovering;
            } else {
                cleanedRecoveryAudience = true;
            }
        }
        if (available() && !latest.equals(persistedAudience)) {
            journal.saveAudience(node, latest);
            persistedAudience = latest;
        }
    }

    private boolean withinRejoinWindow() {
        return System.nanoTime() < reconnectUntil;
    }

    private void poll() throws SQLException {
        long now = System.nanoTime();
        if (!available() || now < nextPull) return;
        if (cursor == null) cursor = journal.openNode(node);
        if (submitted != null) {
            if (!submitted.isDone()) return;
            pending = submitted.join();
            submitted = null;
            if (pending.isEmpty()) {
                nextPull = now + TimeUnit.MILLISECONDS.toNanos(200);
                return;
            }
        }
        if (!pending.isEmpty() && !finishPrefix()) return;

        if (!available()) return;
        if (now >= nextPurge) {
            journal.purgeExpired();
            nextPurge = now + TimeUnit.SECONDS.toNanos(5);
        }
        if (!available()) return;
        List<ChatRelayJournal.Entry> page = journal.pull(cursor);
        if (page.isEmpty()) {
            nextPull = now + TimeUnit.MILLISECONDS.toNanos(200);
            return;
        }
        var admitted = new CompletableFuture<List<Receipt>>();
        onMain.accept(() -> {
            var receipts = new ArrayList<Receipt>(page.size());
            try {
                if (!available()) return;
                for (var entry : page) {
                    if (!available()) break;
                    CompletionStage<Boolean> result = node.equals(entry.origin())
                        ? CompletableFuture.completedFuture(true)
                        : delivery.admit(entry.payload(), entry.origin(), entry.expiresAt() - MESSAGE_MILLIS);
                    if (result == null) break;
                    receipts.add(new Receipt(entry.sequence(), result.toCompletableFuture()));
                }
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "Chat 转发接收回调失败，将从未确认的消息重试", failure);
            } finally {
                admitted.complete(receipts);
                if (available() && !receipts.isEmpty()) {
                    dispatchAdmitted.run();
                    receipts.get(receipts.size() - 1).result().whenComplete((result, failure) -> wakeReceiver());
                }
            }
        });
        submitted = admitted;
    }

    private void wakeReceiver() {
        if (!available()) return;
        try {
            receiver.execute(this::tick);
        } catch (RejectedExecutionException stopped) {
            // The periodic receiver has already been closed.
        }
    }

    private boolean finishPrefix() throws SQLException {
        int count = 0;
        long completed = cursor;
        for (Receipt receipt : pending) {
            if (!receipt.result().isDone()) break;
            if (receipt.result().isCompletedExceptionally() || !Boolean.TRUE.equals(receipt.result().getNow(false))) {
                pending = pending.subList(0, count);
                break;
            }
            completed = receipt.sequence();
            count++;
        }
        if (completed != cursor) {
            if (!available()) return false;
            if (!journal.advance(node, cursor, completed)) {
                cursor = available() ? journal.openNode(node) : null;
                pending = List.of();
                return false;
            }
            cursor = completed;
            pending = pending.subList(count, pending.size());
        }
        return pending.isEmpty();
    }

    @Override
    public void close() {
        closed = true;
        receiver.shutdownNow();
        for (Runnable task : writer.shutdownNow()) {
            ((Submission) task).result.complete(false);
        }
    }

    CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(receiver.stopped().toCompletableFuture(), writer.stopped().toCompletableFuture());
    }

    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private boolean available() {
        return !closed && authorized.getAsBoolean();
    }

    @FunctionalInterface
    interface Delivery {
        CompletionStage<Boolean> admit(String payload, String source, long timestamp);
    }

    private record Receipt(long sequence, CompletableFuture<Boolean> result) {}

    private final class Submission implements Runnable {
        private final UUID id;
        private final String payload;
        private final long expiresAt = System.currentTimeMillis() + MESSAGE_MILLIS;
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();

        private Submission(UUID id, String payload) {
            this.id = id;
            this.payload = payload;
        }

        @Override
        public void run() {
            for (int attempt = 0; attempt < 2 && available(); attempt++) {
                try {
                    result.complete(journal.append(id, node, payload, expiresAt));
                    return;
                } catch (SQLException failure) {
                    if (attempt == 1 && !closed) logger.log(Level.WARNING, "Chat 跨服发件写库失败", failure);
                }
            }
            result.complete(false);
        }
    }
}
