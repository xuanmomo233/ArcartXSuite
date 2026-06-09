package xuanmo.arcartxsuite.afkreward.listener;

import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;

public final class AfkRewardListener implements Listener {

    private final Supplier<AfkRewardService> serviceSupplier;

    public AfkRewardListener(Supplier<AfkRewardService> serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        // 只检测坐标变化，忽略视角转动
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        // MANUAL 模式下封锁移动
        if (service.isInManualAfk(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        // MANUAL 模式下禁止传送（模块自己的 end 回传除外，但 end 回传在命令中执行，不在事件中）
        if (service.isInManualAfk(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (event.getPlayer() instanceof Player player
            && service.isInManualAfk(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        service.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
