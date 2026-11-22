package xuanmo.arcartxsuite.api.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/** 带真实退出信号的模块定时后台线程。 */
public final class ModuleScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();

    public ModuleScheduledExecutor(int threads, ThreadFactory factory) {
        super(threads, factory);
        setRemoveOnCancelPolicy(true);
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    public CompletionStage<Void> stopped() { return stopped.copy(); }

    @Override protected void terminated() { stopped.complete(null); }
}
