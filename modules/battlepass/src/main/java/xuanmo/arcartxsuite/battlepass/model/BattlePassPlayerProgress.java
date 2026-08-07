package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家战令进度 record，记录玩家在某赛季中的等级、经验、档位、任务进度及奖励领取状态。
 * <p>
 * 提供多个 {@code withXxx} 方法以不可变方式更新单个字段，适用于函数式更新场景。
 *
 * @param playerUuid          玩家 UUID
 * @param seasonId            赛季 ID
 * @param currentLevel        当前等级
 * @param currentXp           当前经验值（当前等级内已积累量）
 * @param passTier            战令档位（FREE/PREMIUM/DELUXE）
 * @param taskProgresses      任务进度映射（任务 ID → 完成次数）
 * @param claimedRewards      已领取奖励 ID 集合
 * @param lastDailyResetDate  上次每日重置日期
 * @param lastWeeklyResetDate 上次每周重置日期
 * @param currentWeekNumber   当前周序号
 */
public record BattlePassPlayerProgress(
    UUID playerUuid,
    String seasonId,
    int currentLevel,
    int currentXp,
    PassTier passTier,
    Map<String, Integer> taskProgresses,
    Set<String> claimedRewards,
    LocalDate lastDailyResetDate,
    LocalDate lastWeeklyResetDate,
    int currentWeekNumber
) {

    public BattlePassPlayerProgress {
        if (passTier == null) passTier = PassTier.FREE;
        if (taskProgresses == null) taskProgresses = new HashMap<>();
        if (claimedRewards == null) claimedRewards = new HashSet<>();
        if (lastDailyResetDate == null) lastDailyResetDate = LocalDate.now();
        if (lastWeeklyResetDate == null) lastWeeklyResetDate = LocalDate.now();
        if (currentWeekNumber < 1) currentWeekNumber = 1;
    }

    public BattlePassPlayerProgress(UUID playerUuid, String seasonId) {
        this(playerUuid, seasonId, 1, 0, PassTier.FREE, new HashMap<>(), new HashSet<>(),
            LocalDate.now(), LocalDate.now(), 1);
    }

    /**
     * 玩家是否已解锁高级档位（PREMIUM 或 DELUXE）。
     *
     * @return {@code true} 表示已解锁
     */
    public boolean unlockedPremium() {
        return passTier == PassTier.PREMIUM || passTier == PassTier.DELUXE;
    }

    /**
     * 玩家是否已解锁典藏档位（DELUXE）。
     *
     * @return {@code true} 表示已解锁
     */
    public boolean unlockedDeluxe() {
        return passTier == PassTier.DELUXE;
    }

    /**
     * 返回当前档位对应的经验获取倍率（DELUXE 为 1.5，其余为 1.0）。
     *
     * @return 经验倍率
     */
    public float xpMultiplier() {
        return switch (passTier) {
            case FREE -> 1.0f;
            case PREMIUM -> 1.0f;
            case DELUXE -> 1.5f;
        };
    }

    /**
     * 返回更新等级后的新进度实例。
     *
     * @param level 新等级
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withLevel(int level) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, level, currentXp, passTier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回更新经验值后的新进度实例。
     *
     * @param xp 新经验值
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withXp(int xp) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, xp, passTier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回更新档位后的新进度实例。
     *
     * @param tier 新档位
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withPassTier(PassTier tier) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, tier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回更新指定任务进度后的新进度实例。
     *
     * @param taskId 任务 ID
     * @param count  完成次数
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withTaskProgress(String taskId, int count) {
        Map<String, Integer> newProgresses = new HashMap<>(taskProgresses);
        newProgresses.put(taskId, count);
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            newProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回添加已领取奖励后的新进度实例。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withClaimedReward(String rewardId) {
        Set<String> newClaimed = new HashSet<>(claimedRewards);
        newClaimed.add(rewardId);
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            taskProgresses, newClaimed, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回执行每日重置后的新进度实例（清空任务进度，更新重置日期）。
     *
     * @param date 重置日期
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withDailyReset(LocalDate date) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            new HashMap<>(), claimedRewards, date, lastWeeklyResetDate, currentWeekNumber);
    }

    /**
     * 返回执行每周重置后的新进度实例（更新重置日期与周序号）。
     *
     * @param date        重置日期
     * @param weekNumber  新周序号
     * @return 更新后的进度实例
     */
    public BattlePassPlayerProgress withWeeklyReset(LocalDate date, int weekNumber) {
        Map<String, Integer> newProgresses = new HashMap<>();
        for (Map.Entry<String, Integer> entry : taskProgresses.entrySet()) {
            // 只保留赛季任务进度，清除周任务
            // 这里由上层业务决定过滤逻辑
            newProgresses.put(entry.getKey(), entry.getValue());
        }
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            newProgresses, claimedRewards, lastDailyResetDate, date, weekNumber);
    }

    /**
     * 战令档位枚举。
     * <p>
     * FREE 为免费档位，PREMIUM 为高级付费档位，DELUXE 为典藏付费档位（含 PREMIUM 权益）。
     */
    public enum PassTier {
        FREE, PREMIUM, DELUXE
    }
}
