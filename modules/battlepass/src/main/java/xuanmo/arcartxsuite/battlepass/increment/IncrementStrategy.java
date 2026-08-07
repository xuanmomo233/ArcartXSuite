package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 战令任务进度增量策略接口。
 * <p>
 * 定义事件触发时任务进度增加量的计算逻辑，由 {@link IncrementStrategyParser} 从配置解析，
 * 在 {@link xuanmo.arcartxsuite.battlepass.service.BattlePassTaskEngine} 处理事件时调用。
 */
public interface IncrementStrategy {

    /**
     * 根据玩家与事件载荷计算本次进度增量。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷键值对
     * @return 进度增量（非负整数）
     */
    int calculateIncrement(Player player, Map<String, String> payload);

    /**
     * 返回此策略的类型标识，用于序列化与调试。
     *
     * @return 策略类型字符串
     */
    String type();
}
