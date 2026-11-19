package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageReport(UUID id, UUID reporterId, UUID senderId, String senderName, String messageId,
                                String channel, String text, String reason, Instant createdAt) {}
