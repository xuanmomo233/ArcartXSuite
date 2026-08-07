package xuanmo.arcartxsuite.tab.config;

import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;

/**
 * Tab 模块客户端刷新防护配置。
 * <p>
 * 用于限制客户端主动发起的 Tab 刷新请求频率，防止恶意高频刷新。
 * 关联模块：{@code tab}，由 {@link xuanmo.arcartxsuite.tab.sync.TabClientRefreshGuard} 消费。
 *
 * @param enabled         是否启用防护
 * @param windowMs        统计窗口时长（毫秒），最小为 1
 * @param maxHits         窗口内允许的最大刷新次数，最小为 1
 * @param mode            防护模式，{@code null} 时默认 {@link ClientPacketGuardMode#SILENT}；
 *                        {@link ClientPacketGuardMode#PUNISH} 会被降级为 {@link ClientPacketGuardMode#NOTIFY}
 * @param notifyMessage   命中防护时向玩家发送的提示消息，{@code null} 时为空串
 * @param notifyCooldownMs 提示消息冷却时长（毫秒），最小为 0
 */
public record TabClientRefreshGuardConfiguration(
    boolean enabled,
    long windowMs,
    int maxHits,
    ClientPacketGuardMode mode,
    String notifyMessage,
    long notifyCooldownMs
) {

    /**
     * 紧凑构造器，对字段进行最小值约束与默认值兜底。
     */
    public TabClientRefreshGuardConfiguration {
        windowMs = Math.max(1L, windowMs);
        maxHits = Math.max(1, maxHits);
        notifyCooldownMs = Math.max(0L, notifyCooldownMs);
        mode = mode == null ? ClientPacketGuardMode.SILENT : mode;
        if (mode == ClientPacketGuardMode.PUNISH) {
            mode = ClientPacketGuardMode.NOTIFY;
        }
        notifyMessage = notifyMessage == null ? "" : notifyMessage;
    }
}
