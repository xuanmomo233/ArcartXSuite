package xuanmo.arcartxsuite.entitytracker.entity;

import java.time.LocalDateTime;

/**
 * 玩家Boss最高伤害记录实体
 */
public class PlayerBossBestDamage {
    private Integer id;
    private String playerUuid;
    private String playerName;
    private String bossId;
    private String bossDisplayName;
    private Integer bestDamage;
    private LocalDateTime damageTime;
    private String serverName;
    private String worldName;
    private Double locationX;
    private Double locationY;
    private Double locationZ;
    private Integer rank; // 排行名次(查询时计算)

    // 构造函数
    public PlayerBossBestDamage() {}

    public PlayerBossBestDamage(String playerUuid, String playerName, String bossId, 
                               String bossDisplayName, Integer bestDamage, 
                               LocalDateTime damageTime, String serverName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.bossId = bossId;
        this.bossDisplayName = bossDisplayName;
        this.bestDamage = bestDamage;
        this.damageTime = damageTime;
        this.serverName = serverName;
    }

    // Getter和Setter方法
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getBossId() {
        return bossId;
    }

    public void setBossId(String bossId) {
        this.bossId = bossId;
    }

    public String getBossDisplayName() {
        return bossDisplayName;
    }

    public void setBossDisplayName(String bossDisplayName) {
        this.bossDisplayName = bossDisplayName;
    }

    public Integer getBestDamage() {
        return bestDamage;
    }

    public void setBestDamage(Integer bestDamage) {
        this.bestDamage = bestDamage;
    }

    public LocalDateTime getDamageTime() {
        return damageTime;
    }

    public void setDamageTime(LocalDateTime damageTime) {
        this.damageTime = damageTime;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Double getLocationX() {
        return locationX;
    }

    public void setLocationX(Double locationX) {
        this.locationX = locationX;
    }

    public Double getLocationY() {
        return locationY;
    }

    public void setLocationY(Double locationY) {
        this.locationY = locationY;
    }

    public Double getLocationZ() {
        return locationZ;
    }

    public void setLocationZ(Double locationZ) {
        this.locationZ = locationZ;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    @Override
    public String toString() {
        return "PlayerBossBestDamage{" +
                "id=" + id +
                ", playerUuid='" + playerUuid + '\'' +
                ", playerName='" + playerName + '\'' +
                ", bossId='" + bossId + '\'' +
                ", bossDisplayName='" + bossDisplayName + '\'' +
                ", bestDamage=" + bestDamage +
                ", damageTime=" + damageTime +
                ", serverName='" + serverName + '\'' +
                ", rank=" + rank +
                '}';
    }
}
