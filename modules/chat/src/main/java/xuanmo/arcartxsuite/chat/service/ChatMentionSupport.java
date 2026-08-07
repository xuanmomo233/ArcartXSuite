package xuanmo.arcartxsuite.chat.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 @提及解析工具类。
 * <p>
 * 从消息文本中提取 {@code @玩家名} 和 {@code @all} 标记，
 * 与在线玩家候选名单匹配后返回提及结果。
 */
public final class ChatMentionSupport {

    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\S)@([A-Za-z0-9_]{1,16}|all)\\b");

    private ChatMentionSupport() {
    }

    /**
     * 解析消息中的 @提及标记。
     *
     * @param message       消息文本
     * @param candidateNames 在线玩家候选名称集合
     * @param allowAll      是否允许 @all 全员提及
     * @return 提及结果
     */
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

    /**
     * 提及解析结果。
     *
     * @param mentionAll     是否触发了 @all
     * @param mentionedNames 被提及的玩家名称集合（小写）
     */
    public record MentionResult(
        boolean mentionAll,
        Set<String> mentionedNames
    ) {
    }
}
