package xuanmo.arcartxsuite.fishing.task;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 任务触发条件接口。
 */
public interface TaskCondition {

    /**
     * 测试玩家与事件载荷是否满足此条件。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷
     * @return {@code true} 表示条件满足
     */
    boolean test(@NotNull Player player, @NotNull Map<String, String> payload);
}
