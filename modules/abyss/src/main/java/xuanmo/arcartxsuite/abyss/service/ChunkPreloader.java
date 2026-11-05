package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/** One service-wide, round-robin chunk dispatch budget shared by every preparing instance. */
final class ChunkPreloader implements AutoCloseable {
    private static final class Job {
        final List<int[]> chunks;
        final Function<int[], CompletableFuture<?>> loader;
        final BooleanSupplier valid;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        int next, pending;
        Job(List<int[]> chunks, Function<int[], CompletableFuture<?>> loader, BooleanSupplier valid) {
            this.chunks = List.copyOf(chunks);
            this.loader = loader;
            this.valid = valid;
        }
    }
    private record Completion(Job job, Throwable failure) { }
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Completion> completed = new ConcurrentLinkedQueue<>();
    private final SchedulerAPI scheduler;
    private final Plugin plugin;
    private final int perTick, parallel;
    private ScheduledTask timer;
    private int inFlight;
    private volatile boolean closed;

    ChunkPreloader(SchedulerAPI scheduler, Plugin plugin, int perTick, int parallel) {
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.perTick = perTick;
        this.parallel = parallel;
    }

    CompletableFuture<Void> submit(List<int[]> chunks, Function<int[], CompletableFuture<?>> loader, BooleanSupplier valid) {
        if (closed || jobs.size() >= 64) return CompletableFuture.failedFuture(
            new IllegalStateException(closed ? "Chunk loader stopped" : "Chunk preparation queue full"));
        if (chunks.isEmpty()) return CompletableFuture.completedFuture(null);
        var job = new Job(chunks, loader, valid);
        jobs.addLast(job);
        if (timer == null) {
            try { timer = scheduler.scheduleAtFixedRate(plugin, this::tick, 1L, 1L); }
            catch (RuntimeException failure) { jobs.remove(job); job.result.completeExceptionally(failure); }
        }
        return job.result;
    }

    void tick() {
        if (closed) return;
        Completion completion;
        while ((completion = completed.poll()) != null) {
            inFlight--;
            completion.job().pending--;
            if (completion.failure() != null) completion.job().result.completeExceptionally(completion.failure());
        }
        var ready = new java.util.ArrayList<Job>();
        var cancelled = new java.util.ArrayList<Job>();
        int pendingJobs = jobs.size();
        while (pendingJobs-- > 0) {
            var job = jobs.removeFirst();
            if (job.result.isDone()) continue;
            if (!job.valid.getAsBoolean()) cancelled.add(job);
            else if (job.next == job.chunks.size() && job.pending == 0) ready.add(job);
            else jobs.addLast(job);
        }
        cancelled.forEach(job -> job.result.completeExceptionally(new IllegalStateException("Instance preparation cancelled")));
        ready.forEach(job -> job.result.complete(null));
        long deadline = System.nanoTime() + 4_000_000L;
        int submitted = 0, skipped = 0;
        while (!jobs.isEmpty() && inFlight < parallel && submitted < perTick && System.nanoTime() < deadline) {
            var job = jobs.removeFirst();
            jobs.addLast(job);
            if (job.next >= job.chunks.size()) {
                if (++skipped >= jobs.size()) break;
                continue;
            }
            skipped = 0;
            if (!job.valid.getAsBoolean()) {
                job.result.completeExceptionally(new IllegalStateException("Instance preparation cancelled"));
                jobs.remove(job);
                continue;
            }
            submitted++;
            inFlight++;
            job.pending++;
            try {
                job.loader.apply(job.chunks.get(job.next++)).whenComplete((ignored, error) -> {
                    synchronized (this) {
                        if (!closed) completed.add(new Completion(job, error));
                    }
                });
            } catch (RuntimeException failure) {
                completed.add(new Completion(job, failure));
            }
        }
        if (jobs.isEmpty() && inFlight == 0 && timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override public synchronized void close() {
        closed = true;
        if (timer != null) timer.cancel();
        for (var job : jobs) job.result.completeExceptionally(new IllegalStateException("Chunk loader stopped"));
        jobs.clear();
        completed.clear();
        timer = null;
    }
}
