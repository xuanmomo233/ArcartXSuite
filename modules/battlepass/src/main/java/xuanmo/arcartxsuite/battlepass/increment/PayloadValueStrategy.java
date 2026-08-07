package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 基于事件载荷值的增量策略，实现 {@link IncrementStrategy}。
 * <p>
 * 从事件 payload 中提取指定键的数值，乘以缩放系数后取整作为增量，
 * 可配置单次事件最大增量上限。
 */
public final class PayloadValueStrategy implements IncrementStrategy {

    private final String payloadKey;
    private final int maxPerEvent;
    private final double scale;

    /**
     * 构造载荷值增量策略。
     *
     * @param payloadKey  payload 中数值键名
     * @param maxPerEvent 单次事件最大增量（0 表示不限）
     * @param scale       数值缩放系数
     */
    public PayloadValueStrategy(String payloadKey, int maxPerEvent, double scale) {
        this.payloadKey = payloadKey;
        this.maxPerEvent = maxPerEvent;
        this.scale = scale;
    }

    @Override
    public int calculateIncrement(Player player, Map<String, String> payload) {
        if (payload == null) return 0;
        String raw = payload.get(payloadKey);
        if (raw == null) return 0;
        try {
            double value = Double.parseDouble(raw) * scale;
            int increment = (int) Math.round(value);
            if (maxPerEvent > 0) {
                increment = Math.min(increment, maxPerEvent);
            }
            return Math.max(0, increment);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String type() {
        return "payload_value";
    }

    public String payloadKey() { return payloadKey; }
    public int maxPerEvent() { return maxPerEvent; }
    public double scale() { return scale; }
}
