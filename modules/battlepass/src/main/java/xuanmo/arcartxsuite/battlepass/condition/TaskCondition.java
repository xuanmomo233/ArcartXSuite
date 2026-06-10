package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

public interface TaskCondition {

    boolean test(Player player, Map<String, String> payload);

    String type();
}
