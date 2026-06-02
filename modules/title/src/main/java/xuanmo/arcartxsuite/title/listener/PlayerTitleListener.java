package xuanmo.arcartxsuite.title.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xuanmo.arcartxsuite.title.service.TitleService;

public final class PlayerTitleListener implements Listener {

    private final TitleService titleService;

    public PlayerTitleListener(TitleService titleService) {
        this.titleService = titleService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        titleService.preloadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        titleService.clearPlayer(event.getPlayer());
    }
}
