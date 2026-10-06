package xuanmo.arcartxsuite.abyss.task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.TaskConfig;
import xuanmo.arcartxsuite.abyss.template.TaskConfig.InstanceTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/**
 * 实例定时任务：TIMED / INTERVAL / EVENT 三类。
 * <p>
 * TIMED：副本 start 后第 N 秒触发（timed_scripts 优先，否则共用 script）。
 * INTERVAL：固定间隔循环。
 * EVENT：监听事件名（wave_complete / monster_kill / player_death）由外部触发。
 */
public final class InstanceTaskScheduler {

    private final Map<UUID, Map<String, ScheduledTask>> running = new ConcurrentHashMap<>();
    private final SchedulerAPI scheduler;
    private final Plugin plugin;
    private final Logger logger;

    /** 回调：执行一段脚本。 */
    private BiConsumer<AbyssInstance, TaskFire> fireCallback;

    public record TaskFire(String taskName, java.util.List<String> script,
                           Map<String, String> vars) { }

    public InstanceTaskScheduler(Plugin plugin, SchedulerAPI scheduler, Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setFireCallback(BiConsumer<AbyssInstance, TaskFire> cb) {
        this.fireCallback = cb;
    }

    /** 副本 start：注册 TIMED + INTERVAL 任务（EVENT 类型等外部调用）。 */
    public void initInstance(AbyssInstance instance) {
        Map<String, ScheduledTask> map = running.computeIfAbsent(
            instance.getInstanceId(), k -> new ConcurrentHashMap<>());
        for (InstanceTask t : instance.getTemplate().tasks().tasks().values()) {
            switch (t.type()) {
                case TIMED -> {
                    for (int sec : t.times()) {
                        java.util.List<String> script = t.timedScripts().getOrDefault(sec, t.script());
                        if (script.isEmpty()) continue;
                        ScheduledTask h = scheduler.scheduleOnceSeconds(plugin,
                            () -> fire(instance, new TaskFire(t.name(), script,
                                Map.of("task_time", String.valueOf(sec)))),
                            sec);
                        map.put(t.name() + ":" + sec, h);
                        instance.registerTask(h);
                    }
                }
                case INTERVAL -> {
                    if (t.script().isEmpty() || t.intervalSeconds() <= 0) continue;
                    ScheduledTask h = scheduler.scheduleEverySeconds(plugin,
                        () -> fire(instance, new TaskFire(t.name(), t.script(), Map.of())),
                        t.intervalSeconds(), t.intervalSeconds());
                    map.put(t.name(), h);
                    instance.registerTask(h);
                }
                case EVENT -> { /* 等外部 fireEvent */ }
            }
        }
    }

    /** 外部事件触发（怪物组完成/击杀/玩家死亡时调用）。 */
    public void fireEvent(AbyssInstance instance, String event, Map<String, String> vars) {
        for (InstanceTask t : instance.getTemplate().tasks().tasks().values()) {
            if (t.type() == TaskConfig.TaskType.EVENT
                && event.equalsIgnoreCase(t.event() != null ? t.event() : "")
                && !t.script().isEmpty()) {
                fire(instance, new TaskFire(t.name(), t.script(), vars));
            }
        }
    }

    private void fire(AbyssInstance instance, TaskFire f) {
        if (fireCallback != null && instance != null && !instance.isFinished()) {
            fireCallback.accept(instance, f);
        }
    }

    public void cleanup(AbyssInstance instance) {
        Map<String, ScheduledTask> map = running.remove(instance.getInstanceId());
        if (map != null) {
            map.values().forEach(t -> {
                try { t.cancel(); } catch (Throwable ignored) { }
            });
        }
    }
}
