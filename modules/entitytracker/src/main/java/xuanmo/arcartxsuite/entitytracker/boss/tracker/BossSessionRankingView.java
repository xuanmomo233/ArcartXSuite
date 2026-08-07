package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.UUID;

/**
 * Boss 会话排名视图，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 用于管理命令展示单个 Boss 会话的排名概要信息。
 *
 * @param entityUuid         Boss 实体 UUID
 * @param mythicMobId        MythicMob ID
 * @param displayName        Boss 显示名称
 * @param health             当前血量
 * @param maxHealth          最大血量
 * @param participantCount   参与排名的玩家数
 * @param trackedPlayerCount 被追踪的玩家总数
 * @param totalDamage        总伤害量
 * @param trackedEntries     被追踪的排名条目列表
 */
public record BossSessionRankingView(
    UUID entityUuid,
    String mythicMobId,
    String displayName,
    double health,
    double maxHealth,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    List<BossDamageRankingEntry> trackedEntries
) {
}

