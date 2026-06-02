package xuanmo.arcartxsuite.entitytracker.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 排行榜奖励配置实体
 */
public class RankingRewardConfig {
    private Integer id;
    private String rewardType; // weekly/monthly
    private String rankingType; // best_damage/boss_damage/kills/participate
    private String bossId; // 仅boss_damage类型需要
    private Integer rankStart;
    private Integer rankEnd;
    private String rewardName;
    private String rewardDescription;
    private String rewardItems; // JSON格式
    private String rewardCommands; // JSON格式
    private Integer rewardMoney;
    private Integer rewardDkp;
    private Boolean enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // 构造函数
    public RankingRewardConfig() {}

    public RankingRewardConfig(String rewardType, String rankingType, 
                              Integer rankStart, Integer rankEnd, String rewardName) {
        this.rewardType = rewardType;
        this.rankingType = rankingType;
        this.rankStart = rankStart;
        this.rankEnd = rankEnd;
        this.rewardName = rewardName;
        this.enabled = true;
    }

    // Getter和Setter方法
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Integer getRankStart() {
        return rankStart;
    }

    public void setRankStart(Integer rankStart) {
        this.rankStart = rankStart;
    }

    public Integer getRankEnd() {
        return rankEnd;
    }

    public void setRankEnd(Integer rankEnd) {
        this.rankEnd = rankEnd;
    }

    public String getRewardName() {
        return rewardName;
    }

    public void setRewardName(String rewardName) {
        this.rewardName = rewardName;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public void setRewardDescription(String rewardDescription) {
        this.rewardDescription = rewardDescription;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "RankingRewardConfig{" +
                "id=" + id +
                ", rewardType='" + rewardType + '\'' +
                ", rankingType='" + rankingType + '\'' +
                ", bossId='" + bossId + '\'' +
                ", rankStart=" + rankStart +
                ", rankEnd=" + rankEnd +
                ", rewardName='" + rewardName + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
