package xuanmo.arcartxsuite.mail.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.model.MailPage;

public final class MailLogsPacketFactory {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private MailLogsPacketFactory() {
    }

    public static Map<String, Object> build(MailPage<MailLogEntry> page) {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> entries = new LinkedHashMap<>();
        for (MailLogEntry entry : page.entries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Long.toString(entry.id()));
            item.put("type", entry.type());
            item.put("type_text", typeText(entry.type()));
            item.put("content", entry.content());
            item.put("created_at", TIME_FORMATTER.format(entry.createdAt()));
            entries.put(Long.toString(entry.id()), item);
        }
        packet.put("logs", entries);
        packet.put("page", page.page());
        packet.put("page_size", page.pageSize());
        packet.put("max_page", page.totalPages());
        packet.put("total_count", page.totalItems());
        return packet;
    }

    private static String typeText(String type) {
        if (type == null) {
            return "日志";
        }
        return switch (type.toLowerCase()) {
            case "send" -> "寄件";
            case "claim" -> "领取";
            case "delete" -> "删除";
            case "preset" -> "预设";
            case "cdk" -> "CDK";
            default -> "日志";
        };
    }
}
