package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;

/**
 * 伤害结算记录，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 记录一次 Boss 死亡结算的完整数据：结算 ID、时间、Boss 信息、
 * 参与者统计、总伤害、奖励设置以及所有玩家的结算条目。
 *
 * @param settlementId       结算唯一 ID
 * @param settledAtMillis    结算时间戳（毫秒）
 * @param mythicMobId        MythicMob ID
 * @param bossDisplayName    Boss 显示名称
 * @param entityUuid         Boss 实体 UUID 字符串
 * @param participantCount   通过门槛的参与者数量
 * @param trackedPlayerCount 被追踪的玩家总数
 * @param totalDamage        总伤害量
 * @param rewardsSettings    结算时使用的奖励设置
 * @param trackedEntries     所有被追踪的结算条目列表
 * @param entriesByPlayer    按玩家 UUID 索引的条目映射
 */
public record BossDamageSettlementRecord(
    String settlementId,
    long settledAtMillis,
    String mythicMobId,
    String bossDisplayName,
    String entityUuid,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    BossDamageRankingRewardsSettings rewardsSettings,
    List<BossDamageSettlementEntry> trackedEntries,
    Map<UUID, BossDamageSettlementEntry> entriesByPlayer
) {

    private static final BossDamageSettlementRecord EMPTY = new BossDamageSettlementRecord(
        "",
        0L,
        "",
        "",
        "",
        0,
        0,
        0.0D,
        BossDamageRankingRewardsSettings.defaults(),
        List.of(),
        Map.of()
    );

    /**
     * 返回空的结算记录。
     *
     * @return 空记录
     */
    public static BossDamageSettlementRecord empty() {
        return EMPTY;
    }

    /**
     * 按名次获取结算条目。
     *
     * @param rank 名次（从 1 开始）
     * @return 对应条目，未找到时返回空条目
     */
    public BossDamageSettlementEntry entry(int rank) {
        if (rank <= 0) {
            return BossDamageSettlementEntry.empty();
        }
        for (BossDamageSettlementEntry entry : trackedEntries) {
            if (entry.rank() == rank) {
                return entry;
            }
        }
        return BossDamageSettlementEntry.empty();
    }

    /**
     * 按玩家 UUID 获取结算条目。
     *
     * @param playerUuid 玩家 UUID
     * @return 对应条目，未找到时返回空条目
     */
    public BossDamageSettlementEntry entry(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamageSettlementEntry.empty();
        }
        return entriesByPlayer.getOrDefault(playerUuid, BossDamageSettlementEntry.empty());
    }

    /**
     * 返回第一名（伤害最高）的结算条目。
     *
     * @return 第一名条目，无数据时返回空条目
     */
    public BossDamageSettlementEntry topEntry() {
        return entry(1);
    }

    /**
     * 返回仅包含入榜条目（名次大于 0）的列表。
     *
     * @return 入榜条目列表
     */
    public List<BossDamageSettlementEntry> rankedEntries() {
        return trackedEntries.stream()
            .filter(entry -> entry.rank() > 0)
            .toList();
    }

    /**
     * 返回奖励发放的汇总文本（成功数与失败数）。
     *
     * @return 汇总文本
     */
    public String rewardSummary() {
        if (trackedEntries.isEmpty()) {
            return "无结算记录";
        }

        int successCount = 0;
        int failureCount = 0;
        for (BossDamageSettlementEntry entry : trackedEntries) {
            if (entry.rank() <= 0) {
                continue;
            }
            if (entry.rewarded()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        return "成功=" + successCount + " 失败=" + failureCount;
    }
}

