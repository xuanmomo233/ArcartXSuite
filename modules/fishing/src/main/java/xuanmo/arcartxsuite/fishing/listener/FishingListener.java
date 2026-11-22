package xuanmo.arcartxsuite.fishing.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.fishing.service.FishingService;

/**
 * 钓鱼模块的 Bukkit 事件监听器。
 * <p>
 * 关联模块：{@code fishing}。拦截原版钓鱼事件并启动小游戏，
 * 在玩家退出/死亡/传送时清理进行中的小游戏会话，
 * 并处理鱼竿耐久度加成。
 * 鱼咬钩时消耗饵料队列头部1个饵料。
 */
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

        Player player = event.getPlayer();

        // 小游戏进行中：取消所有钓鱼事件（防止右键收杆打断小游戏）
        if (service.isPlayerInMinigame(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        var hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> service.prepareCast(player, hook.getUniqueId());
            case BITE -> {
                if (service.startVanillaBite(player, hook.getUniqueId(), hook.getLocation())) event.setCancelled(true);
            }
            case CAUGHT_FISH, CAUGHT_ENTITY, IN_GROUND, FAILED_ATTEMPT, REEL_IN ->
                service.forgetCast(player.getUniqueId(), hook.getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        service.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        service.forgetFishSchools(event.getPlayer());
        if (service.isPlayerInMinigame(event.getPlayer().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getPlayer().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
        // 清理内存缓存，防止内存泄漏
        service.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        service.forgetCast(event.getEntity().getUniqueId());
        if (service.isPlayerInMinigame(event.getEntity().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getEntity().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        service.forgetCast(event.getPlayer().getUniqueId());
        if (service.isPlayerInMinigame(event.getPlayer().getUniqueId())) {
            var minigame = service.getActiveMinigame(event.getPlayer().getUniqueId());
            if (minigame != null) {
                minigame.cleanup();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(@NotNull PlayerItemDamageEvent event) {
        if (event.getItem().getType() != Material.FISHING_ROD) return;
        int bonus = service.getRodDurabilityBonus(event.getItem());
        if (bonus <= 0) return;
        int newDamage = Math.max(0, event.getDamage() - bonus);
        if (newDamage == 0) {
            event.setCancelled(true);
        } else {
            event.setDamage(newDamage);
        }
    }
}
