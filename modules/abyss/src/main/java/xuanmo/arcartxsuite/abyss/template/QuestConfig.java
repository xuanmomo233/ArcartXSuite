package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 读取副本任务引用与实例内脚本。任务文字、目标和进度使用 Chemdah。
 */
public record QuestConfig(
    Map<String, Quest> quests
) {

    public record Quest(
        String id,
        String chemdahId,
        boolean autoTrack,
        List<String> onAccept,
        List<String> onComplete,
        boolean manualClaim
    ) {
        static Quest load(String id, ConfigurationSection s) {
            return new Quest(
                id,
                firstNonBlank(s, id, "chemdah-id", "chemdah_id", "quest-id", "quest_id"),
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
