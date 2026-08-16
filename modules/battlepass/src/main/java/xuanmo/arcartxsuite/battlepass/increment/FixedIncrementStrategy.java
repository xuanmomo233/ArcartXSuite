package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 固定增量策略，实现 {@link IncrementStrategy}。
 * <p>
 * 每次事件触发时返回固定的进度增量值，最小为 1。
 */
public final class FixedIncrementStrategy implements IncrementStrategy {

    private final int value;

    /**
     * 构造固定增量策略。
     *
     * @param value 固定增量值，自动限制为不小于 1
     */
    public FixedIncrementStrategy(int value) {
        this.value = Math.max(1, value);
    }

    @Override
    public int calculateIncrement(Player player, Map<String, String> payload) {
        return value;
    }

    @Override
    public String type() {
        return "fixed";
    }

    /**
     * 返回固定增量值。
     *
     * @return 固定增量
     */
    public int value() {
        return value;
    }
}
