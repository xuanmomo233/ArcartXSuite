package xuanmo.arcartxsuite.mail.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;
import xuanmo.arcartxsuite.mail.util.MailItemSerializer;

/**
 * 收件箱界面数据包工厂，构建发送给客户端的收件箱列表与选中邮件详情数据包。
 * <p>
 * 关联模块：mail。所有方法为静态工具方法，封装邮件条目、分页、筛选与状态文本等 UI 数据。
 */
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
        return build(mailPage, selectedMailId, stats, filter, pageClaimableCount, null, 40, null);
    }

    public static Map<String, Object> build(
        MailPage<MailMessage> mailPage,
        long selectedMailId,
        MailMailboxStats stats,
        MailInboxFilter filter,
        int pageClaimableCount,
        MessageProvider messages
    ) {
        return build(mailPage, selectedMailId, stats, filter, pageClaimableCount, messages, 40, null);
    }

    public static Map<String, Object> build(
        MailPage<MailMessage> mailPage,
        long selectedMailId,
        MailMailboxStats stats,
        MailInboxFilter filter,
        int pageClaimableCount,
        MessageProvider messages,
        int inboxPreviewMaxChars
    ) {
        return build(mailPage, selectedMailId, stats, filter, pageClaimableCount, messages, inboxPreviewMaxChars, null);
    }

    public static Map<String, Object> build(
        MailPage<MailMessage> mailPage,
        long selectedMailId,
        MailMailboxStats stats,
        MailInboxFilter filter,
        int pageClaimableCount,
        MessageProvider messages,
        int inboxPreviewMaxChars,
        @Nullable IconResolver iconResolver
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
            entry.put("preview", preview(message.body(), inboxPreviewMaxChars));
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
        packet.put("selected_attachments", buildAttachmentList(selected, iconResolver, messages));
        packet.put("selected_claimable", selected != null && selected.claimable());
        packet.put("maxMailCount", mailPage.pageSize());
        return packet;
    }

    private static String preview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() > maxChars ? singleLine.substring(0, maxChars) + "..." : singleLine;
    }

    private static List<String> bodyLines(String text, MessageProvider messages) {
        if (text == null || text.isBlank()) {
            return List.of(message(messages, "ui.no-body", "&8暂无正文"));
        }
        return text.lines()
            .map(String::stripTrailing)
            .map(line -> line.isBlank() ? " " : line)
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

    /**
     * 构建选中邮件的附件列表数据，供 UI 端用 Slot 控件展示物品图标。
     * <p>
     * 物品附件：反序列化 itemData 后通过 IconResolver 序列化为 UI 可识别的 itemJson。
     * 货币附件：通过 IconResolver.resolveItemJson 解析 IconDefinition（支持 material / custom-model-data / texture 等）。
     *
     * @param selected     选中邮件，为 null 时返回空列表
     * @param iconResolver 图标解析器，为 null 时 itemJson 为空
     * @param messages     消息提供者
     * @return 附件列表，每项含 type/itemJson/description/amount 字段
     */
    private static Map<String, Object> buildAttachmentList(MailMessage selected, @Nullable IconResolver iconResolver, MessageProvider messages) {
        if (selected == null || selected.attachments() == null || selected.attachments().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        int index = 0;
        for (MailAttachment attachment : selected.attachments()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", attachment.type().name());
            entry.put("description", attachment.description().isBlank() ? attachment.type().name() : attachment.description());
            entry.put("amount", attachment.amount());
            entry.put("itemJson", buildAttachmentItemJson(attachment, iconResolver));
            result.put(Integer.toString(index), entry);
            index++;
        }
        return result;
    }

    /**
     * 将单个附件序列化为 UI 可渲染的 itemJson 字符串。
     * <p>
     * 物品附件：反序列化 Base64 itemData 后重建为 CraftItemStack，再通过 IconResolver.itemToJson 序列化。
     * 重建步骤是必要的——Bukkit 反序列化得到的 ItemStack 不是 CraftItemStack，
     * native item2json 无法读取其 ItemMeta 中的 displayName/lore，会导致 UI 悬浮显示原版名称而非自定义名称。
     * 货币附件：通过 IconResolver.resolveItemJson 解析 IconDefinition（支持完整 menu 风格 icon 配置）。
     * 货币附件无 IconDefinition 时回退为纸片 + 描述文本。
     */
    private static String buildAttachmentItemJson(MailAttachment attachment, @Nullable IconResolver iconResolver) {
        if (iconResolver == null) {
            return "";
        }
        try {
            if (attachment.isItem() && attachment.itemData() != null && !attachment.itemData().isBlank()) {
                ItemStack stack = MailItemSerializer.deserialize(attachment.itemData());
                // 重建为 CraftItemStack，确保 item2json 能正确序列化 ItemMeta 中的 displayName/lore
                ItemStack fresh = new ItemStack(stack.getType(), stack.getAmount());
                ItemMeta srcMeta = stack.getItemMeta();
                if (srcMeta != null) {
                    ItemMeta freshMeta = fresh.getItemMeta();
                    if (freshMeta != null) {
                        if (srcMeta.hasDisplayName()) {
                            freshMeta.setDisplayName(srcMeta.getDisplayName());
                        }
                        if (srcMeta.hasLore()) {
                            freshMeta.setLore(srcMeta.getLore());
                        }
                        if (srcMeta.hasCustomModelData()) {
                            freshMeta.setCustomModelData(srcMeta.getCustomModelData());
                        }
                        if (srcMeta.hasEnchants()) {
                            for (org.bukkit.enchantments.Enchantment ench : srcMeta.getEnchants().keySet()) {
                                freshMeta.addEnchant(ench, srcMeta.getEnchantLevel(ench), true);
                            }
                        }
                        fresh.setItemMeta(freshMeta);
                    }
                }
                return iconResolver.itemToJson(fresh);
            } else if (attachment.isCurrency()) {
                // 货币附件：优先使用 IconDefinition（menu 风格完整图标配置）
                if (attachment.icon() != null && attachment.icon().hasIcon()) {
                    return iconResolver.resolveItemJson(null, attachment.icon());
                }
                // 回退：纸片 + 描述文本
                ItemStack stack = new ItemStack(Material.PAPER, 1);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    String desc = attachment.description().isBlank() ? attachment.currencyId() : attachment.description();
                    meta.setDisplayName(desc);
                    stack.setItemMeta(meta);
                }
                return iconResolver.itemToJson(stack);
            }
            return "";
        } catch (Exception ignored) {
            return "";
        }
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
