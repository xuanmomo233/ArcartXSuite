package xuanmo.arcartxsuite.battlepass.condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 解析战令任务条件配置。
 */
public final class TaskConditionParser {

    private static final int MAX_COMPOSITE_DEPTH = 5;

    private TaskConditionParser() {}

    /**
     * 解析配置节中的条件列表。
     *
     * @param section 条件配置节
     * @param logger 日志记录器
     * @param taskId 任务 ID
     * @return 不可变的条件列表
     */
    public static List<TaskCondition> parseList(ConfigurationSection section, Logger logger, String taskId) {
        List<TaskCondition> result = new ArrayList<>();
        if (section == null) return Collections.unmodifiableList(result);
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                TaskCondition condition = parse(child, logger, taskId);
                if (condition != null) result.add(condition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 解析 YAML 列表形式的条件配置。
     *
     * @param maps 条件配置映射列表
     * @param logger 日志记录器
     * @param taskId 任务 ID
     * @return 不可变的条件列表
     */
    public static List<TaskCondition> parseList(
        List<? extends Map<?, ?>> maps, Logger logger, String taskId
    ) {
        return parseMaps(maps, logger, taskId, 0);
    }

    private static List<TaskCondition> parseMaps(
        List<? extends Map<?, ?>> maps, Logger logger, String taskId, int depth
    ) {
        List<TaskCondition> result = new ArrayList<>();
        if (maps != null) {
            for (Map<?, ?> map : maps) {
                TaskCondition condition = parse(map, logger, taskId, depth);
                if (condition != null) result.add(condition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 解析单个配置节形式的条件。
     *
     * @param section 条件配置节
     * @param logger 日志记录器
     * @param taskId 任务 ID
     * @return 解析出的条件，无法识别时返回 {@code null}
     */
    public static TaskCondition parse(ConfigurationSection section, Logger logger, String taskId) {
        if (section == null) return null;
        return parseSection(section, logger, taskId, 0);
    }

    private static TaskCondition parseSection(
        ConfigurationSection section, Logger logger, String taskId, int depth
    ) {
        if (section == null) return null;
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key, section.get(key));
        }
        return parse(values, logger, taskId, depth);
    }

    /**
     * 解析单个映射形式的条件。
     *
     * @param map 条件配置映射
     * @param logger 日志记录器
     * @param taskId 任务 ID
     * @return 解析出的条件，无法识别时返回 {@code null}
     */
    public static TaskCondition parse(Map<?, ?> map, Logger logger, String taskId) {
        return parse(map, logger, taskId, 0);
    }

    private static TaskCondition parse(
        Map<?, ?> map, Logger logger, String taskId, int depth
    ) {
        if (map == null) return null;
        String type = value(map, "type", "");
        TaskCondition condition;
        switch (type) {
            case "event_payload" -> condition = parseEventPayload(map, logger, taskId);
            case "player_state" -> condition = parsePlayerState(map, logger, taskId);
            case "any_of", "all_of", "not" ->
                condition = parseComposite(map, type, logger, taskId, depth);
            default -> {
                warn(logger, "任务 " + taskId
                    + " 包含未知条件类型，条件已忽略: " + type);
                return null;
            }
        }
        if (condition == null) return null;
        return booleanValue(map, "negate", false)
            ? new NegatedCondition(condition)
            : condition;
    }

    private static TaskCondition parseEventPayload(
        Map<?, ?> map, Logger logger, String taskId
    ) {
        if (!map.containsKey("key") || value(map, "key", "").isEmpty()) {
            warn(logger, "任务 " + taskId
                + " 的 event_payload 条件缺少必填项 key，条件已忽略");
            return null;
        }
        if (!map.containsKey("operator") || value(map, "operator", "").isEmpty()) {
            warn(logger, "任务 " + taskId
                + " 的 event_payload 条件缺少必填项 operator，条件已忽略");
            return null;
        }
        String operator = value(map, "operator", "");
        String normalized = normalizeOperator(operator);
        if (!isSupportedOperator(normalized)) {
            warn(logger, "任务 " + taskId
                + " 的 event_payload 条件使用未知 operator '" + operator
                + "'，支持列表: " + EventPayloadCondition.supportedOperators()
                + "；运行期按 equals 处理");
        }
        List<String> values = stringList(map.get("values"));
        return new EventPayloadCondition(
            value(map, "key", ""),
            operator,
            value(map, "value", ""),
            values,
            booleanValue(map, "ignore-case", false),
            logger,
            taskId
        );
    }

    private static TaskCondition parsePlayerState(
        Map<?, ?> map, Logger logger, String taskId
    ) {
        if (!map.containsKey("state-type") || value(map, "state-type", "").isEmpty()) {
            warn(logger, "任务 " + taskId
                + " 的 player_state 条件缺少必填项 state-type，条件已忽略");
            return null;
        }
        if (!map.containsKey("state-id") || value(map, "state-id", "").isEmpty()) {
            warn(logger, "任务 " + taskId
                + " 的 player_state 条件缺少必填项 state-id，条件已忽略");
            return null;
        }
        return new PlayerStateCondition(
            value(map, "state-type", ""),
            value(map, "state-id", ""),
            logger,
            taskId
        );
    }

    private static TaskCondition parseComposite(
        Map<?, ?> map,
        String type,
        Logger logger,
        String taskId,
        int depth
    ) {
        if (depth >= MAX_COMPOSITE_DEPTH) {
            warn(logger, "任务 " + taskId
                + " 的组合条件嵌套深度超过 " + MAX_COMPOSITE_DEPTH
                + " 层，更深层级已忽略");
            return null;
        }
        List<TaskCondition> children = parseChildren(map.get("conditions"), logger, taskId, depth + 1);
        if (children.isEmpty()) {
            warn(logger, "任务 " + taskId
                + " 的组合条件 '" + type + "' 没有可解析的子条件，条件已忽略");
            return null;
        }
        return new CompositeCondition(type, children);
    }

    private static List<TaskCondition> parseChildren(
        Object raw, Logger logger, String taskId, int depth
    ) {
        if (raw instanceof ConfigurationSection section) {
            List<TaskCondition> result = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    TaskCondition condition = parseSection(child, logger, taskId, depth);
                    if (condition != null) result.add(condition);
                }
            }
            return result;
        }
        if (raw instanceof List<?> list) {
            List<TaskCondition> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> child) {
                    TaskCondition condition = parse(child, logger, taskId, depth);
                    if (condition != null) result.add(condition);
                }
            }
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("type")) {
                TaskCondition condition = parse(map, logger, taskId, depth);
                return condition == null ? Collections.emptyList() : List.of(condition);
            }
            List<TaskCondition> result = new ArrayList<>();
            for (Object item : map.values()) {
                if (item instanceof Map<?, ?> child) {
                    TaskCondition condition = parse(child, logger, taskId, depth);
                    if (condition != null) result.add(condition);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static String normalizeOperator(String operator) {
        return switch (operator.trim()) {
            case "==" -> "equals";
            case "!=" -> "not_equals";
            case ">" -> "greater_than";
            case ">=" -> "greater_or_equal";
            case "<" -> "less_than";
            case "<=" -> "less_or_equal";
            default -> operator.trim();
        };
    }

    private static boolean isSupportedOperator(String operator) {
        return EventPayloadCondition.isSupportedOperator(operator);
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object item : collection) {
            if (item != null) result.add(String.valueOf(item));
        }
        return result;
    }

    private static String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) logger.warning(message);
    }
}
