package xuanmo.arcartxsuite.combateffect.display.service;

import io.lumine.mythic.bukkit.events.MythicHealMechanicEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * MythicMobs 治疗事件监听器。
 * <p>
 * 属于 combateffect 模块的 display 子模块，监听 MythicMobs 的
 * {@link MythicHealMechanicEvent}，仅当治疗目标为玩家时
 * 将治疗量转发给 {@link CombatDisplayService} 处理。
 */
final class CombatDisplayMythicHealListener implements Listener {

    private final CombatDisplayService service;

    CombatDisplayMythicHealListener(CombatDisplayService service) {
        this.service = service;
    }

    /**
     * 处理 MythicMobs 治疗机制事件，仅处理目标为玩家的情况。
     *
     * @param event MythicMobs 治疗机制事件
     */
    @EventHandler
    public void onMythicHeal(MythicHealMechanicEvent event) {
        if (event.getTarget() instanceof Player player) {
            service.handleMythicHeal(player, event.getHealAmount());
        }
    }
}

