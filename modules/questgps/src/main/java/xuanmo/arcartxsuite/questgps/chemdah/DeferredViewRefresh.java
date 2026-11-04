package xuanmo.arcartxsuite.questgps.chemdah;

/** Main-thread reload coalescing with generation checks for callbacks queued before shutdown. */
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

    void start() {
        if (!active) {
            active = true;
            generation++;
        }
    }

    void request() {
        if (!active || cancellation != null) return;
        long scheduledGeneration = generation;
        cancellation = scheduler.schedule(() -> {
            if (!active || scheduledGeneration != generation) return;
            cancellation = null;
            action.run();
        });
    }

    @Override
    public void close() {
        active = false;
        generation++;
        if (cancellation != null) cancellation.run();
        cancellation = null;
    }
}
