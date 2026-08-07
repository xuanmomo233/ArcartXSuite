package xuanmo.arcartxsuite.loginview.storage;

import java.util.UUID;

/**
 * 登录会话记录，用于免密自动登录。
 *
 * @param uuid       玩家 UUID
 * @param playerName 玩家名
 * @param ip         会话建立 IP
 * @param createdAt  创建时间戳（毫秒）
 * @param expiresAt  过期时间戳（毫秒）
 */
public record LoginViewSession(
    UUID uuid,
    String playerName,
    String ip,
    long createdAt,
    long expiresAt
) {}
