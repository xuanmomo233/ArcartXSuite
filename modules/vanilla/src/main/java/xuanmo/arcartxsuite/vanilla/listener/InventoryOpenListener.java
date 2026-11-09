package xuanmo.arcartxsuite.vanilla.listener;

import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import xuanmo.arcartxsuite.vanilla.task.FurnaceStateTask;

/**
 * 监听熔炉打开与关闭，追踪状态推送。
 * 工作台界面由 UI YAML 的 match 自动替换，无需插件代码介入。
 */
public final class InventoryOpenListener implements Listener {

    private FurnaceStateTask furnaceTask;

    public InventoryOpenListener(FurnaceStateTask furnaceTask) {
        this.furnaceTask = furnaceTask;
    }

    public void setFurnaceTask(FurnaceStateTask furnaceTask) {
        this.furnaceTask = furnaceTask;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (furnaceTask == null) return;
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();

        for (var type : xuanmo.arcartxsuite.vanilla.recipe.WorkshopType.values())
            if (type.processing() && event.getView().getType().name().equals(type.name())) {
                furnaceTask.track(player, type); break;
            }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (furnaceTask == null) return;
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        furnaceTask.untrackPlayer(player.getUniqueId());
    }
}
