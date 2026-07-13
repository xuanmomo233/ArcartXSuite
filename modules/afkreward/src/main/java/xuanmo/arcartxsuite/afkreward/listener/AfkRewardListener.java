package xuanmo.arcartxsuite.afkreward.listener;

import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService.ManualProtection;

public final class AfkRewardListener implements Listener {

    private final Supplier<AfkRewardService> serviceSupplier;

    public AfkRewardListener(Supplier<AfkRewardService> serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.MOVEMENT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        // MANUAL 模式下禁止传送（模块自己的 end 回传除外，但 end 回传在命令中执行，不在事件中）
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.TELEPORT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.BLOCK_BREAK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (event.getPlayer() instanceof Player player
            && service.isInManualAfk(player.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.INVENTORY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        service.markCombat(player.getUniqueId());
        if (service.isInManualAfk(player.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.RECEIVE_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        service.markCombat(attacker.getUniqueId());
        if (service.isInManualAfk(attacker.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.DEAL_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(player.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.ENTITY_TARGET)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(player.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.VEHICLE_ENTER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.INTERACT_ENTITY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.DROP_ITEM)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.SWAP_HAND)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(player.getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.PICKUP_ITEM)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        if (service.isInManualAfk(event.getPlayer().getUniqueId())
            && service.isManualProtectionEnabled(ManualProtection.EXPERIENCE)) {
            event.setAmount(0);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) return;
        service.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
