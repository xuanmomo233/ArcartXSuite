package xuanmo.arcartxsuite.eventpacket.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;
import xuanmo.arcartxsuite.eventpacket.service.PapiWatcherService;

/**
 * 玩家事件监听器，负责将 Bukkit 原生事件桥接到事件包分发系统。
 * <p>
 * 监听玩家进服、退服以及实体死亡事件，分别触发对应的事件包规则，
 * 并在玩家退服时清理 PAPI 监听器中缓存的玩家状态。
 */
public final class PlayerEventPacketListener implements Listener {

    private final EventPacketDispatchService dispatchService;
    private final PapiWatcherService watcherService;

    /**
     * 构造玩家事件监听器。
     *
     * @param dispatchService 事件包分发服务
     * @param watcherService  PAPI 监听服务，可为 {@code null}
     */
    public PlayerEventPacketListener(EventPacketDispatchService dispatchService, PapiWatcherService watcherService) {
        this.dispatchService = dispatchService;
        this.watcherService = watcherService;
    }

    /**
     * 玩家进服事件：触发 JOIN 规则，首次进服额外触发 FIRST_JOIN 规则。
     *
     * @param event 玩家进服事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        Player player = org.bukkit.Bukkit.getPlayer(event.snapshot().playerId());
        if (player == null || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
        dispatchService.dispatchAll(
            EventPacketTrigger.ADMITTED,
            player,
            EventPacketContext.fromSubjectTrigger(EventPacketTrigger.ADMITTED, player)
        );
    }

    /**
     * 玩家退服事件：触发 QUIT 规则并清理分发服务与 PAPI 监听器中的玩家状态。
     *
     * @param event 玩家退服事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        dispatchService.dispatchAll(
            EventPacketTrigger.QUIT,
            event.getPlayer(),
            EventPacketContext.fromSubjectTrigger(EventPacketTrigger.QUIT, event.getPlayer())
        );
        dispatchService.clearPlayerState(event.getPlayer());
        if (watcherService != null) {
            watcherService.clearPlayer(event.getPlayer().getUniqueId());
        }
    }

    /**
     * 实体死亡事件：记录击杀者击杀进度，交由 PAPI 监听服务判定是否触发击杀计数规则。
     *
     * @param event 实体死亡事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        String mythicMobId = EntityCombatMetadata.resolveMythicMobId(entity);
        if (watcherService != null) {
            watcherService.recordMobKill(
                killer,
                entity.getWorld().getName(),
                EntityCombatMetadata.resolveEntityType(entity),
                mythicMobId
            );
        }
    }
}
