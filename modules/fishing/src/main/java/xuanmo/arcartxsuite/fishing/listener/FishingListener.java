package xuanmo.arcartxsuite.fishing.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.fishing.service.FishingService;

public final class FishingListener implements Listener {

    private final FishingService service;

    public FishingListener(@NotNull FishingService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(@NotNull PlayerFishEvent event) {
        if (!service.isReplaceVanilla()) {
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (service.isPlayerInMinigame(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // 取消原版钓鱼，启动小游戏
        event.setCancelled(true);
        service.startMinigame(player);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        if (service.isPlayerInMinigame(event.getPlayer().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getPlayer().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        if (service.isPlayerInMinigame(event.getEntity().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getEntity().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (service.isPlayerInMinigame(event.getPlayer().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getPlayer().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
    }
}
