package xuanmo.arcartxsuite.onlinerewards.model;

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

    public OnlineRewardsPlayerState() {
        this("", "", 0, 0, "", 0, "", 0, 0, "", 0, 0, 0, 0.0D);
    }

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
        double timeBonusRemainder
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
            timeBonusRemainder
        );
    }
}
