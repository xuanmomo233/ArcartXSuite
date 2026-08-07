package xuanmo.arcartxsuite.conversation.service;

import ink.ptms.chemdah.core.conversation.PlayerReply;
import java.util.Locale;
import java.util.Map;

/**
 * 对话回复语义支持工具（包私有），解析 Chemdah {@link PlayerReply} 的类型与格式。
 * <p>
 * 用于判定回复是 talk、option 还是其他类型，驱动 UI 渲染对应交互控件。
 */
final class ConversationReplySemanticsSupport {

    private ConversationReplySemanticsSupport() {
    }

    static String replyType(PlayerReply reply) {
        if (reply == null) {
            return "";
        }
        Map<String, Object> root = reply.getRoot();
        Object rawType = root == null ? null : root.get("type");
        return normalizeReplyType(rawType, reply.getFormat());
    }

    static String normalizeReplyType(Object rawType, String fallbackFormat) {
        String normalizedType = normalizeValue(rawType);
        if (!normalizedType.isEmpty()) {
            return normalizedType;
        }
        return normalizeValue(fallbackFormat);
    }

    static boolean isTalkType(String replyType) {
        return "talk".equals(normalizeValue(replyType));
    }

    static boolean isCloseType(String replyType) {
        return "close".equals(normalizeValue(replyType));
    }

    private static String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "" : normalized;
    }
}
