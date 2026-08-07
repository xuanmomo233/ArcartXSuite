package xuanmo.arcartxsuite.mail.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 邮件审核配置，定义玩家发送邮件时被拦截的关键词、正则、物品与 Lore 模式。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code moderation} 节解析。
 *
 * @param blockedWords       被拦截的敏感词列表（小写）
 * @param blockedPatterns    被拦截的正则模式列表
 * @param blockedMaterials   被拦截的物品材质列表（大写）
 * @param blockedLorePatterns 被拦截的物品 Lore 正则模式列表
 */
public record MailModerationConfiguration(
    List<String> blockedWords,
    List<Pattern> blockedPatterns,
    List<String> blockedMaterials,
    List<Pattern> blockedLorePatterns
) {
}
