package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 对单个任务条件取反的包装条件。
 */
public final class NegatedCondition implements TaskCondition {

    private final TaskCondition delegate;

    /**
     * 构造取反条件。
     *
     * @param delegate 被取反的条件
     */
    public NegatedCondition(TaskCondition delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     *
     * @param player 触发事件的玩家
     * @param payload 事件载荷
     * @return 被包装条件结果的反值
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        return !delegate.test(player, payload);
    }

    /**
     * 返回条件类型。
     *
     * @return {@code not}
     */
    @Override
    public String type() {
        return "not";
    }
}
