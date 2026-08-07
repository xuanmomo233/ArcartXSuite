package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 战令任务条件接口。
 * <p>
 * 定义任务触发前的过滤判定逻辑，由 {@link TaskConditionParser} 从配置解析，
 * 在事件到达时由 {@link xuanmo.arcartxsuite.battlepass.service.BattlePassTaskEngine} 调用 {@link #test} 进行匹配。
 */
public interface TaskCondition {

    /**
     * 测试玩家与事件载荷是否满足此条件。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷键值对
     * @return {@code true} 表示条件满足
     */
    boolean test(Player player, Map<String, String> payload);

    /**
     * 返回此条件的类型标识，用于序列化与调试。
     *
     * @return 条件类型字符串
     */
    String type();
}
