package xuanmo.arcartxsuite.conversation.service;

import ink.ptms.chemdah.core.conversation.PlayerReply;
import java.util.Locale;
import java.util.Map;

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
