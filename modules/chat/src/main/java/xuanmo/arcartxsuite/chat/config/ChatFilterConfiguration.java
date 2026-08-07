package xuanmo.arcartxsuite.chat.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 聊天过滤器配置。
 * <p>
 * 支持敏感词屏蔽（精确匹配 + 正则匹配）、匹配后取消发送或替换文本，
 * 以及云端敏感词库自动刷新。
 *
 * @param enabled            是否启用过滤
 * @param cancelOnMatch      匹配到敏感词时是否直接取消消息发送（false 则替换）
 * @param replacement        替换文本（如 "*"）
 * @param blockedWords       屏蔽词列表（小写）
 * @param blockedPatterns    屏蔽正则列表
 * @param cloudEnabled       是否启用云端敏感词库
 * @param cloudUrl           云端敏感词库 URL
 * @param cloudRefreshMinutes 云端刷新间隔（分钟）
 */
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
