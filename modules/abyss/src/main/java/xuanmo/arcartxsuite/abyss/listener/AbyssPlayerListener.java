package xuanmo.arcartxsuite.abyss.listener;

import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;

/**
 * 玩家生命周期监听器：
 * <ul>
 *   <li>进服/退服：恢复位置、清理实例成员</li>
 *   <li>致命伤害拦截 → 旁观死亡（替代 PlayerDeathEvent）</li>
 *   <li>实体死亡 → 怪物组存活计数 + 击杀统计</li>
 *   <li>防逃逸：传送方式拦截 + 越界拉回</li>
 *   <li>禁用命令拦截</li>
 *   <li>队伍聊天转发</li>
 *   <li>伤害统计（attributeBridge / Bukkit 双通道）</li>
 * </ul>
 */
public final class AbyssPlayerListener implements Listener {

    private final AbyssService service;
    private final Logger logger;

    public AbyssPlayerListener(AbyssService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    // ═══ 进服/退服 ═══

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }

    // ═══ 致命伤害拦截（旁观死亡机制核心） ═══

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null) return;
        if (service.handleFatalDamage(instance, player, event)) {
            event.setCancelled(true);
        }
    }

    /** 兜底：万一伤害没被拦截（图腾等），真死了也按副本死亡处理。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.getStatus() != AbyssStatus.ACTIVE) return;
        if (true) { // 副本世界已设 keepInventory 规则，真死兜底仍保物品
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        if (!instance.isPlayerCurrentlyDead(player)) {
            instance.recordDeath(player);
            service.runLifecycleScript(instance, "player_death", player);
        }
    }

    // ═══ 实体死亡 → 怪物组 ═══

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        AbyssInstance instance = service.findInstanceByWorld(entity.getWorld());
        if (instance == null) return;
        Player killer = event.getEntity().getKiller();
        service.mobGroups().onEntityDeath(entity.getUniqueId(), instance, killer);
    }

    // ═══ 防逃逸 ═══

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.isFinished()) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        boolean blocked = switch (cause) {
            case ENDER_PEARL -> service.config().escape().blockEnderPearl();
            case CHORUS_FRUIT -> service.config().escape().blockChorusFruit();
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> service.config().escape().blockPortals();
            case SPECTATE -> service.config().escape().blockSpectateTeleport();
            default -> false;
        };
        // 旁观者传送（旁观死亡玩家点实体跟随）也算逃逸尝试
        if (!blocked && cause == PlayerTeleportEvent.TeleportCause.SPECTATE
            && instance.isPlayerCurrentlyDead(player)) {
            blocked = true;
        }
        if (!blocked) return;

        var mode = service.config().escape().mode();
        switch (mode) {
            case BLOCK -> {
                event.setCancelled(true);
                player.sendMessage(service.msg("dungeon.escape-blocked"));
            }
            case TELEPORT_BACK -> {
                event.setCancelled(true);
                var spawn = service.spawnLocation(instance);
                if (spawn != null) player.teleport(spawn);
                player.sendMessage(service.msg("dungeon.escape-blocked"));
            }
            case FAIL -> {
                event.setCancelled(true);
                service.failDungeon(instance, xuanmo.arcartxsuite.abyss.instance.FailReason.ESCAPE);
            }
        }
    }

    // ═══ 禁用命令 ═══

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance == null || instance.isFinished()) return;
        if (player.hasPermission("abyss.bypass.commands")) return;
        String raw = event.getMessage().substring(1); // 去 '/'
        String cmd = raw.split(" ")[0].toLowerCase();
        var blocked = service.config().restrictions().blockedCommands();
        for (String b : blocked) {
            if (cmd.equalsIgnoreCase(b) || raw.toLowerCase().startsWith(b.toLowerCase() + " ")) {
                event.setCancelled(true);
                player.sendMessage(service.msg("dungeon.command-blocked"));
                return;
            }
        }
    }

    // ═══ 禁用物品（消耗类：紫颂果等通过传送拦截已覆盖；此处理 block-interact 由 InteractListener 管） ═══

    // ═══ 队伍聊天 ═══

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        var party = service.party().partyOf(player);
        if (party == null) return;
        // 前缀触发：'!' 开头发队伍频道；或 toggle 状态全转发
        String msg = event.getMessage();
        if (msg.startsWith("!")) {
            event.setCancelled(true);
            String content = msg.substring(1).trim();
            if (!content.isEmpty()) {
                service.party().sendChat(player, content);
            }
        } else if (party.isChatEnabled(player.getUniqueId())) {
            event.setCancelled(true);
            service.party().sendChat(player, msg);
        }
    }

    // ═══ 战斗统计 ═══

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        // 攻击者=副本玩家 → recordDamage；受害者=副本玩家 → recordDamageTaken
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager instanceof Player p) {
            AbyssInstance i = service.instances().getInstance(p);
            if (i != null && service.stats() != null) {
                service.stats().recordDamageDealt(i, p.getUniqueId(), event.getFinalDamage());
            }
        }
        if (victim instanceof Player p) {
            AbyssInstance i = service.instances().getInstance(p);
            if (i != null && service.stats() != null) {
                service.stats().recordDamageTaken(i, p.getUniqueId(), event.getFinalDamage());
            }
        }
    }
}
