package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害排名快照，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 持有排名开关、条目上限、参与者统计、总伤害以及已排序的排名条目列表，
 * 是 BossSession 在某一时刻的排名状态不可变快照。
 *
 * @param enabled           是否启用排名
 * @param entryLimit        排名条目上限
 * @param participantCount  通过门槛的参与者数量
 * @param trackedPlayerCount 被追踪的玩家总数
 * @param totalDamage       总伤害量
 * @param rankedEntries     已排序的入榜条目列表
 * @param trackedEntries    所有被追踪的条目列表（含未入榜）
 * @param entriesByPlayer   按玩家 UUID 索引的条目映射
 */
public record BossDamageRankingSnapshot(
    boolean enabled,
    int entryLimit,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    List<BossDamageRankingEntry> rankedEntries,
    List<BossDamageRankingEntry> trackedEntries,
    Map<UUID, BossDamageRankingEntry> entriesByPlayer
) {

    /**
     * 返回空的排名快照。
     *
     * @param enabled   是否启用排名
     * @param entryLimit 条目上限
     * @return 空快照
     */
    public static BossDamageRankingSnapshot empty(boolean enabled, int entryLimit) {
        return new BossDamageRankingSnapshot(
            enabled,
            Math.max(1, entryLimit),
            0,
            0,
            0.0D,
            List.of(),
            List.of(),
            Map.of()
        );
    }

    /**
     * 按名次获取排名条目。
     *
     * @param rank 名次（从 1 开始）
     * @return 对应条目，越界时返回空条目
     */
    public BossDamageRankingEntry entry(int rank) {
        int zeroBased = rank - 1;
        if (zeroBased < 0 || zeroBased >= rankedEntries.size()) {
            return BossDamageRankingEntry.empty();
        }
        return rankedEntries.get(zeroBased);
    }

    /**
     * 按玩家 UUID 获取排名条目。
     *
     * @param playerUuid 玩家 UUID
     * @return 对应条目，未找到时返回空条目
     */
    public BossDamageRankingEntry entry(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamageRankingEntry.empty();
        }
        return entriesByPlayer.getOrDefault(playerUuid, BossDamageRankingEntry.empty());
    }
}

