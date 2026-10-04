package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * monsters.yml 解析：怪物组定义。
 *
 * <pre>
 * wave_1:
 *   spawn-timing: AUTO_START | DELAYED | TRIGGERED | MANUAL | SCRIPTED
 *   delay-seconds: 10              # DELAYED 用
 *   trigger-after: ["wave_1"]      # TRIGGERED 用：指定组完成后触发
 *   monsters:
 *     - mob-id: SkeletonKnight
 *       location: "0,64,0"
 *       amount: 5
 *       level: 1
 *   complete-when-alive-below: 0   # 存活数 <= 该值时组完成
 *   on-start: ["message @all &e第一波来袭！"]
 *   on-end: ["spawn_group wave_2"]
 * </pre>
 */
public record MonsterConfig(
    Map<String, MonsterGroup> groups
) {

    public enum SpawnTiming { AUTO_START, DELAYED, TRIGGERED, MANUAL, SCRIPTED }

    public record MonsterSpawn(
        String mobId,
        String location,
        int amount,
        double level
    ) {
        static MonsterSpawn load(ConfigurationSection s) {
            return new MonsterSpawn(
                s.getString("mob-id", s.getString("mob_id", s.getString("id", ""))),
                s.getString("location", "0,64,0"),
                s.getInt("amount", 1),
                s.getDouble("level", 1.0)
            );
        }
    }

    public record MonsterGroup(
        String name,
        boolean autoStart,
        SpawnTiming spawnTiming,
        int delaySeconds,
        int restSeconds,        // 休整秒数（-1=未配）：TRIGGERED 触发时若 delay-seconds<=0 用此值
        List<String> triggerAfter,
        List<MonsterSpawn> monsters,
        int completeWhenAliveBelow,
        List<String> onPrespawn, // 预告段：触发后延迟期内跑（"下一波来袭"title/音效）
        List<String> onStart,
        List<String> onEnd,
        List<String> plans
    ) {
        static MonsterGroup load(String name, ConfigurationSection s) {
            SpawnTiming timing;
            try {
                timing = SpawnTiming.valueOf(
                    s.getString("spawn-timing", s.getString("spawn_timing",
                        s.getBoolean("auto-start", s.getBoolean("auto_start", false))
                            ? "AUTO_START" : "MANUAL")).toUpperCase());
            } catch (IllegalArgumentException e) {
                timing = SpawnTiming.MANUAL;
            }
            List<MonsterSpawn> spawns = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("monsters")) {
                org.bukkit.configuration.MemoryConfiguration mem =
                    new org.bukkit.configuration.MemoryConfiguration();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) mem.set(String.valueOf(e.getKey()), e.getValue());
                }
                spawns.add(MonsterSpawn.load(mem));
            }
            return new MonsterGroup(
                name,
                s.getBoolean("auto-start", s.getBoolean("auto_start", timing == SpawnTiming.AUTO_START)),
                timing,
                s.getInt("delay-seconds", s.getInt("delay_seconds", 0)),
                firstNonNeg(s.getInt("rest-seconds", s.getInt("rest_seconds",
                    s.getInt("wave-rest", s.getInt("wave_rest", -1))))),
                s.getStringList("trigger-after").isEmpty()
                    ? s.getStringList("trigger_after") : s.getStringList("trigger-after"),
                spawns,
                s.getInt("complete-when-alive-below",
                    s.getInt("complete_when_alive_below", 0)),
                firstNonEmpty(s, "on-prespawn", "pre-spawn", "on_prespawn"),
                s.getStringList("on-start").isEmpty()
                    ? s.getStringList("on_start") : s.getStringList("on-start"),
                s.getStringList("on-end").isEmpty()
                    ? s.getStringList("on_end") : s.getStringList("on-end"),
                // plans：限定作战方案的组（空=不限）；plan 单值是便捷写法
                s.getStringList("plans").isEmpty()
                    ? (s.getString("plan") != null && !s.getString("plan").isBlank()
                        ? List.of(s.getString("plan").trim()) : List.of())
                    : s.getStringList("plans")
            );
        }
    }

    /** 多键取首个非空字符串列表。 */
    private static List<String> firstNonEmpty(ConfigurationSection s, String... keys) {
        for (String k : keys) {
            List<String> v = s.getStringList(k);
            if (!v.isEmpty()) return v;
        }
        return List.of();
    }

    /** 负值归一为 -1（未配语义），其余原样。 */
    private static int firstNonNeg(int v) {
        return Math.max(v, -1);
    }

    public static MonsterConfig load(ConfigurationSection root) {
        Map<String, MonsterGroup> groups = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    groups.put(key, MonsterGroup.load(key, s));
                }
            }
        }
        return new MonsterConfig(groups);
    }
}
