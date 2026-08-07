package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.List;

/**
 * 单个名次的奖励定义，属于 entitytracker 模块的 boss 伤害排名奖励子系统。
 * <p>
 * 持有名次与该名次对应的奖励动作列表，由排名奖励设置解析并供结算服务执行。
 *
 * @param rank    名次（从 1 开始）
 * @param actions 该名次的奖励动作列表
 */
public record BossDamageRankRewardDefinition(
    int rank,
    List<BossDamageRewardAction> actions
) {

    /**
     * 返回指定名次的空奖励定义（无任何动作）。
     *
     * @param rank 名次，小于 1 时会被修正为 1
     * @return 空奖励定义
     */
    public static BossDamageRankRewardDefinition empty(int rank) {
        return new BossDamageRankRewardDefinition(Math.max(1, rank), List.of());
    }
}

