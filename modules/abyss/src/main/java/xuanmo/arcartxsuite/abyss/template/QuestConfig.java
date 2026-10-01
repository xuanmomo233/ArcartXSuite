package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

/**
 * quests.yml 解析：副本任务（NPC 驱动的接取/提交任务）。
 *
 * <pre>
 * slay_guard:
 *   name: "讨伐守卫"
 *   description: "帮向导消灭地牢守卫"
 *   accept-text: "谢谢你，勇士！消灭 5 只守卫后回来找我。"
 *   accept-script: ["message @trigger &a已接取任务: 讨伐守卫"]
 *   submit-text: "干得漂亮！这是给你的报酬。"
 *   submit-conditions: ["%axsabyss_dungeon_kills% >= 5"]
 *   submit-script: ["grant_reward @trigger quest_pack"]
 * </pre>
 */
public record QuestConfig(
    Map<String, Quest> quests
) {

    public record Quest(
        String id,
        String name,
        String description,
        String acceptText,
        List<String> acceptScript,
        String submitText,
        List<ScriptCondition> submitConditions,
        List<String> submitScript
    ) {
        static Quest load(String id, ConfigurationSection s) {
            return new Quest(
                id,
                s.getString("name", id),
                s.getString("description", ""),
                s.getString("accept-text", s.getString("accept_text", "")),
                s.getStringList("accept-script").isEmpty()
                    ? s.getStringList("accept_script") : s.getStringList("accept-script"),
                s.getString("submit-text", s.getString("submit_text", "")),
                ScriptConditionsLoader.load(s, "submit-conditions", "submit_conditions"),
                s.getStringList("submit-script").isEmpty()
                    ? s.getStringList("submit_script") : s.getStringList("submit-script")
            );
        }
    }

    public static QuestConfig load(ConfigurationSection root) {
        Map<String, Quest> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) map.put(key, Quest.load(key, s));
            }
        }
        return new QuestConfig(map);
    }
}
