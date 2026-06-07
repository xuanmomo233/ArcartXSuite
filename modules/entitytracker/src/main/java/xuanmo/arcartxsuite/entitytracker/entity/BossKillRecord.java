package xuanmo.arcartxsuite.entitytracker.entity;

import java.time.LocalDateTime;

public class BossKillRecord {

    private Long id;
    private String bossId;
    private String bossDisplayName;
    private LocalDateTime killTime;
    private String serverName;
    private String participantsJson;
    private String dropsJson;
    private int totalDamage;
    private int durationSeconds;
    private String worldName;
    private Double locationX;
    private Double locationY;
    private Double locationZ;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getKillTime() {
        return killTime;
    }

    public void setKillTime(LocalDateTime killTime) {
        this.killTime = killTime;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getParticipantsJson() {
        return participantsJson;
    }

    public void setParticipantsJson(String participantsJson) {
        this.participantsJson = participantsJson;
    }

    public String getDropsJson() {
        return dropsJson;
    }

    public void setDropsJson(String dropsJson) {
        this.dropsJson = dropsJson;
    }

    public int getTotalDamage() {
        return totalDamage;
    }

    public void setTotalDamage(int totalDamage) {
        this.totalDamage = totalDamage;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
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
}
