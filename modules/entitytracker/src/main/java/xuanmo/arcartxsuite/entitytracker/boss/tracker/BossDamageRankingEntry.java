package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.UUID;

/**
 * 伤害排名单条记录，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 描述单个玩家在排名中的名次、伤害、伤害占比及承伤等信息。
 *
 * @param playerUuid    玩家 UUID
 * @param playerName    玩家名称
 * @param rank          名次（从 1 开始，0 表示未入榜）
 * @param qualified     是否通过门槛入榜
 * @param damage        造成伤害总量
 * @param damagePercent 伤害占总伤害的百分比
 * @param takenDamage   承受伤害总量
 */
public record BossDamageRankingEntry(
    UUID playerUuid,
    String playerName,
    int rank,
    boolean qualified,
    double damage,
    double damagePercent,
    double takenDamage
) {

    private static final BossDamageRankingEntry EMPTY = new BossDamageRankingEntry(
        new UUID(0L, 0L),
        "",
        0,
        false,
        0.0D,
        0.0D,
        0.0D
    );

    /**
     * 返回空的排名条目（全零值）。
     *
     * @return 空条目
     */
    public static BossDamageRankingEntry empty() {
        return EMPTY;
    }
}

