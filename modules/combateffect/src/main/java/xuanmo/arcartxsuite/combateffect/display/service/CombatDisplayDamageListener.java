package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 战斗显示伤害事件监听器。
 * <p>
 * 属于 combateffect 模块的 display 子模块，监听 Bukkit 原生
 * {@link EntityDamageByEntityEvent}，将伤害事件转发给
 * {@link CombatDisplayService} 处理。
 */
final class CombatDisplayDamageListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayDamageListener(CombatDisplayService service) {
        this.service = service;
    }

    /**
     * 处理实体间伤害事件，忽略已被取消的事件。
     *
     * @param event 实体间伤害事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        service.handleDamage(event);
    }
}

