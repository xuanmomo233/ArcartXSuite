package xuanmo.arcartxsuite.api.capability;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Extension point for material delivery. All methods run on the main thread. */
public interface MaterialContainerAdapter {
    String id();
    boolean supports(InventoryView view);
    /** Capture only changed slots; retain full item data in recovery evidence. */
    Delivery capture(Player player, InventoryView view, Map<Integer, ItemStack> storageChanges, Map<Integer, ItemStack> inputChanges);

    interface Delivery {
        WarehouseMaterialAccess.Target target();
        boolean unchanged();
        /** Called at most once, only after durable delivery authorization and unchanged() == true. */
        void apply();
    }
}
