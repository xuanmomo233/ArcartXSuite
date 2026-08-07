package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.UUID;

/**
 * 伤害结算单条记录，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 在排名条目基础上增加奖励发放状态和各动作执行结果，
 * 是结算记录中每个玩家的最终结算数据。
 *
 * @param playerUuid     玩家 UUID
 * @param playerName     玩家名称
 * @param rank           名次（从 1 开始，0 表示未入榜）
 * @param qualified      是否通过门槛入榜
 * @param damage         造成伤害总量
 * @param damagePercent  伤害占总伤害的百分比
 * @param takenDamage    承受伤害总量
 * @param rewarded       是否成功发放奖励
 * @param rewardFailure  奖励失败原因（成功时为空）
 * @param actionResults  各奖励动作的执行结果列表
 */
public record BossDamageSettlementEntry(
    UUID playerUuid,
    String playerName,
    int rank,
    boolean qualified,
    double damage,
    double damagePercent,
    double takenDamage,
    boolean rewarded,
    String rewardFailure,
    List<BossDamageSettlementActionResult> actionResults
) {

    private static final BossDamageSettlementEntry EMPTY = new BossDamageSettlementEntry(
        new UUID(0L, 0L),
        "",
        0,
        false,
        0.0D,
        0.0D,
        0.0D,
        false,
        "",
        List.of()
    );

    /**
     * 返回空的结算条目（全零值）。
     *
     * @return 空条目
     */
    public static BossDamageSettlementEntry empty() {
        return EMPTY;
    }

    /**
     * 从排名条目构造结算条目。
     *
     * @param entry          排名条目，为 {@code null} 时返回空条目
     * @param rewarded       是否成功发放奖励
     * @param rewardFailure  奖励失败原因
     * @param actionResults  各奖励动作的执行结果
     * @return 构造的结算条目
     */
    public static BossDamageSettlementEntry fromRankingEntry(
        BossDamageRankingEntry entry,
        boolean rewarded,
        String rewardFailure,
        List<BossDamageSettlementActionResult> actionResults
    ) {
        if (entry == null) {
            return empty();
        }
        return new BossDamageSettlementEntry(
            entry.playerUuid(),
            entry.playerName(),
            entry.rank(),
            entry.qualified(),
            entry.damage(),
            entry.damagePercent(),
            entry.takenDamage(),
            rewarded,
            rewardFailure == null ? "" : rewardFailure,
            actionResults == null ? List.of() : List.copyOf(actionResults)
        );
    }

    /**
     * 返回名次的显示文本（如 "#1"），未入榜时返回 "-"。
     *
     * @return 名次显示文本
     */
    public String rankText() {
        return rank > 0 ? "#" + rank : "-";
    }
}

