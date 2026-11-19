package xuanmo.arcartxsuite.api.scheduler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 可报告真实退出的模块工作线程池；退出信号不代替业务落盘回执。 */
public final class ModuleExecutor extends ThreadPoolExecutor {
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();

    public ModuleExecutor(int core, int maximum, long keepAlive, TimeUnit unit,
                          BlockingQueue<Runnable> queue, ThreadFactory factory) {
        super(core, maximum, keepAlive, unit, queue, factory);
    }

    public CompletionStage<Void> stopped() { return stopped.copy(); }

    @Override protected void terminated() { stopped.complete(null); }
}
