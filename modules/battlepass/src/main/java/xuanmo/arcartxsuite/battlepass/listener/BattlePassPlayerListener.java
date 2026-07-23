package xuanmo.arcartxsuite.battlepass.listener;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 进服异步预热缓存，退服异步落盘并驱逐缓存。
 */
public final class BattlePassPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<BattlePassService> serviceSupplier;

    public BattlePassPlayerListener(JavaPlugin plugin, Supplier<BattlePassService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        BattlePassService service = serviceSupplier.get();
        if (service == null) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (player.isOnline()) {
                service.preload(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        BattlePassService service = serviceSupplier.get();
        if (service == null) return;
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> service.handleQuit(uuid));
    }
}
