package xuanmo.arcartxsuite.chat.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 玩家档案记录，存储玩家最近已知名称、最后在线时间与所在服务器。
 * <p>
 * 由 {@link ChatRepository} 持久化，用于跨服场景下按名称查找玩家 UUID。
 *
 * @param playerUuid    玩家 UUID
 * @param lastKnownName 最近已知名称
 * @param lastSeenAt    最后在线时间
 * @param lastServer    最后所在服务器 ID
 */
public record ChatPlayerProfile(
    UUID playerUuid,
    String lastKnownName,
    Instant lastSeenAt,
    String lastServer
) {
}
