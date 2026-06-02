package xuanmo.arcartxsuite.chat.config;

import java.util.regex.Pattern;

public record ChatCustomComponent(
    String id,
    Pattern pattern,
    String replacement
) {
}
