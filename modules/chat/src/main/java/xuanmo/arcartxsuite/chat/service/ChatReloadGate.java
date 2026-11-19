package xuanmo.arcartxsuite.chat.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 重载交接期间拒收原生聊天，避免原版广播绕过频道和屏蔽规则。 */
public final class ChatReloadGate implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final String message;
    private final Set<UUID> notified = ConcurrentHashMap.newKeySet();
    private volatile boolean active = true;

    public ChatReloadGate(JavaPlugin plugin, String message) {
        this.plugin = plugin;
        this.message = message;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            Class<?> paper = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            @SuppressWarnings("unchecked") Class<? extends Event> type = (Class<? extends Event>) paper;
            for (EventPriority priority : new EventPriority[]{EventPriority.LOWEST, EventPriority.HIGHEST}) {
                Bukkit.getPluginManager().registerEvent(type, this, priority,
                    (listener, event) -> reject(event), plugin, false);
            }
        } catch (ClassNotFoundException ignored) {
            // Spigot 由 AsyncPlayerChatEvent 入口处理。
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void early(AsyncPlayerChatEvent event) { reject(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void late(AsyncPlayerChatEvent event) { reject(event); }

    public boolean reject(Event event) {
        if (!active || !(event instanceof Cancellable cancelled)) return false;
        cancelled.setCancelled(true);
        if (event instanceof PlayerEvent playerEvent) {
            Player player = playerEvent.getPlayer();
            if (notified.add(player.getUniqueId()) && plugin.isEnabled()) {
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) player.sendMessage(message);
                    });
                } catch (org.bukkit.plugin.IllegalPluginAccessException stopped) { }
            }
        }
        return true;
    }

    @Override public void close() {
        active = false;
        HandlerList.unregisterAll(this);
    }
}
