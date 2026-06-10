package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

public final class EventPayloadCondition implements TaskCondition {

    private final String key;
    private final String operator;
    private final String value;

    public EventPayloadCondition(String key, String operator, String value) {
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

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
