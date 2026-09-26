package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * obstacles.yml 解析：障碍物（动态方块 + 触发器）。
 *
 * <pre>
 * boss_gate:
 *   blocks:
 *     - "10,64,0,IRON_BLOCK"
 *     - "10,65,0,IRON_BLOCK"
 *   auto_create: true
 *   trigger_mode: NONE | ENTER | LEAVE | PERSIST
 *   trigger_script: ["message @trigger &c大门紧闭！"]
 *   trigger_cooldown: 5
 *   trigger_once: false
 *   on_create: [...]
 *   on_delete: [...]
 * </pre>
 */
public record ObstacleConfig(
    Map<String, Obstacle> obstacles
) {

    public enum TriggerMode { NONE, ENTER, LEAVE, PERSIST }

    public record Obstacle(
        String name,
        List<String> blocks,
        boolean autoCreate,
        TriggerMode triggerMode,
        List<String> triggerScript,
        int triggerCooldown,
        boolean triggerOnce,
        List<String> onCreate,
        List<String> onDelete
    ) {
        static Obstacle load(String name, ConfigurationSection s) {
            TriggerMode mode;
            try {
                mode = TriggerMode.valueOf(
                    s.getString("trigger_mode", s.getString("trigger-mode", "NONE")).toUpperCase());
            } catch (IllegalArgumentException e) {
                mode = TriggerMode.NONE;
            }
            return new Obstacle(
                name,
                s.getStringList("blocks"),
                s.getBoolean("auto_create", s.getBoolean("auto-create", true)),
                mode,
                s.getStringList("trigger_script").isEmpty()
                    ? s.getStringList("trigger-script") : s.getStringList("trigger_script"),
                s.getInt("trigger_cooldown", s.getInt("trigger-cooldown", 0)),
                s.getBoolean("trigger_once", s.getBoolean("trigger-once", false)),
                s.getStringList("on_create").isEmpty()
                    ? s.getStringList("on-create") : s.getStringList("on_create"),
                s.getStringList("on_delete").isEmpty()
                    ? s.getStringList("on-delete") : s.getStringList("on_delete")
            );
        }
    }

    public static ObstacleConfig load(ConfigurationSection root) {
        Map<String, Obstacle> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    map.put(key, Obstacle.load(key, s));
                }
            }
        }
        return new ObstacleConfig(map);
    }
}
