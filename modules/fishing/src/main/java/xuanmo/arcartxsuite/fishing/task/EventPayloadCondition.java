package xuanmo.arcartxsuite.fishing.task;

import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 基于事件 payload 字段的条件判断。
 * <p>
 * 支持运算符：equals / not_equals / gte / lte / gt / lt / in / not_in
 *
 * @param key      payload 字段名
 * @param operator 运算符
 * @param value    比较值（in/not_in 时为逗号分隔列表）
 */
public record EventPayloadCondition(
    @NotNull String key,
    @NotNull String operator,
    @NotNull String value
) implements TaskCondition {

    @Override
    public boolean test(@NotNull Player player, @NotNull Map<String, String> payload) {
        String actual = payload.get(key);
        if (actual == null) return false;
        return switch (operator.toLowerCase()) {
            case "equals" -> actual.equalsIgnoreCase(value);
            case "not_equals" -> !actual.equalsIgnoreCase(value);
            case "gte" -> tryParseDouble(actual) >= tryParseDouble(value);
            case "lte" -> tryParseDouble(actual) <= tryParseDouble(value);
            case "gt" -> tryParseDouble(actual) > tryParseDouble(value);
            case "lt" -> tryParseDouble(actual) < tryParseDouble(value);
            case "in" -> {
                Set<String> values = Set.of(value.toUpperCase().split(","));
                yield values.contains(actual.toUpperCase());
            }
            case "not_in" -> {
                Set<String> values = Set.of(value.toUpperCase().split(","));
                yield !values.contains(actual.toUpperCase());
            }
            default -> false;
        };
    }

    private double tryParseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
