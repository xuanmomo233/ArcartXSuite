package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

final class CombatDisplayDamageListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayDamageListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        service.handleDamage(event);
    }
}

