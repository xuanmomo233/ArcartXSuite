package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 基于事件载荷（payload）键值匹配的任务条件，实现 {@link TaskCondition}。
 * <p>
 * 支持多种比较操作符（equals、contains、starts_with、ends_with、regex、greater_than、less_than），
 * 从事件 payload 中提取指定键的值并与目标值进行比较。
 */
public final class EventPayloadCondition implements TaskCondition {

    private final String key;
    private final String operator;
    private final String value;

    /**
     * 构造事件载荷条件。
     *
     * @param key      payload 中要比较的键名
     * @param operator 比较操作符（equals/contains/starts_with/ends_with/regex/greater_than/less_than）
     * @param value    目标比较值
     */
    public EventPayloadCondition(String key, String operator, String value) {
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 从 payload 中提取指定键的值，按操作符与目标值进行比较。
     *
     * @param player  触发事件的玩家（此条件不使用）
     * @param payload 事件载荷
     * @return {@code true} 表示匹配成功
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        if (payload == null) return false;
        String actual = payload.get(key);
        if (actual == null) return false;
        return switch (operator) {
            case "equals" -> actual.equals(value);
            case "contains" -> actual.contains(value);
            case "starts_with" -> actual.startsWith(value);
            case "ends_with" -> actual.endsWith(value);
            case "regex" -> actual.matches(value);
            case "greater_than" -> {
                try {
                    yield Double.parseDouble(actual) > Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "less_than" -> {
                try {
                    yield Double.parseDouble(actual) < Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> actual.equals(value);
        };
    }

    @Override
    public String type() {
        return "event_payload";
    }

    public String key() { return key; }
    public String operator() { return operator; }
    public String value() { return value; }
}
