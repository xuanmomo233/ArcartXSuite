package xuanmo.arcartxsuite.fishing.task;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 基于 payload 字段值的增量策略。
 * <p>
 * 从 payload 中提取指定字段的数值作为增量，可设置单次事件上限和缩放系数。
 *
 * @param payloadKey  payload 字段名
 * @param maxPerEvent 单次事件增量上限（0 表示不限制）
 * @param scale       缩放系数
 */
public record PayloadValueIncrementStrategy(
    @NotNull String payloadKey,
    int maxPerEvent,
    double scale
) implements IncrementStrategy {

    @Override
    public int calculateIncrement(@NotNull Player player, @NotNull Map<String, String> payload) {
        String raw = payload.get(payloadKey);
        if (raw == null) return 0;
        try {
            double value = Double.parseDouble(raw) * scale;
            int increment = (int) value;
            if (maxPerEvent > 0 && increment > maxPerEvent) increment = maxPerEvent;
            return Math.max(1, increment);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
