package xuanmo.arcartxsuite.auth.runtime;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import xuanmo.arcartxsuite.api.auth.AuthAccess;

/** Independent of UI registration. Cleanup and offline system jobs are not intercepted here. */
public final class AdmissionGuard implements Listener {
    private final java.util.function.BooleanSupplier enforced;
    public AdmissionGuard(java.util.function.BooleanSupplier enforced) { this.enforced=enforced; }
    @SuppressWarnings("unchecked")
    public void registerPaperChat(org.bukkit.plugin.java.JavaPlugin plugin) {
        try {
            Class<?> type=Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            var getPlayer=type.getMethod("getPlayer");
            var cancel=type.getMethod("setCancelled",boolean.class);
            org.bukkit.Bukkit.getPluginManager().registerEvent((Class<? extends Event>)type,this,EventPriority.HIGHEST,
                (listener,event)->{
                    try { if(blocked((Player)getPlayer.invoke(event))) cancel.invoke(event,true); }
                    catch(ReflectiveOperationException e) { throw new org.bukkit.event.EventException(e); }
                },plugin,true);
        } catch(ClassNotFoundException ignored) {
            // Legacy chat listener is used on Spigot.
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot register Paper admission chat guard",e); }
    }
    private boolean blocked(Player player) { return enforced.getAsBoolean() && !AuthAccess.admitted(player.getUniqueId()); }
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if (blocked(e.getPlayer()) && e.getTo()!=null && !e.getFrom().getBlock().equals(e.getTo().getBlock())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e) {
        if (blocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void chat(AsyncPlayerChatEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void click(InventoryClickEvent e) { if(e.getWhoClicked() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void drag(InventoryDragEvent e) { if(e.getWhoClicked() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void pickup(EntityPickupItemEvent e) { if(e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void placeBlock(BlockPlaceEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void editBook(PlayerEditBookEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void fish(PlayerFishEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void armorStand(PlayerArmorStandManipulateEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void emptyBucket(PlayerBucketEmptyEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void fillBucket(PlayerBucketFillEvent e) { if(blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void hunger(FoodLevelChangeEvent e) { if(e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e) { if(e.getTarget() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void vehicle(org.bukkit.event.vehicle.VehicleEnterEvent e) { if(e.getEntered() instanceof Player p && blocked(p)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageEvent e) {
        if(e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player p && blocked(p)) e.setCancelled(true);
        if(e instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player p && blocked(p)) e.setCancelled(true);
    }
}
