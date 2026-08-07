package xuanmo.arcartxsuite.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xuanmo.arcartxsuite.chat.config.ChatFilterConfiguration;

/**
 * 聊天内容审核工具类，对消息执行敏感词过滤。
 * <p>
 * 支持精确词匹配与正则匹配，可配置匹配后取消发送或替换文本，
 * 并合并云端敏感词库进行联合判定。
 */
public final class ChatModerationSupport {

    private ChatModerationSupport() {
    }

    /**
     * 对消息应用过滤器，返回审核结果。
     *
     * @param message      原始消息
     * @param configuration 过滤器配置
     * @param cloudWords   云端敏感词列表（可为 {@code null}）
     * @return 审核结果（blocked = true 表示应取消发送）
     */
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

    /**
     * 审核结果。
     *
     * @param blocked 是否命中敏感词且应取消发送
     * @param message 处理后的消息（取消时为原文，替换时为替换后文本）
     */
    public record ModerationResult(
        boolean blocked,
        String message
    ) {
    }
}
