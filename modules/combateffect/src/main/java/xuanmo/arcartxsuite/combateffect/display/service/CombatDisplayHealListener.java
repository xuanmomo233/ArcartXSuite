package xuanmo.arcartxsuite.combateffect.display.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * 战斗显示治疗事件监听器。
 * <p>
 * 属于 combateffect 模块的 display 子模块，监听 Bukkit 原生
 * {@link EntityRegainHealthEvent}，将治疗事件转发给
 * {@link CombatDisplayService} 处理。
 */
final class CombatDisplayHealListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayHealListener(CombatDisplayService service) {
        this.service = service;
    }

    /**
     * 处理实体恢复生命值事件。
     *
     * @param event 实体恢复生命值事件
     */
    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        service.handleHeal(event);
    }
}

