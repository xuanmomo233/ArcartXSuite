package xuanmo.arcartxsuite.mail.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;

public final class MailInboxPacketFactory {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private MailInboxPacketFactory() {
    }

    public static Map<String, Object> build(
        MailPage<MailMessage> mailPage,
        long selectedMailId,
        MailMailboxStats stats,
        MailInboxFilter filter,
        int pageClaimableCount
    ) {
        return build(mailPage, selectedMailId, stats, filter, pageClaimableCount, null);
    }

    public static Map<String, Object> build(
        MailPage<MailMessage> mailPage,
        long selectedMailId,
        MailMailboxStats stats,
        MailInboxFilter filter,
        int pageClaimableCount,
        MessageProvider messages
    ) {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> entries = new LinkedHashMap<>();
        MailMessage selected = null;
        List<MailMessage> mailEntries = mailPage.entries();

        int index = 0;
        for (MailMessage message : mailEntries) {
            if (message.id() == selectedMailId) {
                selected = message;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", Long.toString(message.id()));
            entry.put("subject", message.subject());
            entry.put("sender_name", message.senderName());
            entry.put("source_type", message.sourceType().name().toLowerCase());
            entry.put("source_text", sourceText(message.sourceType(), messages));
            entry.put("status", message.status().name().toLowerCase());
            entry.put("status_text", statusText(message.status(), messages));
            entry.put("created_at_text", TIME_FORMATTER.format(message.createdAt()));
            entry.put("expires_at_text", message.expiresAt() == null ? message(messages, "ui.expires-never", "永久") : TIME_FORMATTER.format(message.expiresAt()));
            entry.put("preview", preview(message.body()));
            entry.put("attachment_summary", attachmentSummary(message.attachments(), messages));
            entry.put("claimable", message.claimable());
            entry.put("unread", message.unread());
            entry.put("has_attachments", message.hasAttachments());
            entries.put(Integer.toString(index), entry);
            index++;
        }

        if (selected == null && !mailEntries.isEmpty()) {
            selected = mailEntries.get(0);
        }

        packet.put("messages", entries);
        packet.put("total_count", stats.totalCount());
        packet.put("unread_count", stats.unreadCount());
        packet.put("claimable_count", stats.claimableCount());
        packet.put("page_claimable_count", pageClaimableCount);
        packet.put("page", mailPage.page());
        packet.put("page_size", mailPage.pageSize());
        packet.put("max_page", mailPage.totalPages());
        packet.put("filter_mode", filter == null ? "all" : filter.name().toLowerCase());
        packet.put("selected_id", selected == null ? "" : Long.toString(selected.id()));
        packet.put("selected_subject", selected == null ? "" : selected.subject());
        packet.put("selected_sender_name", selected == null ? "" : selected.senderName());
        packet.put("selected_source_text", selected == null ? "" : sourceText(selected.sourceType(), messages));
        packet.put("selected_status_text", selected == null ? "" : statusText(selected.status(), messages));
        packet.put("selected_created_at", selected == null ? "" : TIME_FORMATTER.format(selected.createdAt()));
        packet.put("selected_expires_at", selected == null ? "" : (selected.expiresAt() == null ? message(messages, "ui.expires-never", "永久") : TIME_FORMATTER.format(selected.expiresAt())));
        packet.put("selected_body", bodyLines(selected == null ? "" : selected.body(), messages));
        packet.put("selected_attachment_summary", selected == null ? message(messages, "ui.no-attachments", "无附件") : attachmentSummary(selected.attachments(), messages));
        packet.put("selected_claimable", selected != null && selected.claimable());
        packet.put("maxMailCount", mailPage.pageSize());
        return packet;
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() > 42 ? singleLine.substring(0, 42) + "..." : singleLine;
    }

    private static List<String> bodyLines(String text, MessageProvider messages) {
        if (text == null || text.isBlank()) {
            return List.of(message(messages, "ui.no-body", "&8暂无正文"));
        }
        return text.lines()
            .map(String::stripTrailing)
            .map(line -> line.isBlank() ? " " : "&0" + line)
            .toList();
    }

    private static String attachmentSummary(List<MailAttachment> attachments, MessageProvider messages) {
        if (attachments == null || attachments.isEmpty()) {
            return message(messages, "ui.no-attachments", "无附件");
        }
        StringBuilder builder = new StringBuilder();
        for (MailAttachment attachment : attachments) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(attachment.description().isBlank() ? attachment.type().name() : attachment.description());
        }
        return builder.toString();
    }

    private static String sourceText(MailSourceType type, MessageProvider messages) {
        return switch (type) {
            case PLAYER -> message(messages, "ui.source-player", "玩家邮件");
            case PRESET -> message(messages, "ui.source-preset", "预设邮件");
            case CDK -> message(messages, "ui.source-cdk", "CDK 邮件");
            case SYSTEM -> message(messages, "ui.source-system", "系统邮件");
        };
    }

    private static String statusText(MailStatus status, MessageProvider messages) {
        return switch (status) {
            case UNREAD -> message(messages, "ui.status-unread", "未读");
            case READ -> message(messages, "ui.status-read", "已读");
            case CLAIMED -> message(messages, "ui.status-claimed", "已领取");
            case DELETED -> message(messages, "ui.status-deleted", "已删除");
            case EXPIRED -> message(messages, "ui.status-expired", "已过期");
        };
    }

    private static String message(MessageProvider messages, String key, String fallback) {
        return messages == null ? fallback : messages.get(key);
    }
}
