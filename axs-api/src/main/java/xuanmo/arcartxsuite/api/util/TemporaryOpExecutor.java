package xuanmo.arcartxsuite.api.util;

import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

public final class TemporaryOpExecutor {

    private TemporaryOpExecutor() {
    }

    public static <T> T execute(Player player, Supplier<T> action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        boolean wasOp = player.isOp();
        try {
            player.setOp(true);
            return action.get();
        } finally {
            if (!wasOp) {
                player.setOp(false);
            }
        }
    }
}
