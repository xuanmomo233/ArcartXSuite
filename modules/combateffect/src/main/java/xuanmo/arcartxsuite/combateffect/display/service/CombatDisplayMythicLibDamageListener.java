package xuanmo.arcartxsuite.combateffect.display.service;

import io.lumine.mythic.lib.api.event.AttackUnregisteredEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

final class CombatDisplayMythicLibDamageListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayMythicLibDamageListener(CombatDisplayService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(AttackUnregisteredEvent event) {
        EntityDamageEvent bukkitEvent = event.toBukkit();
        double finalDamage = bukkitEvent == null ? event.getDamage().getDamage() : bukkitEvent.getFinalDamage();
        service.handleMythicLibDamage(event.getEntity(), finalDamage);
    }
}

