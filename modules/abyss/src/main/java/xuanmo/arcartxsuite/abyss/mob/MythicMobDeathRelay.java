package xuanmo.arcartxsuite.abyss.mob;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import java.util.function.BiFunction;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;

/**
 * MythicMobs 死亡事件中继（可选依赖隔离类，仅 MM 启用时注册）。
 * <p>
 * MM 怪的死亡在 EntityDeathEvent 之前由 MM 自己处理掉落/经验，
 * 用 {@code MythicMobDeathEvent} 拿到 killer（可能是 MM 技能击杀
 * 而非直接实体击杀），转发给 {@link MonsterGroupManager}。
 */
public final class MythicMobDeathRelay implements Listener {

    private final MonsterGroupManager groups;
    private final BiFunction<Entity, org.bukkit.World, AbyssInstance> instanceLookup;

    public MythicMobDeathRelay(MonsterGroupManager groups,
                               BiFunction<Entity, org.bukkit.World, AbyssInstance> instanceLookup) {
        this.groups = groups;
        this.instanceLookup = instanceLookup;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;
        AbyssInstance instance = instanceLookup.apply(entity, entity.getWorld());
        if (instance == null) return;
        Player killer = null;
        if (event.getKiller() instanceof Player p) {
            killer = p;
        }
        groups.onEntityDeath(entity.getUniqueId(), instance, killer);
    }
}
