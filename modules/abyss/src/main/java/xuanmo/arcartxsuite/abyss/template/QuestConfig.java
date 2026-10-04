package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * quests.yml 解析：副本任务 → questgps/Chemdah 任务的适配层。
 * <p>
 * 任务语义（目标/进度/完成判定）100% 由 Chemdah 任务定义承担，
 * 本文件只保留 abyss 编排面：映射关系 + 副本脚本钩子。
 * 注意 Chemdah 任务为玩家级持久档案——副本任务应在 Chemdah 侧配置为可重复接取。
 *
 * <pre>
 * slayer:
 *   chemdah-id: "abyss_slayer"          # questgps/Chemdah 任务 id（缺省=本 key）
 *   name: "讨伐深渊魔物"                  # HUD 任务行显示名
 *   description: "消灭地牢中的魔物"
 *   auto-track: true                    # 接取后自动 quest_track 追踪
 *   on-accept:                          # 该玩家接取成功时跑的副本脚本（@trigger=该玩家）
 *     - "message @trigger &a已接取委托"
 *   on-complete:                        # Chemdah 完成时跑的副本脚本（副本侧奖励）
 *     - "grant_reward @trigger quest_pack"
 *   manual-claim: false                 # true=完成不自动发奖，需 submit_quest 回 NPC 手动领取
 * </pre>
 */
public record QuestConfig(
    Map<String, Quest> quests
) {

    public record Quest(
        String id,
        String chemdahId,
        String name,
        String description,
        boolean autoTrack,
        List<String> onAccept,
        List<String> onComplete,
        boolean manualClaim
    ) {
        static Quest load(String id, ConfigurationSection s) {
            return new Quest(
                id,
                firstNonBlank(s, id, "chemdah-id", "chemdah_id", "quest-id", "quest_id"),
                s.getString("name", id),
                s.getString("description", ""),
                s.getBoolean("auto-track", s.getBoolean("auto_track", true)),
                listOf(s, "on-accept", "on_accept", "accept-script", "accept_script"),
                listOf(s, "on-complete", "on_complete", "submit-script", "submit_script"),
                s.getBoolean("manual-claim", s.getBoolean("manual_claim", false))
            );
        }

        private static String firstNonBlank(ConfigurationSection s, String def, String... keys) {
            for (String k : keys) {
                String v = s.getString(k);
                if (v != null && !v.isBlank()) return v;
            }
            return def;
        }

        private static List<String> listOf(ConfigurationSection s, String... keys) {
            for (String k : keys) {
                List<String> v = s.getStringList(k);
                if (v != null && !v.isEmpty()) return v;
            }
            return List.of();
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
