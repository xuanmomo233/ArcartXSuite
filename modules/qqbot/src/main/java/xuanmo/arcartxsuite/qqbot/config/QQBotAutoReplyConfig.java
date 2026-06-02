package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 关键词自动回复（FAQ）配置。
 */
public record QQBotAutoReplyConfig(
    boolean enabled,
    int cooldownSeconds,
    List<AutoReplyRule> rules
) {
    /**
     * 单条关键词回复规则。
     *
     * @param keywords   触发关键词列表
     * @param response   回复内容
     * @param exactMatch true 表示消息须完全等于某关键词，false 表示包含即触发
     */
    public record AutoReplyRule(
        List<String> keywords,
        String response,
        boolean exactMatch
    ) {
        public boolean matches(String message) {
            if (message == null) return false;
            String msg = message.trim();
            for (String kw : keywords) {
                if (kw == null || kw.isBlank()) continue;
                if (exactMatch) {
                    if (msg.equalsIgnoreCase(kw.trim())) return true;
                } else if (msg.toLowerCase().contains(kw.trim().toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }
}
