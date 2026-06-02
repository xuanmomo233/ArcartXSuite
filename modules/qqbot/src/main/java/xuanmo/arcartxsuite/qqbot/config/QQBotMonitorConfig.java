package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 服务器监控告警配置。
 * 检测到 TPS 过低或内存占用过高时自动推送到指定群。
 */
public record QQBotMonitorConfig(
    boolean enabled,
    double tpsThreshold,
    int memoryThresholdPercent,
    int checkIntervalSeconds,
    int cooldownSeconds,
    List<Long> alarmGroups,
    String tpsAlarmFormat,
    String memoryAlarmFormat
) {
    /** alarmGroups 为空时表示推送到所有已配置群 */
    public boolean pushToAllGroups() {
        return alarmGroups == null || alarmGroups.isEmpty();
    }
}
