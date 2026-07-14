package xuanmo.arcartxsuite.mail.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailPage;

public final class MailLogsPacketFactory {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private MailLogsPacketFactory() {
    }

    public static Map<String, Object> build(MailPage<MailLogEntry> page) {
        return build(page, null);
    }

    public static Map<String, Object> build(MailPage<MailLogEntry> page, MessageProvider messages) {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> entries = new LinkedHashMap<>();
        int index = 0;
        for (MailLogEntry entry : page.entries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Long.toString(entry.id()));
            item.put("type", entry.type());
            item.put("type_text", typeText(entry.type(), messages));
            item.put("content", entry.content());
            item.put("created_at", TIME_FORMATTER.format(entry.createdAt()));
            entries.put(Integer.toString(index), item);
            index++;
        }
        packet.put("logs", entries);
        packet.put("page", page.page());
        packet.put("page_size", page.pageSize());
        packet.put("max_page", page.totalPages());
        packet.put("total_count", page.totalItems());
        packet.put("maxLogCount", page.pageSize());
        return packet;
    }

    private static String typeText(String type, MessageProvider messages) {
        if (type == null) {
            return message(messages, "ui.log-type", "日志");
        }
        return switch (type.toLowerCase()) {
            case "send" -> message(messages, "ui.log-send", "寄件");
            case "claim" -> message(messages, "ui.log-claim", "领取");
            case "delete" -> message(messages, "ui.log-delete", "删除");
            case "preset" -> message(messages, "ui.log-preset", "预设");
            case "cdk" -> message(messages, "ui.log-cdk", "CDK");
            default -> message(messages, "ui.log-type", "日志");
        };
    }

    private static String message(MessageProvider messages, String key, String fallback) {
        return messages == null ? fallback : messages.get(key);
    }
}
