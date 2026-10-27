package xuanmo.arcartxsuite.abyss.script;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/**
 * 脚本会话：一段动作队列的执行状态。
 * <p>
 * 顺序执行动作行；{@code wait} 动作把会话挂起（pendingTask），
 * 调度器到点后 resume() 继续。实例结束/取消时 cancel() 终止。
 */
public final class ScriptSession {

    private final String name;
    private final List<String> lines;
    private final ScriptContext context;
    private final ScriptRunner runner;

    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile ScheduledTask pendingTask;

    public ScriptSession(String name, List<String> lines, ScriptContext context, ScriptRunner runner) {
        this.name = name;
        this.lines = lines;
        this.context = context;
        this.runner = runner;
    }

    public String name() { return name; }
    public ScriptContext context() { return context; }
    public boolean isCancelled() { return cancelled.get(); }

    /** 继续执行（主线程）：从 cursor 逐行跑，直到 wait 挂起或跑完。 */
    public void resume() {
        if (cancelled.get()) return;
        int idx;
        while ((idx = cursor.getAndIncrement()) < lines.size()) {
            if (cancelled.get()) return;
            if (context.getInstance().isFinished() && !context.allowsFinishedInstance()) return;
            String line = lines.get(idx);
            if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            boolean suspended = runner.executeLine(line.trim(), context, this);
            if (suspended || cancelled.get()) {
                return; // wait 挂起，等 pendingTask resume
            }
        }
    }

    /** wait 动作调用：登记挂起任务。 */
    public void suspend(ScheduledTask task) {
        this.pendingTask = task;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            ScheduledTask t = pendingTask;
            if (t != null) {
                try { t.cancel(); } catch (Throwable ignored) { }
            }
        }
    }
}
