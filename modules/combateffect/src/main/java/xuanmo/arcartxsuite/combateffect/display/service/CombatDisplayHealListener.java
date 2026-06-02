package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

final class CombatDisplayHealListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayHealListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        service.handleHeal(event);
    }
}

