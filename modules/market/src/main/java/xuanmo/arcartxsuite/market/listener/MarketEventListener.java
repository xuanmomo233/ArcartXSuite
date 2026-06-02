package xuanmo.arcartxsuite.market.listener;

import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.market.recycle.RecycleService;

/**
 * 市场事件监听器（自动回收等）。
 */
public class MarketEventListener implements Listener {

    private final Supplier<RecycleService> recycleSupplier;
    private final boolean autoRecycleEnabled;

    public MarketEventListener(Supplier<RecycleService> recycleSupplier, boolean autoRecycleEnabled) {
        this.recycleSupplier = recycleSupplier;
        this.autoRecycleEnabled = autoRecycleEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!autoRecycleEnabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.hasPermission("axsmarket.autorecycle")) return;

        RecycleService service = recycleSupplier.get();
        if (service == null) return;

        ItemStack item = event.getItem().getItemStack();
        if (service.isRecyclable(item)) {
            // 自动回收：取消拾取并给钱
            event.setCancelled(true);
            service.recycle(player, item.clone());
            event.getItem().remove();
        }
    }
}
