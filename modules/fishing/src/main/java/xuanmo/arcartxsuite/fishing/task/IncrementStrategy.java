package xuanmo.arcartxsuite.fishing.task;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 进度增量策略接口。
 */
public interface IncrementStrategy {

    /**
     * 计算本次事件的进度增量。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷
     * @return 进度增量值
     */
    int calculateIncrement(@NotNull Player player, @NotNull Map<String, String> payload);
}
