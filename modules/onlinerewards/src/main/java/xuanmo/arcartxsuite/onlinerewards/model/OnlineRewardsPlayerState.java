package xuanmo.arcartxsuite.onlinerewards.model;

/**
 * 玩家在线奖励运行时状态。
 * <p>
 * 属于 {@code onlinerewards} 模块，承载单个玩家的当日/周/月/总在线时长、奖励阶段、
 * 签到连续与累计天数、补签卡、离线储蓄分钟数及周期奖励领取记录等全部可变状态。
 * 该类为可变对象，由 {@code OnlineRewardsStateEngine} 与 {@code OnlineRewardsService} 维护。
 */
public final class OnlineRewardsPlayerState {

    private String playerName;
    private String rewardDate;
    private int onlineMinutes;
    private int rewardStage;
    private String weekKey;
    private int weekMinutes;
    private String monthKey;
    private int monthMinutes;
    private int totalMinutes;
    private String lastSignInDate;
    private int signInStreak;
    private int signInTotal;
    private int makeupCards;
    private double timeBonusRemainder;
    private int offlineSavingsMinutes;
    private java.util.Set<String> claimedWeeklyRewardIds;
    private java.util.Set<String> claimedMonthlyRewardIds;

    /**
     * 创建一个所有字段为默认初始值的新状态。
     */
    public OnlineRewardsPlayerState() {
        this("", "", 0, 0, "", 0, "", 0, 0, "", 0, 0, 0, 0.0D, 0, java.util.Set.of(), java.util.Set.of());
    }

    /**
     * 以全字段构造玩家状态，所有数值字段会被约束为非负，字符串字段 null 转空串。
     *
     * @param playerName             玩家名称
     * @param rewardDate             当前奖励日期
     * @param onlineMinutes          当日在线分钟数
     * @param rewardStage            已领取的阶段奖励序号
     * @param weekKey                当前周键
     * @param weekMinutes            本周在线分钟数
     * @param monthKey               当前月键
     * @param monthMinutes           本月在线分钟数
     * @param totalMinutes           历史总在线分钟数
     * @param lastSignInDate         上次签到日期
     * @param signInStreak           连续签到天数
     * @param signInTotal            累计签到天数
     * @param makeupCards            补签卡数量
     * @param timeBonusRemainder     时长倍率累计余数
     * @param offlineSavingsMinutes  离线储蓄分钟数
     * @param claimedWeeklyRewardIds 已领取的周奖励 id 集合
     * @param claimedMonthlyRewardIds 已领取的月奖励 id 集合
     */
    public OnlineRewardsPlayerState(
        String playerName,
        String rewardDate,
        int onlineMinutes,
        int rewardStage,
        String weekKey,
        int weekMinutes,
        String monthKey,
        int monthMinutes,
        int totalMinutes,
        String lastSignInDate,
        int signInStreak,
        int signInTotal,
        int makeupCards,
        double timeBonusRemainder,
        int offlineSavingsMinutes,
        java.util.Set<String> claimedWeeklyRewardIds,
        java.util.Set<String> claimedMonthlyRewardIds
    ) {
        this.playerName = playerName == null ? "" : playerName;
        this.rewardDate = rewardDate == null ? "" : rewardDate;
        this.onlineMinutes = Math.max(0, onlineMinutes);
        this.rewardStage = Math.max(0, rewardStage);
        this.weekKey = weekKey == null ? "" : weekKey;
        this.weekMinutes = Math.max(0, weekMinutes);
        this.monthKey = monthKey == null ? "" : monthKey;
        this.monthMinutes = Math.max(0, monthMinutes);
        this.totalMinutes = Math.max(0, totalMinutes);
        this.lastSignInDate = lastSignInDate == null ? "" : lastSignInDate;
        this.signInStreak = Math.max(0, signInStreak);
        this.signInTotal = Math.max(0, signInTotal);
        this.makeupCards = Math.max(0, makeupCards);
        this.timeBonusRemainder = Math.max(0.0D, timeBonusRemainder);
        this.offlineSavingsMinutes = Math.max(0, offlineSavingsMinutes);
        this.claimedWeeklyRewardIds = claimedWeeklyRewardIds == null ? java.util.Set.of() : java.util.Set.copyOf(claimedWeeklyRewardIds);
        this.claimedMonthlyRewardIds = claimedMonthlyRewardIds == null ? java.util.Set.of() : java.util.Set.copyOf(claimedMonthlyRewardIds);
    }

