package xuanmo.arcartxsuite.api.command;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 节流控制台命令执行器：把奖励/结算类命令放入队列，每 tick 最多执行固定条数，
 * 避免高频发奖（如全服结算、批量签到）在主线程形成瞬时尖峰。
 * <p>
 * 命令仍由 Bukkit 调度器在主线程执行（Bukkit API 要求），本类只做削峰排队。
 * 由宿主在 onEnable 调用 {@link #init(Plugin, int)} 启动排水任务，
 * onDisable 调用 {@link #shutdown()}。
 * <p>
 * 注意：入队命令是异步完成的，{@link #dispatch} 不返回执行结果；
 * 命令失败仅在控制台记日志。需要即时执行结果的管理命令请勿走此队列。
 * <p>
 * 队列上限 {@link #MAX_PENDING}，超出后丢弃并告警（防恶意堆积撑爆内存）。
 */
public final class ThrottledCommandExecutor {

    /** 队列最大堆积量。 */
    private static final int MAX_PENDING = 10_000;

    private static final Queue<String> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile int perTick = 20;
    private static volatile BukkitTask drainTask;

    private ThrottledCommandExecutor() {}

    /**
     * 启动排水任务（宿主 onEnable 调用，幂等——重复调用先停旧任务）。
     *
     * @param plugin          宿主插件实例
     * @param commandsPerTick 每 tick 最多执行的命令条数；&lt;=0 表示关闭节流
     *                        （{@link #dispatch} 退化为调用方线程直接执行）
     */
    public static synchronized void init(Plugin plugin, int commandsPerTick) {
        shutdown();
        if (commandsPerTick <= 0) {
            return;
        }
        perTick = commandsPerTick;
        drainTask = Bukkit.getScheduler().runTaskTimer(plugin, ThrottledCommandExecutor::drain, 1L, 1L);
    }

    /** 停止排水任务并清空队列（宿主 onDisable / reload 调用）。 */
    public static synchronized void shutdown() {
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        QUEUE.clear();
    }

    /**
     * 入队一条控制台命令（任意线程可调用；实际在排水任务的主线程节拍中执行）。
     * 节流关闭（per-tick&lt;=0 或未 init）时在调用方线程直接执行——调用方约定为主线程。
     *
     * @param command 命令文本（不含前导 {@code /}）
     * @return {@code true} 表示已受理（入队成功或直发完成）；队列满丢弃/直发异常返回 {@code false}
     */
    public static boolean dispatch(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        if (drainTask == null) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                return true;
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[AXS] 命令执行失败 [" + command + "]: " + e.getMessage());
                return false;
            }
        }
        if (QUEUE.size() >= MAX_PENDING) {
            Bukkit.getLogger().warning("[AXS] 命令节流队列已满(" + MAX_PENDING + ")，丢弃命令: " + command);
            return false;
        }
        QUEUE.offer(command);
        return true;
    }

    /** 当前队列堆积量（诊断用）。 */
    public static int pendingCount() {
        return QUEUE.size();
    }

    private static void drain() {
        for (int i = 0; i < perTick; i++) {
            String command = QUEUE.poll();
            if (command == null) {
                return;
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[AXS] 节流命令执行失败 [" + command + "]: " + e.getMessage());
            }
        }
    }
}
