package xuanmo.arcartxsuite.api.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 跟踪外部调度器已经开始的回调；关闭后拒绝新调用，不等待现有调用。 */
public final class ModuleCallback implements Runnable, AutoCloseable {
    private final Runnable action;
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private boolean closed;
    private int running;

    public ModuleCallback(Runnable action) { this.action = action; }

    @Override public void run() {
        synchronized (this) {
            if (closed) return;
            running++;
        }
        try { action.run(); }
        finally {
            boolean complete;
            synchronized (this) { complete = --running == 0 && closed; }
            if (complete) stopped.complete(null);
        }
    }

    @Override public void close() {
        boolean complete;
        synchronized (this) { closed = true; complete = running == 0; }
        if (complete) stopped.complete(null);
    }

    /** Tracks a synchronous capability call without allocating a future per invocation. */
    public <T> T call(java.util.function.Supplier<T> operation, T unavailable) {
        synchronized (this) {
            if (closed) return unavailable;
            running++;
        }
        try { return operation.get(); }
        finally {
            boolean complete;
            synchronized (this) { complete = --running == 0 && closed; }
            if (complete) stopped.complete(null);
        }
    }

    public CompletionStage<Void> stopped() { return stopped.copy(); }
}
