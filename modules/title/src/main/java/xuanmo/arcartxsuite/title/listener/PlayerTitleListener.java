package xuanmo.arcartxsuite.title.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xuanmo.arcartxsuite.title.service.TitleService;

/**
 * 玩家称号事件监听器。
 * <p>
 * 监听玩家加入与退出事件，在加入时预加载称号状态，
 * 在退出时清理缓存，确保称号属性及时同步与释放。
 */
public final class PlayerTitleListener implements Listener {

    private final TitleService titleService;

    /**
     * @param titleService 称号服务实例
     */
    public PlayerTitleListener(TitleService titleService) {
        this.titleService = titleService;
    }

    /**
     * 玩家加入时预加载称号状态。
     *
     * @param event 玩家加入事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        var player=org.bukkit.Bukkit.getPlayer(event.snapshot().playerId());
        if(player!=null) titleService.preloadPlayer(player);
    }
    @EventHandler
    public void onRestriction(xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent event) {
        var player=org.bukkit.Bukkit.getPlayer(event.previous().playerId());
        if(player!=null) titleService.clearPlayer(player);
    }

    /**
     * 玩家退出时清理称号缓存。
     *
     * @param event 玩家退出事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        titleService.clearPlayer(event.getPlayer());
    }
}
