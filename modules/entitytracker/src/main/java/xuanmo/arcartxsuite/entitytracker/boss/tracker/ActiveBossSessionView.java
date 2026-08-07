package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.UUID;

/**
 * 活跃 Boss 会话的只读视图，属于 entitytracker 模块的 boss 追踪子系统。
 * <p>
 * 用于管理命令展示当前活跃 Boss 的概要信息。
 *
 * @param entityUuid         Boss 实体 UUID
 * @param mythicMobId        MythicMob ID
 * @param displayName        Boss 显示名称
 * @param health             当前血量
 * @param maxHealth          最大血量
 * @param participantCount   参与排名的玩家数
 * @param trackedPlayerCount 被追踪的玩家总数
 * @param totalDamage        总伤害量
 */
public record ActiveBossSessionView(
    UUID entityUuid,
    String mythicMobId,
    String displayName,
    double health,
    double maxHealth,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage
) {
}

