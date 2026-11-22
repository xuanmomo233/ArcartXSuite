package xuanmo.arcartxsuite.chat.service;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.model.ChatEnvelope;
import xuanmo.arcartxsuite.chat.model.ChatMessageReport;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;

/** Recent chat is limited to each actual recipient. Only submitted reports are stored durably. */
final class ChatModeration {
    private static final int MAX_RECENT = 32;
    private static final Duration HISTORY_LIFETIME = Duration.ofMinutes(10);
    private final Map<UUID, ArrayDeque<Seen>> recent = new HashMap<>();
    private final ChatService chat;
    private final ChatRepository repository;

    ChatModeration(ChatService chat, ChatRepository repository) {
        this.chat = chat;
        this.repository = repository;
    }

    void record(Player player, ChatChannelDefinition channel, ChatEnvelope envelope) {
        var party = channel.mode() == ChatChannelMode.PARTY ? chat.partyOf(player) : null;
        record(player, describe(channel, envelope, party == null ? null : party.id()));
    }

    Seen describe(ChatChannelDefinition channel, ChatEnvelope envelope, UUID partyId) {
        UUID sender;
        try {
            sender = UUID.fromString(envelope.senderUuid());
            UUID.fromString(envelope.messageId());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return new Seen(envelope.messageId(), channel.id(), channel.audienceKey(), sender, envelope.senderName(),
            ChatColor.stripColor(envelope.rawMessage()), partyId, Instant.now());
    }

    void record(Player player, Seen message) {
        if (message == null) return;
        var queue = recent.computeIfAbsent(player.getUniqueId(), key -> new ArrayDeque<>());
        if (queue.size() == MAX_RECENT) queue.removeLast();
        queue.addFirst(message);
    }

    void forget(UUID player) {
        recent.remove(player);
    }

    void history(Player player, String filter) {
        int displayed = 0;
        var seen = recent.get(player.getUniqueId());
        if (seen != null) {
            for (Seen message : seen) {
                if ((!filter.isBlank() && !message.channel().equalsIgnoreCase(filter)) || !visible(player, message)) continue;
                player.sendMessage(chat.message("moderation.history-line", message.id().substring(0, 8),
                    message.channel(), message.senderName(), message.text()));
                if (++displayed == 10) break;
            }
        }
        if (displayed == 0) player.sendMessage(chat.message("moderation.empty"));
    }

    void report(Player player, String key, String reason) {
        var seen = recent.get(player.getUniqueId());
        if (seen == null || !key.matches("[0-9a-fA-F-]{8,36}") || reason.isBlank() || reason.length() > 255) {
            player.sendMessage(chat.message("moderation.report-usage"));
            return;
        }
        var matches = seen.stream().filter(message -> message.id().startsWith(key.toLowerCase(java.util.Locale.ROOT))
            && !message.sender().equals(player.getUniqueId()) && visible(player, message)).toList();
        if (matches.size() != 1) {
            player.sendMessage(chat.message("moderation.report-missing"));
            return;
        }
        Seen message = matches.get(0);
        UUID reportId = UUID.randomUUID();
        try {
            boolean saved = repository.saveReport(new ChatMessageReport(reportId, player.getUniqueId(), message.sender(),
                message.senderName(), message.id(), message.channel(), message.text(), ChatColor.stripColor(reason), Instant.now()));
            player.sendMessage(chat.message(saved ? "moderation.report-sent" : "moderation.report-duplicate", reportId));
        } catch (SQLException exception) {
            unavailable(player, exception);
        }
    }

    private boolean visible(Player player, Seen message) {
        if (message.createdAt().plus(HISTORY_LIFETIME).isBefore(Instant.now())) return false;
        ChatChannelDefinition channel = chat.configuration().channel(message.channel());
        if (channel == null || !channel.audienceKey().equals(message.audience()) || !chat.canReceiveChannel(channel, player)) return false;
        if (message.partyId() == null) return channel.mode() != ChatChannelMode.PARTY;
        var party = chat.partyOf(player);
        return party != null && message.partyId().equals(party.id());
    }

    void inspect(CommandSender operator, String action, String argument) {
        if (operator instanceof Player player && (!AuthAccess.admitted(player.getUniqueId())
                || !(player.hasPermission("arcartxsuite.admin") || player.hasPermission("arcartxsuite.chat.review")))) {
            operator.sendMessage(chat.message("common.no-permission"));
            return;
        }
        try {
            switch (action) {
                case "mutehistory" -> {
                    var records = repository.moderationHistory(argument, 20);
                    if (records.isEmpty()) operator.sendMessage(chat.message("moderation.empty"));
                    for (ChatModerationReceipt record : records) printReceipt(operator, record);
                }
                case "receipt" -> {
                    var receipt = repository.findModerationReceipt(UUID.fromString(argument));
                    if (receipt.isPresent()) printReceipt(operator, receipt.get());
                    else operator.sendMessage(chat.message("moderation.empty"));
                }
                case "reports" -> {
                    if (!argument.isBlank()) {
                        var report = repository.findReport(UUID.fromString(argument));
                        if (report.isPresent()) printReport(operator, report.get());
                        else operator.sendMessage(chat.message("moderation.empty"));
                        return;
                    }
                    var reports = repository.loadOpenReports(20);
                    if (reports.isEmpty()) operator.sendMessage(chat.message("moderation.empty"));
                    for (ChatMessageReport report : reports) {
                        operator.sendMessage(chat.message("moderation.report-summary", report.id(), report.channel(),
                            report.senderName(), report.createdAt()));
                    }
                }
                case "closereport" -> operator.sendMessage(chat.message(
                    repository.closeReport(UUID.fromString(argument), operator.getName())
                        ? "moderation.report-closed" : "moderation.empty"));
                default -> operator.sendMessage(chat.message("moderation.review-usage"));
            }
        } catch (IllegalArgumentException exception) {
            operator.sendMessage(chat.message("moderation.review-usage"));
        } catch (SQLException exception) {
            unavailable(operator, exception);
        }
    }

    private void printReceipt(CommandSender operator, ChatModerationReceipt receipt) {
        operator.sendMessage(chat.message("moderation.receipt-line", receipt.id(), receipt.action(), receipt.playerName(),
            receipt.operator(), receipt.node(), receipt.createdAt(), receipt.expiresAt() == null ? "-" : receipt.expiresAt(),
            receipt.reason()));
    }

    private void printReport(CommandSender operator, ChatMessageReport report) {
        operator.sendMessage(chat.message("moderation.report-summary", report.id(), report.channel(),
            report.senderName(), report.createdAt()));
        operator.sendMessage(chat.message("moderation.report-detail", report.reporterId(), report.text(), report.reason()));
    }

    private void unavailable(CommandSender operator, SQLException exception) {
        chat.logStorageFailure(exception);
        operator.sendMessage(chat.message("result.storage-unavailable"));
    }

    record Seen(String id, String channel, String audience, UUID sender, String senderName, String text,
                        UUID partyId, Instant createdAt) {}
}
