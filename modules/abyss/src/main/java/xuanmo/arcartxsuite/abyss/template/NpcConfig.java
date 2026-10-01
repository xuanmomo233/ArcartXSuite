package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * npcs.yml 解析：副本 NPC。
 *
 * <pre>
 * guide:
 *   name: "&e向导·艾琳"
 *   type: PLAYER | VILLAGER | ZOMBIE | SKELETON | WITCH | CREEPER
 *         | ENDERMAN | BLAZE | PILLAGER | WANDERING_TRADER | IRON_GOLEM
 *   location: "3,64,5,90,0"       # x,y,z,yaw,pitch
 *   skin: "玩家名或皮肤值"          # 仅 PLAYER 型（走 Adyeshach）
 *   look-at-player: true
 *   dialogs:
 *     - "你好，冒险者"
 *     - "前面就是 Boss 房了"
 *   dialog-interval: 40           # 逐句间隔 tick
 *   click-script: [...]
 *   once: false
 * </pre>
 */
public record NpcConfig(
    Map<String, DungeonNpc> npcs
) {

    public record DungeonNpc(
        String name,
        String displayName,
        String type,
        String location,
        String skin,
        boolean lookAtPlayer,
        List<String> dialogs,
        int dialogInterval,
        List<String> clickScript,
        boolean once
    ) {
        static DungeonNpc load(String name, ConfigurationSection s) {
            return new DungeonNpc(
                name,
                s.getString("name", name),
                s.getString("type", "VILLAGER").toUpperCase(),
                s.getString("location", "0,64,0,0,0"),
                s.getString("skin", null),
                s.getBoolean("look-at-player", s.getBoolean("look_at_player", true)),
                s.getStringList("dialogs"),
                s.getInt("dialog-interval", s.getInt("dialog_interval", 40)),
                s.getStringList("click-script").isEmpty()
                    ? s.getStringList("click_script") : s.getStringList("click-script"),
                s.getBoolean("once", false)
            );
        }
    }

    public static NpcConfig load(ConfigurationSection root) {
        Map<String, DungeonNpc> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) map.put(key, DungeonNpc.load(key, s));
            }
        }
        return new NpcConfig(map);
    }
}
