package xuanmo.arcartxsuite.fishing.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.script.XpCurve;

/**
 * 玩家钓鱼赛季数据，按赛季 ID 隔离，赛季结束时重置。
 * <p>
 * 经验模型为"当前级内经验"：升级时扣减当前级所需经验，剩余经验进入下一级。
 * 天赋点与天赋树点亮状态也属于赛季数据，随赛季重置。
 *
 * @param uuid               玩家 UUID
 * @param seasonId           赛季 ID
 * @param level              当前赛季等级
 * @param currentXp          当前级内已积累经验（升级时扣减）
 * @param talentPoints       总获得天赋点（等级÷3 累计）
 * @param unspentTalentPoints 未分配天赋点
 * @param talents            已点亮天赋节点 ID 集合
 * @param claimedRewards     已领取的等级奖励 ID 集合
 */
public record FishingSeasonData(
    @NotNull UUID uuid,
    @NotNull String seasonId,
    int level,
    int currentXp,
    int talentPoints,
    int unspentTalentPoints,
    @NotNull Set<String> talents,
    @NotNull Set<String> claimedRewards
) {

    public FishingSeasonData {
        if (talents == null) talents = new HashSet<>();
        if (claimedRewards == null) claimedRewards = new HashSet<>();
        talents = new HashSet<>(talents);
        claimedRewards = new HashSet<>(claimedRewards);
    }

    public static @NotNull FishingSeasonData empty(@NotNull UUID uuid, @NotNull String seasonId) {
        return new FishingSeasonData(uuid, seasonId, 1, 0, 0, 0, new HashSet<>(), new HashSet<>());
    }

    /**
     * 判断是否可升级到下一级。
     *
     * @param xpCurve 经验曲线
     * @param maxLevel 赛季最大等级
     * @return {@code true} 表示当前经验已满足升级条件且未到上限
     */
    public boolean canLevelUp(@NotNull XpCurve xpCurve, int maxLevel) {
        if (level >= maxLevel) return false;
        return currentXp >= xpCurve.xpRequiredForLevelOr(level + 1, Integer.MAX_VALUE);
    }

    /**
     * 升级一级：等级+1，扣减当前级所需经验，剩余经验保留。
     *
     * @param xpCurve 经验曲线
     * @return 升级后的新实例
     */
    public @NotNull FishingSeasonData withLevelUp(@NotNull XpCurve xpCurve) {
        int required = xpCurve.xpRequiredForLevelOr(level + 1, Integer.MAX_VALUE);
        int remaining = Math.max(0, currentXp - required);
        int newLevel = level + 1;
        // 每3级获得1点天赋点
        int newTalentPoints = talentPoints;
        int newUnspent = unspentTalentPoints;
        if (newLevel % 3 == 0) {
            newTalentPoints = talentPoints + 1;
            newUnspent = unspentTalentPoints + 1;
        }
        return new FishingSeasonData(uuid, seasonId, newLevel, remaining,
            newTalentPoints, newUnspent, talents, claimedRewards);
    }

    /**
     * 累加经验并自动连续升级。
     * <p>
     * <b>注意</b>：此方法仅做等级/经验/天赋点计算，不触发升级事件或奖励发放。
     * 调用方应在服务层（{@code FishingService}）处理升级副作用。
     * 满级后经验截断到 {@code cap - 1}，与 battlepass 行为一致。
     *
     * @param xp      增加的经验值
     * @param xpCurve 经验曲线
     * @param maxLevel 赛季最大等级
     * @return 更新后的新实例
     */
    public @NotNull FishingSeasonData withXpAdded(int xp, @NotNull XpCurve xpCurve, int maxLevel) {
        int newXp = currentXp + xp;
        int newLevel = level;
        int newTalentPoints = talentPoints;
        int newUnspent = unspentTalentPoints;

        // 逐级扣减升级
        while (newLevel < maxLevel) {
            int required = xpCurve.xpRequiredForLevelOr(newLevel + 1, Integer.MAX_VALUE);
            if (newXp < required) break;
            newXp -= required;
            newLevel++;
            // 每3级获得1点天赋点
            if (newLevel % 3 == 0) {
                newTalentPoints++;
                newUnspent++;
            }
        }
        // 满级后截断到当前级上限 - 1，防止经验无限累加
        if (newLevel >= maxLevel) {
            int cap = xpCurve.xpRequiredForLevelOr(maxLevel, Integer.MAX_VALUE);
            newXp = Math.min(newXp, Math.max(0, cap - 1));
        }
        return new FishingSeasonData(uuid, seasonId, newLevel, newXp,
            newTalentPoints, newUnspent, talents, claimedRewards);
    }

    /**
     * 点亮一个天赋节点（消耗1点未分配天赋点）。
     *
     * @param nodeId 天赋节点 ID
     * @return 更新后的新实例，天赋点不足时返回原实例
     */
    public @NotNull FishingSeasonData withTalentUnlocked(@NotNull String nodeId) {
        if (unspentTalentPoints <= 0 || talents.contains(nodeId)) return this;
        Set<String> newTalents = new HashSet<>(talents);
        newTalents.add(nodeId);
        return new FishingSeasonData(uuid, seasonId, level, currentXp,
            talentPoints, unspentTalentPoints - 1, newTalents, claimedRewards);
    }

    /**
     * 重置天赋树（退还所有已分配天赋点）。
     *
     * @return 更新后的新实例
     */
    public @NotNull FishingSeasonData withTalentsReset() {
        return new FishingSeasonData(uuid, seasonId, level, currentXp,
            talentPoints, talentPoints, new HashSet<>(), claimedRewards);
    }

    /**
     * 标记已领取等级奖励。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的新实例
     */
    public @NotNull FishingSeasonData withRewardClaimed(@NotNull String rewardId) {
        Set<String> newClaimed = new HashSet<>(claimedRewards);
        newClaimed.add(rewardId);
        return new FishingSeasonData(uuid, seasonId, level, currentXp,
            talentPoints, unspentTalentPoints, talents, newClaimed);
    }

    /**
     * 返回当前级升级所需经验。
     *
     * @param xpCurve 经验曲线
     * @return 所需经验值
     */
    public int xpForNextLevel(@NotNull XpCurve xpCurve) {
        return xpCurve.xpRequiredForLevelOr(level + 1, Integer.MAX_VALUE);
    }
}
