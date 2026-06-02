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

public final class PlayerEventPacketListener implements Listener {

    private final EventPacketDispatchService dispatchService;
    private final PapiWatcherService watcherService;

    public PlayerEventPacketListener(EventPacketDispatchService dispatchService, PapiWatcherService watcherService) {
        this.dispatchService = dispatchService;
        this.watcherService = watcherService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        dispatchService.dispatchAll(
            EventPacketTrigger.JOIN,
            event.getPlayer(),
            EventPacketContext.fromSubjectTrigger(EventPacketTrigger.JOIN, event.getPlayer())
        );
        if (!event.getPlayer().hasPlayedBefore()) {
            dispatchService.dispatchAll(
                EventPacketTrigger.FIRST_JOIN,
                event.getPlayer(),
                EventPacketContext.fromSubjectTrigger(EventPacketTrigger.FIRST_JOIN, event.getPlayer())
            );
        }
    }

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
