package xuanmo.arcartxsuite.chat.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMentionSupport {

    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\S)@([A-Za-z0-9_]{1,16}|all)\\b");

    private ChatMentionSupport() {
    }

    public static MentionResult parseMentions(String message, Collection<String> candidateNames, boolean allowAll) {
        LinkedHashSet<String> mentionedNames = new LinkedHashSet<>();
        boolean mentionAll = false;
        if (message == null || message.isBlank()) {
            return new MentionResult(false, Set.of());
        }

        Set<String> candidates = new LinkedHashSet<>();
        if (candidateNames != null) {
            for (String candidateName : candidateNames) {
                if (candidateName != null && !candidateName.isBlank()) {
                    candidates.add(candidateName.toLowerCase(Locale.ROOT));
                }
            }
        }

        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            if ("all".equals(token)) {
                if (allowAll) {
                    mentionAll = true;
                }
                continue;
            }
            if (candidates.contains(token)) {
                mentionedNames.add(token);
            }
        }
        return new MentionResult(mentionAll, Set.copyOf(mentionedNames));
    }

    public record MentionResult(
        boolean mentionAll,
        Set<String> mentionedNames
    ) {
    }
}
