package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * tasks.yml 解析：副本内定时任务。
 *
 * <pre>
 * countdown_tips:
 *   type: TIMED                 # 副本开始后第 N 秒各触发一次
 *   times: [30, 60, 90]
 *   script: [...]               # 所有时间点共用（可用 {task_time} 变量）
 *   # 或者按时间分别配置:
 *   timed-scripts:
 *     30: ["message @all 已进行30秒"]
 * periodic_supply:
 *   type: INTERVAL
 *   interval: 120               # 每 120 秒循环
 *   script: [...]
 * on_wave_complete:
 *   type: EVENT
 *   event: wave_complete        # wave_complete | monster_kill | player_death（事件名固定 snake）
 *   script: [...]
 * </pre>
 */
public record TaskConfig(
    Map<String, InstanceTask> tasks
) {

    public enum TaskType { TIMED, INTERVAL, EVENT }

    public record InstanceTask(
        String name,
        TaskType type,
        List<Integer> times,
        int intervalSeconds,
        String event,
        List<String> script,
        Map<Integer, List<String>> timedScripts
    ) {
        static InstanceTask load(String name, ConfigurationSection s) {
            TaskType type;
            try {
                type = TaskType.valueOf(s.getString("type", "TIMED").toUpperCase());
            } catch (IllegalArgumentException e) {
                type = TaskType.TIMED;
            }
            Map<Integer, List<String>> timed = new LinkedHashMap<>();
            ConfigurationSection ts = s.getConfigurationSection("timed-scripts");
            if (ts == null) ts = s.getConfigurationSection("timed_scripts");
            if (ts != null) {
                for (String k : ts.getKeys(false)) {
                    try {
                        timed.put(Integer.parseInt(k), ts.getStringList(k));
                    } catch (NumberFormatException ignored) { }
                }
            }
            return new InstanceTask(
                name,
                type,
                s.getIntegerList("times"),
                s.getInt("interval", s.getInt("interval-seconds", s.getInt("interval_seconds", 60))),
                s.getString("event", null),
                s.getStringList("script"),
                timed
            );
        }
    }

    public static TaskConfig load(ConfigurationSection root) {
        Map<String, InstanceTask> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    map.put(key, InstanceTask.load(key, s));
                }
            }
        }
        return new TaskConfig(map);
    }
}
