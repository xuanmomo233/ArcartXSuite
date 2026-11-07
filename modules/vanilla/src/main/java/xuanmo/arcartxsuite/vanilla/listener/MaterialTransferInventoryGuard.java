package xuanmo.arcartxsuite.vanilla.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import xuanmo.arcartxsuite.vanilla.recipe.RecipeMaterialPlanner;

/** Short-lived per-player gate, only while a warehouse transfer is in flight. */
public final class MaterialTransferInventoryGuard implements Listener {
    private final RecipeMaterialPlanner planner;
    public MaterialTransferInventoryGuard(RecipeMaterialPlanner planner){this.planner=planner;}
    @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p && planner.busy(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && planner.busy(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void drop(PlayerDropItemEvent e){if(planner.busy(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void swap(PlayerSwapHandItemsEvent e){if(planner.busy(e.getPlayer()))e.setCancelled(true);}
}
