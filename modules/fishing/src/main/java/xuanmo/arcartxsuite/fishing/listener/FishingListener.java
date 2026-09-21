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

        // 在 BITE 状态（鱼咬钩）时启动小游戏，而不是等 CAUGHT_FISH
        // CAUGHT_FISH 需要玩家右键收杆才触发，而我们要在收杆前就启动小游戏
        if (event.getState() == PlayerFishEvent.State.BITE) {
            // 取消原版钓鱼（阻止后续 CAUGHT_FISH/FAILED_ATTEMPT），启动小游戏
            boolean started = service.startMinigame(player);
            if (started) {
                // 小游戏启动成功后才消耗饵料——启动失败（packetBridge 不可用/冷却/权限）不吞饵
                service.consumeBaitOnBite(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        service.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
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
