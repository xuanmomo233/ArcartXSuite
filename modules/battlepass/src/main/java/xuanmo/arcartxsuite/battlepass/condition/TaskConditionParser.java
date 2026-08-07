package xuanmo.arcartxsuite.battlepass.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 任务条件解析器，从 YAML 配置节解析 {@link TaskCondition} 列表。
 * <p>
 * 根据 {@code type} 字段路由到具体实现类（{@link EventPayloadCondition}、{@link PlayerStateCondition}），
 * 提供单条解析和批量解析两种入口。
 */
public final class TaskConditionParser {

    private TaskConditionParser() {}

    /**
     * 从配置节批量解析条件列表。
     *
     * @param section 包含多个条件子节的配置节
     * @return 解析得到的不可变条件列表，入参为 null 时返回空列表
     */
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

    /**
     * 从单个配置节解析一条条件。
     *
     * @param section 条件配置节，需含 {@code type} 字段
     * @return 解析得到的条件实例，类型未知时返回 {@code null}
     */
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
