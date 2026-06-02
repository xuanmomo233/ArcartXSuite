package xuanmo.arcartxsuite.eventpacket.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public record EventPacketCondition(
    String placeholder,
    EventPacketConditionOperator operator,
    String value,
    String raw
) {

    private static final Pattern INLINE_PATTERN = Pattern.compile(
        "^(%[^%]+%)\\s+(==|!=|>=|<=|>|<|contains|regex)\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );

    @Nullable
    public static EventPacketCondition parse(String inline) {
        if (inline == null || inline.isBlank()) {
            return null;
        }
        String trimmed = inline.trim();
        Matcher matcher = INLINE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        String placeholder = matcher.group(1).trim();
        EventPacketConditionOperator operator = EventPacketConditionOperator.parse(matcher.group(2).trim());
        String value = matcher.group(3).trim();
        return new EventPacketCondition(placeholder, operator, value, trimmed);
    }
}
