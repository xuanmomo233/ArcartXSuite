package xuanmo.arcartxsuite.tooltip.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;

/**
 * 玩家退出监听器，清理 tooltip 缓存。
 */
public final class TooltipPlayerListener implements Listener {

    private final TooltipDataCache cache;

    public TooltipPlayerListener(@NotNull TooltipDataCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.removePlayer(event.getPlayer().getUniqueId());
    }
}
