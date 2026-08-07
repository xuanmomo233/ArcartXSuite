package xuanmo.arcartxsuite.chat.config;

import java.util.regex.Pattern;

/**
 * 自定义聊天组件定义。
 * <p>
 * 通过正则匹配聊天消息中的特定文本并替换为自定义内容，
 * 用于实现如表情包、关键词高亮等扩展功能。
 *
 * @param id          组件 ID（小写规范化）
 * @param pattern     匹配正则表达式
 * @param replacement 替换文本
 */
public record ChatCustomComponent(
    String id,
    Pattern pattern,
    String replacement
) {
}
