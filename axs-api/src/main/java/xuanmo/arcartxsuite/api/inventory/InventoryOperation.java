package xuanmo.arcartxsuite.api.inventory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Player;

/** Process-local reservation shared by modules that prepare changes to a player's inventory. */
public final class InventoryOperation implements AutoCloseable {
    private static final ConcurrentMap<UUID, InventoryOperation> ACTIVE = new ConcurrentHashMap<>();
    private final UUID playerId;

    private InventoryOperation(UUID playerId) {
        this.playerId = playerId;
    }

    public static InventoryOperation tryAcquire(Player player) {
        InventoryOperation operation = new InventoryOperation(player.getUniqueId());
        return ACTIVE.putIfAbsent(operation.playerId, operation) == null ? operation : null;
    }

    public static boolean busy(Player player) {
        return ACTIVE.containsKey(player.getUniqueId());
    }

    @Override
    public void close() {
        ACTIVE.remove(playerId, this);
    }
}
