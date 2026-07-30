package xuanmo.arcartxsuite.lifecycle;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent;
import priv.seventeen.artist.arcartx.event.client.ClientInitializedEvent;
import xuanmo.arcartxsuite.module.ModuleRegistry;

public final class ClientEventLifecycleManager implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<ModuleRegistry> registrySupplier;
    private boolean registered;

    public ClientEventLifecycleManager(JavaPlugin plugin, Supplier<ModuleRegistry> registrySupplier) {
        this.plugin = plugin;
        this.registrySupplier = registrySupplier;
    }

    public void start() {
        if (registered) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    public void stop() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientCustomPacket(ClientCustomPacketEvent event) {
        ModuleRegistry registry = registrySupplier.get();
        if (registry == null) return;
        Player player = event.getPlayer();
        String packetId = event.getId();
        List<String> data = event.getData();
        Runnable route = () -> registry.routeClientPacket(player, packetId, data);
        if (Bukkit.isPrimaryThread()) {
            route.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, route);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientInitialized(ClientInitializedEvent.End event) {
        ModuleRegistry registry = registrySupplier.get();
        if (registry == null) return;
        Player player = event.getPlayer();
        Runnable route = () -> registry.routeClientInitialized(player);
        if (Bukkit.isPrimaryThread()) {
            route.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, route);
        }
    }
}
