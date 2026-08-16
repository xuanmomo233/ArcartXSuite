package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 基于事件载荷值的增量策略。
 * <p>
 * 运行期只读取已解析的字段，先将载荷值乘以 scale 并四舍五入，
 * 再按 min-per-event 和 max-per-event 进行夹取。
 */
public final class PayloadValueStrategy implements IncrementStrategy {

    private final String payloadKey;
    private final int maxPerEvent;
    private final int minPerEvent;
    private final double scale;

    /**
     * 构造载荷值增量策略。
     *
     * @param payloadKey payload 键名
     * @param maxPerEvent 单次最大增量，0 表示不限制
     * @param minPerEvent 单次最小增量
     * @param scale 载荷值倍率
     */
    public PayloadValueStrategy(
        String payloadKey, int maxPerEvent, int minPerEvent, double scale
    ) {
        this.payloadKey = payloadKey;
        this.maxPerEvent = maxPerEvent;
        this.minPerEvent = minPerEvent;
        this.scale = scale;
    }

    /**
     * {@inheritDoc}
     *
     * @param player 触发事件的玩家
     * @param payload 事件载荷
     * @return 夹取后的非负增量
     */
    @Override
    public int calculateIncrement(Player player, Map<String, String> payload) {
        if (payload == null) return 0;
        String raw = payload.get(payloadKey);
        if (raw == null) return 0;
        try {
            int increment = Math.max(0, (int) Math.round(Double.parseDouble(raw) * scale));
            if (maxPerEvent > 0) increment = Math.min(increment, maxPerEvent);
            return Math.max(minPerEvent, increment);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 返回策略类型。
     *
     * @return {@code payload_value}
     */
    @Override
    public String type() {
        return "payload_value";
    }

    /**
     * 返回 payload 键名。
     *
     * @return payload 键名
     */
    public String payloadKey() {
        return payloadKey;
    }

    /**
     * 返回单次最大增量。
     *
     * @return 最大增量，0 表示不限制
     */
    public int maxPerEvent() {
        return maxPerEvent;
    }

    /**
     * 返回单次最小增量。
     *
     * @return 最小增量
     */
    public int minPerEvent() {
        return minPerEvent;
    }

    /**
     * 返回载荷值倍率。
     *
     * @return 倍率
     */
    public double scale() {
        return scale;
    }
}
