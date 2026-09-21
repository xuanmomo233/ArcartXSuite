package xuanmo.arcartxsuite.security;

import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

/**
 * 单条客户端包限流规则。
 *
 * @param enabled          是否启用
 * @param windowMs         滑动窗口时长（毫秒），至少 1
 * @param maxHits          窗口内最大命中次数，至少 1
 * @param mode             超限处置模式
 * @param notifyMessage    通知消息（含颜色码）
 * @param notifyCooldownMs 通知冷却（毫秒），避免刷屏
 * @param punishCommand    惩罚命令模板
 * @param punishCooldownMs 惩罚命令冷却（毫秒），避免连续超限反复执行控制台命令
 */
public record ClientPacketGuardRule(
    boolean enabled,
    long windowMs,
    int maxHits,
    ClientPacketGuardMode mode,
    String notifyMessage,
    long notifyCooldownMs,
    String punishCommand,
    long punishCooldownMs
) {

    public ClientPacketGuardRule {
        windowMs = Math.max(1L, windowMs);
        maxHits = Math.max(1, maxHits);
        notifyCooldownMs = Math.max(0L, notifyCooldownMs);
        punishCooldownMs = Math.max(0L, punishCooldownMs);
        mode = mode == null ? ClientPacketGuardMode.SILENT : mode;
        notifyMessage = notifyMessage == null ? "" : notifyMessage;
        punishCommand = punishCommand == null ? "" : punishCommand.trim();
    }
}
