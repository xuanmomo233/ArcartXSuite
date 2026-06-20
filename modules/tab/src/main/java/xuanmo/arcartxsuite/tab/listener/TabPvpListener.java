package xuanmo.arcartxsuite.tab.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;

/**
 * 监听玩家间伤害事件，记录最近一次 PVP 时间戳，
 * 供 {@code %axstab_pvp%} / {@code %axstab_pvp_color%} 占位符判定 PVP 高亮窗口。
 *
 * <p>受 {@code settings.style.pvp-highlight.enabled} 控制；事件总是被监听，
 * 由 {@link TabSyncService#isPvpActive} 在读取时校验开关，避免反复装拆 Listener。
 */
public final class TabPvpListener implements Listener {

    private final TabSyncService service;

    public TabPvpListener(TabSyncService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        Player victim = event.getEntity() instanceof Player p ? p : null;
        if (attacker == null && victim == null) {
            return;
        }
        if (attacker != null && victim != null && attacker.equals(victim)) {
            return;
        }
        service.recordPvpEvent(
            attacker == null ? null : attacker.getUniqueId(),
            victim == null ? null : victim.getUniqueId()
        );
    }

    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player pp) {
                return pp;
            }
        }
        return null;
    }
}
