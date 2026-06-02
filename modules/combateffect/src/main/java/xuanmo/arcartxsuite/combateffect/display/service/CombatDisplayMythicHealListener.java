package xuanmo.arcartxsuite.combateffect.display.service;

import io.lumine.mythic.bukkit.events.MythicHealMechanicEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class CombatDisplayMythicHealListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayMythicHealListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler
    public void onMythicHeal(MythicHealMechanicEvent event) {
        if (event.getTarget() instanceof Player player) {
            service.handleMythicHeal(player, event.getHealAmount());
        }
    }
}

