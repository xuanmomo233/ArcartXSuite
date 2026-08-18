package xuanmo.arcartxsuite.fishing.task;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 固定增量策略：每次事件固定增加指定值。
 */
public record FixedIncrementStrategy(int value) implements IncrementStrategy {

    @Override
    public int calculateIncrement(@NotNull Player player, @NotNull Map<String, String> payload) {
        return value;
    }
}
