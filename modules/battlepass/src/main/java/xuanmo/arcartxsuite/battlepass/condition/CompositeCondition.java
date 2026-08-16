package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 组合多个子条件的任务条件。
 * <p>
 * {@code any_of} 要求任一子条件满足，{@code all_of} 要求全部满足，
 * {@code not} 对全部子条件满足的结果取反。
 */
public final class CompositeCondition implements TaskCondition {

    private final String type;
    private final List<TaskCondition> children;

    /**
     * 构造组合条件。
     *
     * @param type 组合类型（any_of/all_of/not）
     * @param children 子条件列表
     */
    public CompositeCondition(String type, List<TaskCondition> children) {
        this.type = type;
        this.children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     *
     * @param player 触发事件的玩家
     * @param payload 事件载荷
     * @return 组合条件的判定结果
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        if ("any_of".equals(type)) {
            for (TaskCondition child : children) {
                if (child.test(player, payload)) return true;
            }
            return false;
        }
        boolean result = true;
        for (TaskCondition child : children) {
            if (!child.test(player, payload)) {
                result = false;
                break;
            }
        }
        return "not".equals(type) ? !result : result;
    }

    /**
     * 返回组合类型。
     *
     * @return 组合类型
     */
    @Override
    public String type() {
        return type;
    }

    /**
     * 返回不可变的子条件列表。
     *
     * @return 子条件列表
     */
    public List<TaskCondition> children() {
        return Collections.unmodifiableList(children);
    }
}
