package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

public final class FixedIncrementStrategy implements IncrementStrategy {

    private final int value;

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

    public int value() {
        return value;
    }
}
