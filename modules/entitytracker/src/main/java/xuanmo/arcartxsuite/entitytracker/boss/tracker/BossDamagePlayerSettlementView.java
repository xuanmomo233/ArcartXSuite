package xuanmo.arcartxsuite.entitytracker.boss.tracker;

/**
 * 玩家最近一次结算的视图，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 聚合结算记录与该玩家在结算中的条目，供占位符和命令查询使用。
 *
 * @param settlement 结算记录
 * @param entry      该玩家在结算中的条目
 */
public record BossDamagePlayerSettlementView(
    BossDamageSettlementRecord settlement,
    BossDamageSettlementEntry entry
) {

    private static final BossDamagePlayerSettlementView EMPTY = new BossDamagePlayerSettlementView(
        BossDamageSettlementRecord.empty(),
        BossDamageSettlementEntry.empty()
    );

    /**
     * 返回空的结算视图。
     *
     * @return 空视图
     */
    public static BossDamagePlayerSettlementView empty() {
        return EMPTY;
    }
}

