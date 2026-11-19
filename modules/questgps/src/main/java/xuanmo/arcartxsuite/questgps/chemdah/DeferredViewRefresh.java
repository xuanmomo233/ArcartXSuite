package xuanmo.arcartxsuite.questgps.chemdah;

/** Coalesces reload notifications from any thread into one main-thread rebuild. */
final class DeferredViewRefresh implements AutoCloseable {
    interface Scheduler {
        Runnable schedule(Runnable action);
    }

    private final Scheduler scheduler;
    private final Runnable action;
    private Runnable cancellation;
    private long generation;
    private boolean active;

    DeferredViewRefresh(Scheduler scheduler, Runnable action) {
        this.scheduler = scheduler;
        this.action = action;
    }

    synchronized void start() {
        if (!active) {
            active = true;
            generation++;
        }
    }

    synchronized void request() {
        if (!active || cancellation != null) return;
        long scheduledGeneration = generation;
        cancellation = scheduler.schedule(() -> {
            synchronized (this) {
                if (!active || scheduledGeneration != generation) return;
                cancellation = null;
            }
            action.run();
        });
    }

    @Override
    public synchronized void close() {
        active = false;
        generation++;
        if (cancellation != null) cancellation.run();
        cancellation = null;
    }
}
