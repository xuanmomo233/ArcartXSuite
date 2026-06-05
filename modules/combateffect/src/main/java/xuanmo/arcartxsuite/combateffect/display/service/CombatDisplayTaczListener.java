package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.event.TaczGunDamageEvent;

/**
 * 监听 {@link TaczGunDamageEvent}，将 TACZ 枪械伤害交由 {@link CombatDisplayService} 处理。
 */
final class CombatDisplayTaczListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayTaczListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler
    public void onTaczGunDamage(TaczGunDamageEvent event) {
        service.handleTaczDamage(event);
    }
}
