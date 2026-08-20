package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * 基于事件载荷键值匹配的任务条件。
 * <p>
 * 条件在构造时完成正则编译、数值解析和集合构造，避免在事件热路径中重复处理配置。
 * 当 payload 缺少目标键时，除 {@code missing} 外的所有操作符都返回 {@code false}；
 * 缺失键不会满足任何 {@code not_*} 操作符。
 */
public final class EventPayloadCondition implements TaskCondition {

    private static final Set<String> SUPPORTED_OPERATOR_SET = Set.of(
        "equals", "equals_ignore_case", "not_equals", "contains", "not_contains",
        "starts_with", "ends_with", "regex", "greater_than", "greater_or_equal",
        "less_than", "less_or_equal", "in", "not_in", "exists", "missing"
    );
    private static final String SUPPORTED_OPERATORS =
        String.join(", ", SUPPORTED_OPERATOR_SET);

    private final String key;
    private final String operator;
    private final String value;
    private final boolean ignoreCase;
    private final Pattern pattern;
    private final Double numericValue;
    private final Set<String> values;
    private final boolean invalid;

    /**
     * 构造事件载荷条件。
     *
     * @param key payload 中要检查的键名
     * @param operator 比较操作符
     * @param value 目标比较值
     * @param values 集合比较的目标值
     * @param ignoreCase 是否忽略字符串大小写
     * @param logger 日志记录器
     * @param taskId 任务 ID
     */
    public EventPayloadCondition(
        String key,
        String operator,
        String value,
        Collection<String> values,
        boolean ignoreCase,
        Logger logger,
        String taskId
    ) {
        this.key = key;
        this.operator = normalizeOperator(operator);
        this.value = value == null ? "" : value;
        this.ignoreCase = ignoreCase;
        this.pattern = "regex".equals(this.operator) ? compilePattern(this.value, logger, taskId) : null;
        this.numericValue = isNumericOperator(this.operator)
            ? parseNumber(this.value, logger, taskId)
            : null;
        this.values = isSetOperator(this.operator)
            ? buildValues(values, this.value, ignoreCase)
            : Collections.emptySet();
        this.invalid = ("regex".equals(this.operator) && this.pattern == null)
            || (isNumericOperator(this.operator) && this.numericValue == null);
    }

    /**
     * 使用单个 value 构造事件载荷条件。
     *
     * @param key payload 中要检查的键名
     * @param operator 比较操作符
     * @param value 目标比较值
     */
    public EventPayloadCondition(String key, String operator, String value) {
        this(key, operator, value, Collections.emptyList(), false, null, "");
    }

    /**
     * 判断操作符是否受支持。
     *
     * @param operator 规范化后的操作符
     * @return {@code true} 表示操作符受支持
     */
    public static boolean isSupportedOperator(String operator) {
        return SUPPORTED_OPERATOR_SET.contains(operator);
    }

    private static String normalizeOperator(String operator) {
        if (operator == null) return "equals";
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

    private static Pattern compilePattern(String value, Logger logger, String taskId) {
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            warn(logger, taskId, "regex 编译失败: " + e.getMessage());
            return null;
        }
    }

    private static Double parseNumber(String value, Logger logger, String taskId) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            warn(logger, taskId, "数值配置非法: " + value);
            return null;
        }
    }

    private static Set<String> buildValues(
        Collection<String> configuredValues, String value, boolean ignoreCase
    ) {
        Set<String> result = new HashSet<>();
        if (configuredValues != null) {
            for (String item : configuredValues) {
                if (item != null) result.add(normalizeValue(item, ignoreCase));
            }
        }
        if (result.isEmpty() && value != null) {
            for (String item : value.split(",")) {
                result.add(normalizeValue(item, ignoreCase));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static String normalizeValue(String value, boolean ignoreCase) {
        String normalized = value.trim();
        return ignoreCase ? normalized.toLowerCase(java.util.Locale.ROOT) : normalized;
    }

    private static boolean isNumericOperator(String operator) {
        return switch (operator) {
            case "greater_than", "greater_or_equal", "less_than", "less_or_equal" -> true;
            default -> false;
        };
    }

    private static boolean isSetOperator(String operator) {
        return "in".equals(operator) || "not_in".equals(operator);
    }

    private static void warn(Logger logger, String taskId, String reason) {
        if (logger != null) {
            logger.warning("任务 " + taskId + " 的 event_payload 条件无效: " + reason);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param player 触发事件的玩家
     * @param payload 事件载荷
     * @return {@code true} 表示条件满足
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        if (payload == null) return false;
        if ("missing".equals(operator)) return !payload.containsKey(key);
        if ("exists".equals(operator)) return payload.containsKey(key);
        if (invalid || !payload.containsKey(key)) return false;
        String actual = payload.get(key);
        if (actual == null) return false;
        String comparedActual = ignoreCase ? actual.toLowerCase(java.util.Locale.ROOT) : actual;
        String comparedValue = ignoreCase ? value.toLowerCase(java.util.Locale.ROOT) : value;
        return switch (operator) {
            case "equals" -> comparedActual.equals(comparedValue);
            case "equals_ignore_case" -> actual.equalsIgnoreCase(value);
            case "not_equals" -> !comparedActual.equals(comparedValue);
            case "contains" -> comparedActual.contains(comparedValue);
            case "not_contains" -> !comparedActual.contains(comparedValue);
            case "starts_with" -> comparedActual.startsWith(comparedValue);
            case "ends_with" -> comparedActual.endsWith(comparedValue);
            case "regex" -> pattern.matcher(actual).matches();
            case "greater_than", "greater_or_equal", "less_than", "less_or_equal" ->
                compareNumber(actual, operator);
            case "in" -> values.contains(normalizeValue(actual, ignoreCase));
            case "not_in" -> !values.contains(normalizeValue(actual, ignoreCase));
            default -> actual.equals(value);
        };
    }

    private boolean compareNumber(String actual, String operator) {
        try {
            int result = Double.compare(Double.parseDouble(actual), numericValue);
            return switch (operator) {
                case "greater_than" -> result > 0;
                case "greater_or_equal" -> result >= 0;
                case "less_than" -> result < 0;
                case "less_or_equal" -> result <= 0;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 返回条件类型。
     *
     * @return {@code event_payload}
     */
    @Override
    public String type() {
        return "event_payload";
    }

    /**
     * 返回 payload 键名。
     *
     * @return payload 键名
     */
    public String key() {
        return key;
    }

    /**
     * 返回规范化后的操作符。
     *
     * @return 操作符
     */
    public String operator() {
        return operator;
    }

    /**
     * 返回目标值。
     *
     * @return 目标值
     */
    public String value() {
        return value;
    }

    /**
     * 返回支持的操作符说明。
     *
     * @return 支持的操作符列表
     */
    public static String supportedOperators() {
        return SUPPORTED_OPERATORS;
    }
}
