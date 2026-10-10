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
 *   auto-create: true
 *   trigger-mode: NONE | ENTER | LEAVE | PERSIST
 *   trigger-script: ["message @trigger &c大门紧闭！"]
 *   trigger-cooldown: 5
 *   trigger-once: false
 *   on-create: [...]
 *   on-delete: [...]
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
                    s.getString("trigger-mode", s.getString("trigger_mode", "NONE")).toUpperCase());
            } catch (IllegalArgumentException e) {
                mode = TriggerMode.NONE;
            }
            return new Obstacle(
                name,
                s.getStringList("blocks"),
                s.getBoolean("auto-create", s.getBoolean("auto_create", true)),
                mode,
                s.getStringList("trigger-script").isEmpty()
                    ? s.getStringList("trigger_script") : s.getStringList("trigger-script"),
                s.getInt("trigger-cooldown", s.getInt("trigger_cooldown", 0)),
                s.getBoolean("trigger-once", s.getBoolean("trigger_once", false)),
                s.getStringList("on-create").isEmpty()
                    ? s.getStringList("on_create") : s.getStringList("on-create"),
                s.getStringList("on-delete").isEmpty()
                    ? s.getStringList("on_delete") : s.getStringList("on-delete")
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
