package xuanmo.arcartxsuite.chat.config;

import java.util.List;
import java.util.regex.Pattern;

public record ChatFilterConfiguration(
    boolean enabled,
    boolean cancelOnMatch,
    String replacement,
    List<String> blockedWords,
    List<Pattern> blockedPatterns,
    boolean cloudEnabled,
    String cloudUrl,
    int cloudRefreshMinutes
) {
}
