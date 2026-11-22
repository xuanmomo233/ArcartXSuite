package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;

final class CombatDisplayDamageListener implements Listener {
    private final CombatDisplayService service;

    CombatDisplayDamageListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(CombatDamageEvent event) {
        service.handleDamage(event);
    }
}
