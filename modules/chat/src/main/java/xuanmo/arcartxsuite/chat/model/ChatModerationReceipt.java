package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ChatModerationReceipt(UUID id, UUID playerId, String playerName, String action, String operator,
                                    String reason, String node, Instant createdAt, @Nullable Instant expiresAt) {}
