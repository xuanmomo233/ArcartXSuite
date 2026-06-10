package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    public boolean unlockedPremium() {
        return passTier == PassTier.PREMIUM || passTier == PassTier.DELUXE;
    }

    public boolean unlockedDeluxe() {
        return passTier == PassTier.DELUXE;
    }

    public float xpMultiplier() {
        return switch (passTier) {
            case FREE -> 1.0f;
            case PREMIUM -> 1.0f;
            case DELUXE -> 1.5f;
        };
    }

    public BattlePassPlayerProgress withLevel(int level) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, level, currentXp, passTier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    public BattlePassPlayerProgress withXp(int xp) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, xp, passTier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    public BattlePassPlayerProgress withPassTier(PassTier tier) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, tier,
            taskProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    public BattlePassPlayerProgress withTaskProgress(String taskId, int count) {
        Map<String, Integer> newProgresses = new HashMap<>(taskProgresses);
        newProgresses.put(taskId, count);
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            newProgresses, claimedRewards, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    public BattlePassPlayerProgress withClaimedReward(String rewardId) {
        Set<String> newClaimed = new HashSet<>(claimedRewards);
        newClaimed.add(rewardId);
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            taskProgresses, newClaimed, lastDailyResetDate, lastWeeklyResetDate, currentWeekNumber);
    }

    public BattlePassPlayerProgress withDailyReset(LocalDate date) {
        return new BattlePassPlayerProgress(playerUuid, seasonId, currentLevel, currentXp, passTier,
            new HashMap<>(), claimedRewards, date, lastWeeklyResetDate, currentWeekNumber);
    }

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

    public enum PassTier {
        FREE, PREMIUM, DELUXE
    }
}
