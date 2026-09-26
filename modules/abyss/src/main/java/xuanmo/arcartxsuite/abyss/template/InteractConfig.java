package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

/**
 * interacts.yml 解析：交互点。
 *
 * <pre>
 * treasure_chest:
 *   type: RIGHT_CLICK_BLOCK | LEFT_CLICK_BLOCK | BREAK_BLOCK | PLACE_BLOCK
 *         | STEP_ON | ENTER_REGION | RIGHT_CLICK_ENTITY
 *   location: "5,64,10"           # 点型
 *   region: ["0,60,0", "20,80,20"] # 区域型（STEP_ON/ENTER_REGION 用）
 *   required_item: "material:TRIPWIRE_HOOK:1"
 *   required_block: "CHEST"        # 目标方块类型（可空）
 *   entity_name: "宝箱守卫"         # RIGHT_CLICK_ENTITY 用
 *   once: true
 *   fail_message: "&c需要一把钥匙"
 *   conditions: ["%player_level% >= 10"]
 *   script: ["grant_reward @trigger 宝箱奖励"]
 * </pre>
 */
public record InteractConfig(
    Map<String, Interact> interacts
) {

    public enum InteractType {
        RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK, BREAK_BLOCK, PLACE_BLOCK,
        STEP_ON, ENTER_REGION, RIGHT_CLICK_ENTITY
    }

    public record Interact(
        String name,
        InteractType type,
        String location,
        List<String> region,
        String requiredItem,
        String requiredBlock,
        String entityName,
        boolean once,
        String failMessage,
        List<ScriptCondition> conditions,
        List<String> script
    ) {
        static Interact load(String name, ConfigurationSection s) {
            InteractType type;
            try {
                type = InteractType.valueOf(s.getString("type", "RIGHT_CLICK_BLOCK").toUpperCase());
            } catch (IllegalArgumentException e) {
                type = InteractType.RIGHT_CLICK_BLOCK;
            }
            return new Interact(
                name,
                type,
                s.getString("location", null),
                s.getStringList("region"),
                s.getString("required_item", s.getString("required-item", null)),
                s.getString("required_block", s.getString("required-block", null)),
                s.getString("entity_name", s.getString("entity-name", null)),
                s.getBoolean("once", false),
                s.getString("fail_message", s.getString("fail-message", null)),
                ScriptConditionsLoader.load(s, "conditions"),
                s.getStringList("script")
            );
        }
    }

    public static InteractConfig load(ConfigurationSection root) {
        Map<String, Interact> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    map.put(key, Interact.load(key, s));
                }
            }
        }
        return new InteractConfig(map);
    }
}
