package xuanmo.arcartxsuite.api.command;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 节流控制台命令执行器：把奖励/结算类命令放入队列，每 tick 最多执行固定条数，
 * 避免高频发奖（如全服结算、批量签到）在主线程形成瞬时尖峰。
 * <p>
 * 命令仍由 Bukkit 调度器在主线程执行（Bukkit API 要求），本类只做削峰排队。
 * 由宿主在 onEnable 调用 {@link #init(Plugin, int)} 配置队列，有待执行命令时调度，
 * onDisable 调用 {@link #shutdown()}。
 * <p>
 * 注意：入队命令是异步完成的，{@link #dispatch} 不返回执行结果；
 * 命令失败仅在控制台记日志。需要即时执行结果的管理命令请勿走此队列。
 * <p>
 * 队列上限 {@link #MAX_PENDING}，超出后丢弃并告警（防恶意堆积撑爆内存）。
 */
public final class ThrottledCommandExecutor {
    public enum Result { SUCCESS, NOT_EXECUTED, UNCONFIRMED }

    /** 队列最大堆积量。 */
    private static final int MAX_PENDING = 10_000;

    private static final Object LOCK = new Object();
    private static Settings settings;
    private static Drain activeDrain;
    private static boolean stopped;

    private ThrottledCommandExecutor() {}

    /**
     * 配置按需执行的命令队列。重新初始化会取消上一次仍在等待的命令。
     *
     * @param plugin          宿主插件实例
     * @param commandsPerTick 每 tick 最多执行的命令条数；&lt;=0 表示关闭节流
     *                        （{@link #dispatch} 退化为调用方线程直接执行）
     */
    public static void init(Plugin plugin, int commandsPerTick) {
        Settings next = commandsPerTick <= 0 ? null : new Settings(Objects.requireNonNull(plugin), commandsPerTick);
        replace(next, false);
    }

    /** 停止并拒绝新命令，直至下次初始化。等待中的命令返回失败回执。 */
    public static void shutdown() {
        replace(null, true);
    }

    private static void replace(Settings next, boolean retiring) {
        Drain previous;
        synchronized (LOCK) {
            previous = activeDrain;
            activeDrain = null;
            settings = next;
            stopped = retiring;
        }
        if (previous == null) return;
        try {
            previous.task.cancel();
        } finally {
            QueuedCommand command;
            while ((command = previous.queue.pollFirst()) != null) complete(command.completion(), Result.NOT_EXECUTED);
        }
    }

    /**
     * 入队一条控制台命令（任意线程可调用；实际在排水任务的主线程节拍中执行）。
     * 节流关闭（per-tick&lt;=0 或未 init）时在调用方线程直接执行——调用方约定为主线程。
     *
     * @param command 命令文本（不含前导 {@code /}）
     * @return {@code true} 表示已受理（入队成功或直发完成）；队列满丢弃/直发异常返回 {@code false}
     */
    public static boolean dispatch(String command) {
        return dispatchIf(command, null);
    }

    /** A queued command checks its optional guard on the dispatch thread immediately before execution. */
    public static boolean dispatchIf(String command, BooleanSupplier permitted) {
        return dispatch(new QueuedCommand(command, permitted, null));
    }

    /** Reports the Bukkit command result once after execution, rejection, or queue shutdown. */
    public static void dispatch(String command, Consumer<Boolean> completion) {
        dispatchWithResult(command, null, completion == null ? null : result -> completion.accept(result == Result.SUCCESS));
    }

    /** Distinguishes refusal before Bukkit dispatch from an attempted command whose effects are unconfirmed. */
    public static void dispatchWithResult(String command, BooleanSupplier permitted, Consumer<Result> completion) {
        dispatch(new QueuedCommand(command, permitted, completion));
    }

    private static boolean dispatch(QueuedCommand command) {
        if (command.text() != null && !command.text().isBlank()) {
            Admission admission;
            try {
                admission = admit(command);
            } catch (RuntimeException | LinkageError failure) {
                Bukkit.getLogger().warning("[AXS] 命令节流任务调度失败: " + failure.getMessage());
                admission = Admission.STOPPED;
            }
            if (admission == Admission.QUEUED) return true;
            if (admission == Admission.DIRECT) return execute(command);
            if (admission == Admission.FULL) {
                Bukkit.getLogger().warning("[AXS] 命令节流队列已满(" + MAX_PENDING + ")，丢弃命令: " + command.text());
            }
        }
        complete(command.completion(), Result.NOT_EXECUTED);
        return false;
    }

    private static Admission admit(QueuedCommand command) {
        synchronized (LOCK) {
            if (stopped) return Admission.STOPPED;
            if (settings == null) return Admission.DIRECT;
            if (activeDrain == null) {
                var next = new Drain(settings.perTick());
                next.task = Objects.requireNonNull(Bukkit.getScheduler().runTaskTimer(settings.plugin(), next, 1L, 1L));
                activeDrain = next;
            } else if (activeDrain.queue.size() >= MAX_PENDING) {
                return Admission.FULL;
            }
            activeDrain.queue.addLast(command);
            return Admission.QUEUED;
        }
    }

    /** 当前队列堆积量（诊断用）。 */
    public static int pendingCount() {
        synchronized (LOCK) {
            return activeDrain == null ? 0 : activeDrain.queue.size();
        }
    }

    private static final class Drain implements Runnable {
        private final ArrayDeque<QueuedCommand> queue = new ArrayDeque<>();
        private final int perTick;
        private BukkitTask task;

        private Drain(int perTick) {
            this.perTick = perTick;
        }

        @Override public void run() {
            try {
                for (int i = 0; i < perTick; i++) {
                    QueuedCommand command;
                    synchronized (LOCK) {
                        if (activeDrain != this) return;
                        command = queue.pollFirst();
                    }
                    if (command == null) break;
                    execute(command);
                }
            } finally {
                stopIfIdle();
            }
        }

        private void stopIfIdle() {
            synchronized (LOCK) {
                if (activeDrain != this || !queue.isEmpty()) return;
                activeDrain = null;
            }
            task.cancel();
        }
    }

    private static boolean execute(QueuedCommand command) {
        Result result = Result.NOT_EXECUTED;
        try {
            if (command.permitted() == null || command.permitted().getAsBoolean()) {
                result = Result.UNCONFIRMED;
                if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.text())) result = Result.SUCCESS;
                return true;
            }
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[AXS] 节流命令执行失败 [" + command.text() + "]: " + e.getMessage());
        } finally {
            complete(command.completion(), result);
        }
        return false;
    }

    private static void complete(Consumer<Result> completion, Result result) {
        if (completion == null) return;
        try {
            completion.accept(result);
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[AXS] 命令结果回调失败: " + e.getMessage());
        }
    }

    private record QueuedCommand(String text, BooleanSupplier permitted, Consumer<Result> completion) {}
    private record Settings(Plugin plugin, int perTick) {}
    private enum Admission { QUEUED, DIRECT, FULL, STOPPED }
}
