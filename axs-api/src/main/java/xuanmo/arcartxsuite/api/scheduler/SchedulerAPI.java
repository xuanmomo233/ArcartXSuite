package xuanmo.arcartxsuite.api.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 宿主统一调度 API，封装固定间隔调度、日历触发调度、跨服单点调度。
 * <p>
 * 替代直接使用 {@code Bukkit.getScheduler()}，提供更高层的抽象：
 * <ul>
 *   <li>固定间隔调度（tick/秒双单位）—— 封装 Bukkit Scheduler</li>
 *   <li>日历触发调度（{@link ScheduleSpec}）—— 支持整点/每日/每周/固定间隔四种模式</li>
 *   <li>跨服单点调度（{@code scheduleCalendarLeaderOnly}）—— 多服中仅一个节点执行，宕机自动接管</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 每整点触发（如神秘商人刷新）
 * scheduler.scheduleCalendar(plugin, () -> checkMysteryMerchant(), ScheduleSpec.hourly());
 *
 * // 每天 8:00 重置签到
 * scheduler.scheduleCalendar(plugin, () -> dailyReset(), ScheduleSpec.dailyAt(8, 0));
 *
 * // 每 30 秒同步（替代手算 600 tick）
 * scheduler.scheduleEverySeconds(plugin, () -> sync(), 30, 30);
 *
 * // 跨服单点：每整点只在一个服执行
 * scheduler.scheduleCalendarLeaderOnly("market:mystery", plugin,
 *     () -> triggerMysteryMerchant(), ScheduleSpec.hourly());
 * }</pre>
 *
 * @since 1.6.0
 */
@ApiStability.Stable
public interface SchedulerAPI {

    // ── 固定间隔调度（封装 Bukkit Scheduler）──────────────────

    /**
     * 固定速率同步任务（tick 单位）。
     *
     * @param plugin       宿主插件实例
     * @param task         要执行的任务
     * @param delayTicks   首次延迟 tick 数
     * @param periodTicks  循环间隔 tick 数
     * @return 任务句柄
     */
    @NotNull ScheduledTask scheduleAtFixedRate(@NotNull Plugin plugin, @NotNull Runnable task,
                                               long delayTicks, long periodTicks);

    /**
     * 固定速率异步任务（tick 单位）。
     *
     * @param plugin       宿主插件实例
     * @param task         要执行的任务
     * @param delayTicks   首次延迟 tick 数
     * @param periodTicks  循环间隔 tick 数
     * @return 任务句柄
     */
    @NotNull ScheduledTask scheduleAsyncAtFixedRate(@NotNull Plugin plugin, @NotNull Runnable task,
                                                    long delayTicks, long periodTicks);

    /**
     * 固定速率同步任务（秒单位，内部 ×20 换算 tick）。
     */
    default @NotNull ScheduledTask scheduleEverySeconds(@NotNull Plugin plugin, @NotNull Runnable task,
                                                        long delaySeconds, long periodSeconds) {
        return scheduleAtFixedRate(plugin, task, delaySeconds * 20L, periodSeconds * 20L);
    }

    /**
     * 固定速率异步任务（秒单位，内部 ×20 换算 tick）。
     */
    default @NotNull ScheduledTask scheduleAsyncEverySeconds(@NotNull Plugin plugin, @NotNull Runnable task,
                                                             long delaySeconds, long periodSeconds) {
        return scheduleAsyncAtFixedRate(plugin, task, delaySeconds * 20L, periodSeconds * 20L);
    }

    // ── 一次性延迟调度 ──────────────────────────────────────

    /**
     * 一次性延迟同步任务（tick 单位，触发一次即完成）。
     * <p>
     * <b>禁止用 {@code scheduleAtFixedRate(task, delay, Long.MAX_VALUE)} 模拟一次性</b>：
     * period≈MAX_VALUE 会使调度器下次执行时间 long 溢出为负 → 首触发后每 tick 重跑
     * （abyss 曾因此聊天刷屏/全团重复传送）。统一用本方法。
     *
     * @param plugin     宿主插件实例
     * @param task       要执行的任务（在主线程执行一次）
     * @param delayTicks 延迟 tick 数
     * @return 任务句柄（取消语义同周期任务）
     */
    @NotNull default ScheduledTask scheduleOnce(@NotNull Plugin plugin, @NotNull Runnable task,
                                                long delayTicks) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = scheduleAtFixedRate(plugin, () -> {
            ScheduledTask h = holder[0];
            if (h != null) {
                try { h.cancel(); } catch (Throwable ignored) { }
            }
            task.run();
        }, delayTicks, Long.MAX_VALUE);
        return holder[0];
    }

    /**
     * 一次性延迟异步任务（tick 单位，触发一次即完成）。
     */
    @NotNull default ScheduledTask scheduleOnceAsync(@NotNull Plugin plugin, @NotNull Runnable task,
                                                     long delayTicks) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = scheduleAsyncAtFixedRate(plugin, () -> {
            ScheduledTask h = holder[0];
            if (h != null) {
                try { h.cancel(); } catch (Throwable ignored) { }
            }
            task.run();
        }, delayTicks, Long.MAX_VALUE);
        return holder[0];
    }

    /**
     * 一次性延迟同步任务（秒单位，内部 ×20 换算 tick）。
     */
    default @NotNull ScheduledTask scheduleOnceSeconds(@NotNull Plugin plugin, @NotNull Runnable task,
                                                       long delaySeconds) {
        return scheduleOnce(plugin, task, delaySeconds * 20L);
    }

    // ── 日历触发调度 ──────────────────────────────────────────

    /**
     * 按日历规格触发同步任务。
     * <p>
     * HOURLY/DAILY/WEEKLY 模式按服务器时区在指定时刻触发；
     * INTERVAL 模式退化为固定间隔调度。
     *
     * @param plugin 宿主插件实例
     * @param task   要执行的任务（在主线程执行）
     * @param spec   调度规格
     * @return 任务句柄
     */
    @NotNull ScheduledTask scheduleCalendar(@NotNull Plugin plugin, @NotNull Runnable task,
                                            @NotNull ScheduleSpec spec);

    /**
     * 按日历规格触发异步任务。
     *
     * @param plugin 宿主插件实例
     * @param task   要执行的任务（在异步线程执行）
     * @param spec   调度规格
     * @return 任务句柄
     */
    @NotNull ScheduledTask scheduleCalendarAsync(@NotNull Plugin plugin, @NotNull Runnable task,
                                                 @NotNull ScheduleSpec spec);

    // ── 跨服单点调度 ──────────────────────────────────────────

    /**
     * 注册一个跨服单点定时任务：在所有子服中仅一个节点执行，该节点宕机后其他节点自动接管。
     * <p>
     * 单服环境下退化为普通定时任务（本节点即主）。
     * <p>
     * 选主机制：Redis 可用时用 Redis 分布式锁；不可用时用数据库租约表。
     * 选主状态对调用方透明，调用方只需关注 task 本身。
     *
     * @param taskKey 任务唯一键（跨服共享，相同 key 的任务只在一个节点执行，建议格式 {@code <moduleId>:<taskName>}）
     * @param plugin  宿主插件实例
     * @param task    要执行的任务（仅在主节点执行，在主线程执行）
     * @param spec    调度规格
     * @return 任务句柄
     */
    @NotNull ScheduledTask scheduleCalendarLeaderOnly(@NotNull String taskKey, @NotNull Plugin plugin,
                                                      @NotNull Runnable task, @NotNull ScheduleSpec spec);
}
