package xuanmo.arcartxsuite.mail.config;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 邮件审核配置，定义玩家发送邮件时被拦截的关键词、正则、物品与 Lore 模式。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code moderation} 节解析。
 *
 * @param blockedWords              被拦截的敏感词列表（小写）
 * @param blockedPatterns           被拦截的正则模式列表
 * @param blockedMaterials          被拦截的物品材质列表（大写）
 * @param blockedLorePatterns       被拦截的物品 Lore 正则模式列表
 * @param blockedDisplaynamePatterns 被拦截的物品自定义名称正则模式列表
 * @param blockedNbtKeys            被拦截的 NBT/PDC 键列表（存在即匹配）
 * @param blockedNbtValues          被拦截的 NBT/PDC 键值对（键=值，值匹配即匹配）
 */
public record MailModerationConfiguration(
    List<String> blockedWords,
    List<Pattern> blockedPatterns,
    List<String> blockedMaterials,
    List<Pattern> blockedLorePatterns,
    List<Pattern> blockedDisplaynamePatterns,
    List<String> blockedNbtKeys,
    Map<String, String> blockedNbtValues
) {
}
