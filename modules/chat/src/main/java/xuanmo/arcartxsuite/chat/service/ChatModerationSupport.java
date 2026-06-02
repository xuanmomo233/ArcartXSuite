package xuanmo.arcartxsuite.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xuanmo.arcartxsuite.chat.config.ChatFilterConfiguration;

public final class ChatModerationSupport {

    private ChatModerationSupport() {
    }

    public static ModerationResult applyFilters(String message, ChatFilterConfiguration configuration, List<String> cloudWords) {
        if (message == null) {
            return new ModerationResult(false, "");
        }
        if (configuration == null || !configuration.enabled()) {
            return new ModerationResult(false, message);
        }

        String current = message;
        List<String> words = new ArrayList<>(configuration.blockedWords());
        if (cloudWords != null) {
            for (String cloudWord : cloudWords) {
                if (cloudWord != null && !cloudWord.isBlank()) {
                    words.add(cloudWord.toLowerCase(Locale.ROOT));
                }
            }
        }

        for (String blockedWord : words) {
            if (blockedWord.isBlank()) {
                continue;
            }
            if (current.toLowerCase(Locale.ROOT).contains(blockedWord)) {
                if (configuration.cancelOnMatch()) {
                    return new ModerationResult(true, current);
                }
                current = replaceIgnoreCase(current, blockedWord, configuration.replacement());
            }
        }

        for (Pattern pattern : configuration.blockedPatterns()) {
            Matcher matcher = pattern.matcher(current);
            if (!matcher.find()) {
                continue;
            }
            if (configuration.cancelOnMatch()) {
                return new ModerationResult(true, current);
            }
            current = matcher.replaceAll(configuration.replacement());
        }
        return new ModerationResult(false, current);
    }

    private static String replaceIgnoreCase(String input, String target, String replacement) {
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
            .matcher(input)
            .replaceAll(Matcher.quoteReplacement(replacement));
    }

    public record ModerationResult(
        boolean blocked,
        String message
    ) {
    }
}
