package xuanmo.arcartxsuite.security;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Protects reserved ground items even when Warehouse is stopped or unavailable. */
public final class GroundTransferGuard implements Listener {
    private static final NamespacedKey MARKER = new NamespacedKey("arcartxsuite", "warehouse_ground_transfer");

    public void register(JavaPlugin host) {
        PluginManager manager = Bukkit.getPluginManager();
        manager.registerEvents(this, host);
        registerPlayerAttempt(manager, host);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    private void registerPlayerAttempt(PluginManager manager, JavaPlugin host) {
        try {
            var type = Class.forName("org.bukkit.event.player.PlayerAttemptPickupItemEvent", false,
                    Event.class.getClassLoader()).asSubclass(Event.class);
            Method getItem = type.getMethod("getItem");
            Method setFly = type.getMethod("setFlyAtPlayer", boolean.class);
            manager.registerEvent(type, this, EventPriority.LOWEST, (ignored, event) -> {
                try {
                    if (!reserved((Item) getItem.invoke(event))) return;
                    ((Cancellable) event).setCancelled(true);
                    setFly.invoke(event, false);
                } catch (ReflectiveOperationException failure) {
                    throw new EventException(failure);
                }
            }, host, false);
        } catch (ClassNotFoundException ignored) {
            // Spigot does not expose the early Paper event.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            host.getLogger().warning("无法注册 Paper 掉落物预拾取保护: " + unavailable.getMessage());
        }
    }

    private static boolean reserved(Item item) {
        return item.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void playerPickup(PlayerPickupItemEvent event) {
        if (reserved(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void entityPickup(EntityPickupItemEvent event) {
        if (reserved(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void inventoryPickup(InventoryPickupItemEvent event) {
        if (reserved(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void merge(ItemMergeEvent event) {
        if (reserved(event.getEntity()) || reserved(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void despawn(ItemDespawnEvent event) {
        if (reserved(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && reserved(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void teleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Item item && reserved(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void portal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Item item && reserved(item)) event.setCancelled(true);
    }
}
