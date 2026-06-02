package xuanmo.arcartxsuite.entitytracker.entity;

import java.time.LocalDateTime;

/**
 * 排行榜奖励发放记录实体
 */
public class RankingRewardRecord {
    private Integer id;
    private Integer rewardConfigId;
    private String rewardType; // weekly/monthly
    private String rankingType; // best_damage/boss_damage/kills/participate
    private String bossId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String playerUuid;
    private String playerName;
    private Integer rank;
    private Integer score; // 伤害值、击杀数等
    private String rewardItems; // JSON格式
    private String rewardCommands; // JSON格式
    private Integer rewardMoney;
    private Integer rewardDkp;
    private String status; // pending/success/failed
    private LocalDateTime issuedTime;
    private String failureReason;
    private String serverName;
    private Integer retryCount;

    // 构造函数
    public RankingRewardRecord() {}

    public RankingRewardRecord(Integer rewardConfigId, String rewardType, String rankingType,
                              LocalDateTime periodStart, LocalDateTime periodEnd,
                              String playerUuid, String playerName, Integer rank, Integer score) {
        this.rewardConfigId = rewardConfigId;
        this.rewardType = rewardType;
        this.rankingType = rankingType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rank = rank;
        this.score = score;
        this.status = "pending";
        this.retryCount = 0;
    }

    // Getter和Setter方法
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRewardConfigId() {
        return rewardConfigId;
    }

    public void setRewardConfigId(Integer rewardConfigId) {
        this.rewardConfigId = rewardConfigId;
    }

    public String getRewardType() {
        return rewardType;
    }

    public void setRewardType(String rewardType) {
        this.rewardType = rewardType;
    }

    public String getRankingType() {
        return rankingType;
    }

    public void setRankingType(String rankingType) {
        this.rankingType = rankingType;
    }

    public String getBossId() {
        return bossId;
    }

    public void setBossId(String bossId) {
        this.bossId = bossId;
    }

    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getRewardItems() {
        return rewardItems;
    }

    public void setRewardItems(String rewardItems) {
        this.rewardItems = rewardItems;
    }

    public String getRewardCommands() {
        return rewardCommands;
    }

    public void setRewardCommands(String rewardCommands) {
        this.rewardCommands = rewardCommands;
    }

    public Integer getRewardMoney() {
        return rewardMoney;
    }

    public void setRewardMoney(Integer rewardMoney) {
        this.rewardMoney = rewardMoney;
    }

    public Integer getRewardDkp() {
        return rewardDkp;
    }

    public void setRewardDkp(Integer rewardDkp) {
        this.rewardDkp = rewardDkp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getIssuedTime() {
        return issuedTime;
    }

    public void setIssuedTime(LocalDateTime issuedTime) {
        this.issuedTime = issuedTime;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public String toString() {
        return "RankingRewardRecord{" +
                "id=" + id +
                ", rewardConfigId=" + rewardConfigId +
                ", rewardType='" + rewardType + '\'' +
                ", rankingType='" + rankingType + '\'' +
                ", playerUuid='" + playerUuid + '\'' +
                ", playerName='" + playerName + '\'' +
                ", rank=" + rank +
                ", score=" + score +
                ", status='" + status + '\'' +
                '}';
    }
}