    public String playerName() {
        return playerName;
    }

    public String rewardDate() {
        return rewardDate;
    }

    public int onlineMinutes() {
        return onlineMinutes;
    }

    public int rewardStage() {
        return rewardStage;
    }

    public String weekKey() {
        return weekKey;
    }

    public int weekMinutes() {
        return weekMinutes;
    }

    public String monthKey() {
        return monthKey;
    }

    public int monthMinutes() {
        return monthMinutes;
    }

    public int totalMinutes() {
        return totalMinutes;
    }

    public String lastSignInDate() {
        return lastSignInDate;
    }

    public int signInStreak() {
        return signInStreak;
    }

    public int signInTotal() {
        return signInTotal;
    }

    public int makeupCards() {
        return makeupCards;
    }

    public double timeBonusRemainder() {
        return timeBonusRemainder;
    }

    public int offlineSavingsMinutes() {
        return offlineSavingsMinutes;
    }

    public java.util.Set<String> claimedWeeklyRewardIds() {
        return claimedWeeklyRewardIds;
    }

    public java.util.Set<String> claimedMonthlyRewardIds() {
        return claimedMonthlyRewardIds;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName == null ? "" : playerName;
    }

    public void setRewardDate(String rewardDate) {
        this.rewardDate = rewardDate == null ? "" : rewardDate;
    }

    public void setOnlineMinutes(int onlineMinutes) {
        this.onlineMinutes = Math.max(0, onlineMinutes);
    }

    public void setRewardStage(int rewardStage) {
        this.rewardStage = Math.max(0, rewardStage);
    }

    public void setWeekKey(String weekKey) {
        this.weekKey = weekKey == null ? "" : weekKey;
    }

    public void setWeekMinutes(int weekMinutes) {
        this.weekMinutes = Math.max(0, weekMinutes);
    }

    public void setMonthKey(String monthKey) {
        this.monthKey = monthKey == null ? "" : monthKey;
    }

    public void setMonthMinutes(int monthMinutes) {
        this.monthMinutes = Math.max(0, monthMinutes);
    }

    public void setTotalMinutes(int totalMinutes) {
        this.totalMinutes = Math.max(0, totalMinutes);
    }

    public void setLastSignInDate(String lastSignInDate) {
        this.lastSignInDate = lastSignInDate == null ? "" : lastSignInDate;
    }

    public void setSignInStreak(int signInStreak) {
        this.signInStreak = Math.max(0, signInStreak);
    }

    public void setSignInTotal(int signInTotal) {
        this.signInTotal = Math.max(0, signInTotal);
    }

    public void setMakeupCards(int makeupCards) {
        this.makeupCards = Math.max(0, makeupCards);
    }

    public void setTimeBonusRemainder(double timeBonusRemainder) {
        this.timeBonusRemainder = Math.max(0.0D, timeBonusRemainder);
    }

    public void setOfflineSavingsMinutes(int offlineSavingsMinutes) {
        this.offlineSavingsMinutes = Math.max(0, offlineSavingsMinutes);
    }

    public void setClaimedWeeklyRewardIds(java.util.Set<String> claimedWeeklyRewardIds) {
        this.claimedWeeklyRewardIds = claimedWeeklyRewardIds == null ? java.util.Set.of() : java.util.Set.copyOf(claimedWeeklyRewardIds);
    }

    public void setClaimedMonthlyRewardIds(java.util.Set<String> claimedMonthlyRewardIds) {
        this.claimedMonthlyRewardIds = claimedMonthlyRewardIds == null ? java.util.Set.of() : java.util.Set.copyOf(claimedMonthlyRewardIds);
    }

    /**
     * 创建当前状态的深拷贝。
     *
     * @return 与当前状态字段一致的新实例
     */
    public OnlineRewardsPlayerState copy() {
        return new OnlineRewardsPlayerState(
            playerName,
            rewardDate,
            onlineMinutes,
            rewardStage,
            weekKey,
            weekMinutes,
            monthKey,
            monthMinutes,
            totalMinutes,
            lastSignInDate,
            signInStreak,
            signInTotal,
            makeupCards,
            timeBonusRemainder,
            offlineSavingsMinutes,
            claimedWeeklyRewardIds,
            claimedMonthlyRewardIds
        );
    }
}
