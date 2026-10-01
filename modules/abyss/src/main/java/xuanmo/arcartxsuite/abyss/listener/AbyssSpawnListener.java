package xuanmo.arcartxsuite.abyss.listener;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.WorldRules;

/**
 * 副本世界刷怪控制（BTDungeons 同款）：
 * <ul>
 *   <li>{@code disabled_spawn_reasons}：按生成来源精确禁用</li>
 *   <li>{@code disable_natural_spawn}：禁环境驱动刷怪（自然/刷怪笼/巡逻队/袭击/刷蛋石等），
 *       插件/命令/刷怪蛋/玩法产生的生成不受影响（副本脚本刷怪走 CUSTOM）</li>
 * </ul>
 */
public final class AbyssSpawnListener implements Listener {

    /** disable_natural_spawn 时取消的环境驱动来源。 */
    private static final Set<CreatureSpawnEvent.SpawnReason> ENVIRONMENTAL =
        EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.SPAWNER,
            CreatureSpawnEvent.SpawnReason.BREEDING,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.LIGHTNING,
            CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            CreatureSpawnEvent.SpawnReason.DROWNED,
            CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK,
            CreatureSpawnEvent.SpawnReason.INFECTION,
            CreatureSpawnEvent.SpawnReason.OCELOT_BABY,
            CreatureSpawnEvent.SpawnReason.ENDER_PEARL
        );

    private final AbyssService service;
    private final Logger logger;

    public AbyssSpawnListener(AbyssService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        var instance = service.findInstanceByWorld(event.getLocation().getWorld());
        if (instance == null) return;
        WorldRules rules = instance.getTemplate().worldRules();
        var reason = event.getSpawnReason();
        // 用户来源黑名单优先（任一来源可禁用，包括 CUSTOM）
        for (String r : rules.disabledSpawnReasons()) {
            if (r.equalsIgnoreCase(reason.name())) {
                event.setCancelled(true);
                return;
            }
        }
        if (rules.disableNaturalSpawn() && ENVIRONMENTAL.contains(reason)) {
            event.setCancelled(true);
        }
    }
}
