package xuanmo.arcartxsuite.battlepass.increment;

import java.util.Map;
import org.bukkit.entity.Player;

public interface IncrementStrategy {

    int calculateIncrement(Player player, Map<String, String> payload);

    String type();
}
