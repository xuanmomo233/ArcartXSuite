package xuanmo.arcartxsuite.onlinerewards.model;

import java.util.UUID;

/**
 * 在线时长排行榜条目。
 * <p>
 * 属于 {@code onlinerewards} 模块，表示排行榜中单个玩家的唯一 id、名称及对应统计分钟数。
 *
 * @param playerUuid 玩家唯一 id
 * @param playerName 玩家名称
 * @param minutes    统计周期内的在线分钟数
 */
public record OnlineRewardsLeaderboardEntry(
    UUID playerUuid,
    String playerName,
    int minutes
) {
}
