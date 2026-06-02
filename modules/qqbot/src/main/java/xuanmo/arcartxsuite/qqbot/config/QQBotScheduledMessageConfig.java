package xuanmo.arcartxsuite.qqbot.config;

import java.util.List;

/**
 * 定时消息配置（单条）。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>{@code interval} — 每隔 {@code intervalSeconds} 秒推送一次</li>
 *   <li>{@code daily} — 每日在 {@code dailyTime}（HH:mm）推送一次</li>
 * </ul>
 */
public record QQBotScheduledMessageConfig(
    String id,
    String mode,
    int intervalSeconds,
    String dailyTime,
    String message,
    List<Long> targetGroups
) {
    public boolean isInterval() {
        return "interval".equalsIgnoreCase(mode);
    }

    public boolean isDaily() {
        return "daily".equalsIgnoreCase(mode);
    }

    public boolean pushToAllGroups() {
        return targetGroups == null || targetGroups.isEmpty();
    }
}
