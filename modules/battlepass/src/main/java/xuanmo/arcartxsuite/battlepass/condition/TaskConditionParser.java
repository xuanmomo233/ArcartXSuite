package xuanmo.arcartxsuite.battlepass.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class TaskConditionParser {

    private TaskConditionParser() {}

    public static List<TaskCondition> parseList(ConfigurationSection section) {
        List<TaskCondition> result = new ArrayList<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection condSection = section.getConfigurationSection(key);
            if (condSection == null) continue;
            TaskCondition cond = parse(condSection);
            if (cond != null) result.add(cond);
        }
        return result;
    }

    public static TaskCondition parse(ConfigurationSection section) {
        String type = section.getString("type", "");
        return switch (type) {
            case "event_payload" -> new EventPayloadCondition(
                section.getString("key", ""),
                section.getString("operator", "equals"),
                section.getString("value", "")
            );
            case "player_state" -> new PlayerStateCondition(
                section.getString("state-type", ""),
                section.getString("state-id", "")
            );
            default -> null;
        };
    }
}
