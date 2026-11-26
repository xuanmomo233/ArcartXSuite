package xuanmo.arcartxsuite.chat.service;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import xuanmo.arcartxsuite.chat.storage.ChatRepository.GuardedRoutes;

/** Performs SQL in submission order and accepts results on the server thread. */
final class ChatDeliveryQueue implements AutoCloseable {
    private static final int MAX_PENDING = 256;
    private static final int MAX_GUARDED_MESSAGES = 32;
    private static final int MAX_GUARDED_RECIPIENTS = 192;
    private static final long MAX_MAIN_START_WAIT_MS = 200;
    private static final long REMOTE_ROUTE_MAX_WAIT = TimeUnit.MILLISECONDS.toNanos(250);
    private final ArrayDeque<Pending<?>> pending = new ArrayDeque<>();
    private final Consumer<Runnable> onMain;
    private final Consumer<Runnable> retryOnMain;
    private final LongSupplier policyVersion;
    private final Consumer<RuntimeException> onDeliveryFailure;
    private final GuardFactory guardFactory;
    private final BooleanSupplier guardedRemoteAllowed;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor worker = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "AXS-Chat-Delivery");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    private boolean guardRunning;

    ChatDeliveryQueue(Consumer<Runnable> onMain, LongSupplier policyVersion) {
        this(onMain, policyVersion, failure -> { throw failure; });
    }

    ChatDeliveryQueue(Consumer<Runnable> onMain, LongSupplier policyVersion, Consumer<RuntimeException> onDeliveryFailure) {
        this(onMain, onMain, policyVersion, onDeliveryFailure);
    }

    ChatDeliveryQueue(Consumer<Runnable> onMain, Consumer<Runnable> retryOnMain, LongSupplier policyVersion,
                      Consumer<RuntimeException> onDeliveryFailure) {
        this(onMain, retryOnMain, policyVersion, onDeliveryFailure, null);
    }

    ChatDeliveryQueue(Consumer<Runnable> onMain, Consumer<Runnable> retryOnMain, LongSupplier policyVersion,
                      Consumer<RuntimeException> onDeliveryFailure, GuardFactory guardFactory) {
        this(onMain, retryOnMain, policyVersion, onDeliveryFailure, guardFactory, () -> true);
    }

    ChatDeliveryQueue(Consumer<Runnable> onMain, Consumer<Runnable> retryOnMain, LongSupplier policyVersion,
                      Consumer<RuntimeException> onDeliveryFailure, GuardFactory guardFactory,
                      BooleanSupplier guardedRemoteAllowed) {
        this.onMain = onMain;
        this.retryOnMain = retryOnMain;
        this.policyVersion = policyVersion;
        this.onDeliveryFailure = onDeliveryFailure;
        this.guardFactory = guardFactory;
        this.guardedRemoteAllowed = guardedRemoteAllowed;
    }

    <T> boolean offer(Query<T> query, BiConsumer<T, SQLException> complete) {
        return offer(query, value -> true, complete);
    }

    synchronized boolean hasRoom() {
        return !closed && pending.size() < MAX_PENDING;
    }

    synchronized <T> boolean offer(Query<T> query, Predicate<T> current, BiConsumer<T, SQLException> complete) {
        return offer(query, current, value -> true, complete);
    }

    synchronized <T> boolean offer(Query<T> query, Predicate<T> current, Predicate<T> dispatchable,
                                   BiConsumer<T, SQLException> complete) {
        return offer(query, current, dispatchable, complete, null);
    }

    <T> boolean offerRemote(Query<T> query, Consumer<T> complete, Runnable abandoned) {
        return offer(() -> new RemoteRoute<>(query.run(), System.nanoTime()),
            route -> System.nanoTime() - route.preparedAt() <= REMOTE_ROUTE_MAX_WAIT,
            route -> true, (route, failure) -> complete.accept(route.value()), abandoned);
    }

    synchronized <T> boolean offerGuardedRemote(Set<UUID> actors, int recipients, GuardedQuery<T> query,
                                               Consumer<T> complete, Runnable abandoned) {
        if (guardFactory == null || !guardedRemoteAllowed.getAsBoolean() || !hasRoom()) return false;
        boolean wasEmpty = pending.isEmpty();
        var guarded = new GuardedTask<>(Set.copyOf(actors), recipients, query);
        pending.addLast(new Pending<>(null, value -> true, value -> true,
            (value, failure) -> complete.accept(value), abandoned, guarded));
        if (wasEmpty) onMain.accept(this::drain);
        return true;
    }

    synchronized void startAdmittedRemoteBatch() {
        Pending<?> head = pending.peekFirst();
        if (!closed && !guardRunning && head != null && head.guarded != null && guardedRemoteAllowed.getAsBoolean()) {
            startGuardedBatch();
        }
    }

    private synchronized <T> boolean offer(Query<T> query, Predicate<T> current, Predicate<T> dispatchable,
                                           BiConsumer<T, SQLException> complete, Runnable abandoned) {
        if (!hasRoom()) return false;
        Pending<T> job = new Pending<>(query, current, dispatchable, complete, abandoned, null);
        pending.addLast(job);
        if (submit(job)) return true;
        pending.removeLast();
        return false;
    }

    private <T> boolean submit(Pending<T> job) {
        job.version = policyVersion.getAsLong();
        job.ready = false;
        try {
            worker.execute(() -> {
                if (closed) return;
                T result = null;
                SQLException error = null;
                try {
                    result = job.query.run();
                } catch (SQLException failure) {
                    error = failure;
                } catch (RuntimeException failure) {
                    error = new SQLException("Failed to prepare chat delivery", failure);
                }
                finish(job, result, error);
            });
            return true;
        } catch (RejectedExecutionException stopped) {
            return false;
        }
    }

    private <T> void finish(Pending<T> job, T value, SQLException failure) {
        if (closed) return;
        onMain.accept(() -> {
            synchronized (this) {
                if (closed) return;
                job.value = value;
                job.failure = failure;
                job.ready = true;
                // A route prepared behind a blocked head may miss a dropped cross-server policy notice.
                job.recheckAtHead = job.abandoned != null && pending.peekFirst() != job;
            }
            drain();
        });
    }

    private void drain() {
        while (true) {
            Pending<?> next;
            synchronized (this) {
                if (closed || guardRunning) return;
                next = pending.peekFirst();
                if (next == null) return;
                if (next.guarded != null) {
                    if (guardedRemoteAllowed.getAsBoolean()) {
                        startGuardedBatch();
                        return;
                    }
                    pending.removeFirst();
                    next.abandon();
                    continue;
                }
                if (!next.ready) return;
                if ((next.failure != null && next.abandoned != null)
                        || (next.failure == null && !next.mayDispatch())) {
                    retryHead(next);
                    return;
                }
                if (next.failure == null && (next.recheckAtHead || next.version != policyVersion.getAsLong()
                        || !next.isCurrent())
                    && submit(next)) return;
                if (next.abandoned == null) pending.removeFirst();
            }
            try {
                next.deliver();
            } catch (RuntimeException failure) {
                if (next.abandoned != null) {
                    synchronized (this) {
                        if (!closed) retryHead(next);
                    }
                    onDeliveryFailure.accept(failure);
                    return;
                }
                onDeliveryFailure.accept(failure);
            }
            if (next.abandoned != null) {
                synchronized (this) {
                    if (pending.peekFirst() == next) pending.removeFirst();
                }
            }
        }
    }

    private void startGuardedBatch() {
        List<Pending<?>> batch = collectGuardedBatch();
        guardRunning = true;
        worker.execute(() -> runGuardedBatches(batch));
    }

    private List<Pending<?>> collectGuardedBatch() {
        List<Pending<?>> batch = new ArrayList<>();
        long recipients = 0;
        for (Pending<?> job : pending) {
            if (job.guarded == null || batch.size() == MAX_GUARDED_MESSAGES
                || (!batch.isEmpty() && recipients + job.guarded.recipients() > MAX_GUARDED_RECIPIENTS)) break;
            batch.add(job);
            recipients += job.guarded.recipients();
        }
        return batch;
    }

    private void runGuardedBatches(List<Pending<?>> batch) {
        boolean retry = true;
        try {
            while (!closed && !batch.isEmpty()) {
                if (!dispatchGuardedBatch(batch)) return;
                synchronized (this) {
                    // Advance to the next remote prefix only after the previous database lease is released.
                    batch = collectGuardedBatch();
                }
            }
            retry = false;
        } catch (SQLException failure) {
            onDeliveryFailure.accept(new IllegalStateException("Failed to prepare guarded remote chat delivery", failure));
        } catch (RuntimeException failure) {
            onDeliveryFailure.accept(failure);
        } finally {
            boolean shouldRetry = retry;
            if (!closed) onMain.accept(() -> finishGuardedBatch(shouldRetry));
        }
    }

    private boolean dispatchGuardedBatch(List<Pending<?>> batch) throws SQLException {
        if (!guardedRemoteAllowed.getAsBoolean()) return false;
        Set<UUID> actors = new HashSet<>();
        for (Pending<?> job : batch) actors.addAll(job.guarded.actors());
        try (GuardedRoutes routes = guardFactory.open(actors)) {
            long version = policyVersion.getAsLong();
            List<PreparedDelivery<?>> prepared = new ArrayList<>(batch.size());
            for (Pending<?> job : batch) prepared.add(job.prepareGuarded(routes));
            var dispatch = new GuardedDispatch(prepared, version);
            onMain.accept(dispatch::deliver);
            return dispatch.await();
        }
    }

    private void finishGuardedBatch(boolean retry) {
        if (closed) return;
        if (retry) {
            retryOnMain.accept(this::resumeAfterGuard);
        } else {
            resumeAfterGuard();
        }
    }

    private void resumeAfterGuard() {
        synchronized (this) {
            guardRunning = false;
        }
        drain();
    }

    /** The worker may cancel a callback before it starts. Once sending begins, the locks hold through its last packet. */
    private final class GuardedDispatch {
        private final List<PreparedDelivery<?>> prepared;
        private final long version;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CompletableFuture<Boolean> finished = new CompletableFuture<>();

        private GuardedDispatch(List<PreparedDelivery<?>> prepared, long version) {
            this.prepared = prepared;
            this.version = version;
        }

        private void deliver() {
            if (!claimed.compareAndSet(false, true)) return;
            boolean completed = false;
            try {
                for (PreparedDelivery<?> delivery : prepared) {
                    synchronized (ChatDeliveryQueue.this) {
                        if (closed || !guardedRemoteAllowed.getAsBoolean() || pending.peekFirst() != delivery.job()
                            || version != policyVersion.getAsLong()) return;
                    }
                    delivery.deliver();
                    synchronized (ChatDeliveryQueue.this) {
                        if (pending.peekFirst() == delivery.job()) pending.removeFirst();
                    }
                }
                completed = true;
            } catch (RuntimeException failure) {
                onDeliveryFailure.accept(failure);
            } finally {
                finished.complete(completed);
            }
        }

        private boolean await() {
            try {
                return finished.get(MAX_MAIN_START_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                return !claimed.compareAndSet(false, true) && finished.join();
            } catch (InterruptedException interrupted) {
                if (!claimed.compareAndSet(false, true)) finished.join();
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException impossible) {
                throw new IllegalStateException(impossible.getCause());
            }
        }
    }

    private void retryHead(Pending<?> head) {
        head.ready = false;
        retryOnMain.accept(() -> retry(head));
    }

    private synchronized void retry(Pending<?> head) {
        if (!closed && pending.peekFirst() == head) submit(head);
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Pending<?> job : pending) job.abandon();
        pending.clear();
        worker.shutdownNow();
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return worker.stopped(); }

    @FunctionalInterface
    interface Query<T> {
        T run() throws SQLException;
    }

    @FunctionalInterface
    interface GuardFactory {
        GuardedRoutes open(Set<UUID> actors) throws SQLException;
    }

    @FunctionalInterface
    interface GuardedQuery<T> {
        T run(GuardedRoutes routes) throws SQLException;
    }

    private record GuardedTask<T>(Set<UUID> actors, int recipients, GuardedQuery<T> query) {}

    private record PreparedDelivery<T>(Pending<T> job, T value) {
        private void deliver() {
            job.complete.accept(value, null);
        }
    }

    private record RemoteRoute<T>(T value, long preparedAt) {}

    private static final class Pending<T> {
        final Query<T> query;
        final Predicate<T> current;
        final Predicate<T> dispatchable;
        final BiConsumer<T, SQLException> complete;
        final Runnable abandoned;
        final GuardedTask<T> guarded;
        long version;
        boolean ready;
        boolean recheckAtHead;
        T value;
        SQLException failure;

        Pending(Query<T> query, Predicate<T> current, Predicate<T> dispatchable, BiConsumer<T, SQLException> complete,
                Runnable abandoned, GuardedTask<T> guarded) {
            this.query = query;
            this.current = current;
            this.dispatchable = dispatchable;
            this.complete = complete;
            this.abandoned = abandoned;
            this.guarded = guarded;
        }

        PreparedDelivery<T> prepareGuarded(GuardedRoutes routes) throws SQLException {
            return new PreparedDelivery<>(this, guarded.query().run(routes));
        }

        void deliver() {
            complete.accept(value, failure);
        }

        boolean isCurrent() {
            return current.test(value);
        }

        boolean mayDispatch() {
            return dispatchable.test(value);
        }


        void abandon() {
            if (abandoned != null) abandoned.run();
        }
    }
}
