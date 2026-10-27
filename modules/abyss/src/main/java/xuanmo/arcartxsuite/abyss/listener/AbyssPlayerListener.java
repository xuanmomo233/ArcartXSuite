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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
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
        if (service.isDesignInstance(instance)) {
            event.setCancelled(true);
            return;
        }
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
        if (service.isDesignInstance(instance)) return;
        if (!instance.isPlayerCurrentlyDead(player)) {
            int deaths = instance.recordDeath(player);
            service.runLifecycleScript(instance, "player_death", player);
            // 与拦截路径对齐：EVENT 任务（player_death）+ EventBus + 全队覆灭判定
            service.notifyPlayerDeath(instance, player, deaths);
        }
    }

    /**
     * 真死兜底的重生位置：fatal-damage-check 关闭时玩家会真死，
     * 原版默认复活到床位/世界出生点（副本外）——拉回副本出生点并旁观化，
     * 与拦截路径的观感一致。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        AbyssInstance instance = service.instances().getInstance(player);
        if (instance != null && service.isDesignInstance(instance)) {
            var spawn = service.spawnLocation(instance);
            if (spawn != null) event.setRespawnLocation(spawn);
            return;
        }
        if (instance == null || !instance.isPlayerCurrentlyDead(player)) return;
        var spawn = service.spawnLocation(instance);
        if (spawn != null) event.setRespawnLocation(spawn);
        // 旁观化需下一拍执行（respawn 事件内直接改模式无效）
        service.scheduleOnce(instance, () -> {
            if (player.isOnline() && instance.isPlayerCurrentlyDead(player)
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
        }, 1L);
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
        if (service.isDesignInstance(instance)) {
            var session = service.designManager() != null ? service.designManager().session(player) : null;
            if (event.getTo() != null && event.getTo().getWorld() != instance.getWorld()
                && (session == null || !session.saving())) {
                event.setCancelled(true);
                player.sendMessage("§e请先退出设计模式，再离开设计世界。");
            }
            return;
        }

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
        if (service.isDesignInstance(instance)) return;
        if (player.hasPermission("arcartxsuite.abyss.bypass")) return;
        String raw = event.getMessage().substring(1); // 去 '/'
        String cmd = raw.split(" ")[0].toLowerCase();
        String rawLower = raw.toLowerCase();

        // 副本级禁用（disabled_commands）优先：与黑名单同语义
        for (String b : instance.getTemplate().requirements().disabledCommands()) {
            if (cmd.equalsIgnoreCase(b) || rawLower.startsWith(b.toLowerCase() + " ")) {
                event.setCancelled(true);
                player.sendMessage(service.msg("dungeon.command-blocked"));
                return;
            }
        }

        // 命令控制：副本级 command_control 优先，INHERIT 时沿用全局 restrictions
        var cc = instance.getTemplate().worldRules().commandControl();
        AbyssModuleConfiguration.CommandMode mode;
        java.util.List<String> list; // 全限定避免与 List import 冲突依赖
        if (cc.mode() == xuanmo.arcartxsuite.abyss.template.WorldRules.CommandControl.ControlMode.INHERIT) {
            mode = service.config().restrictions().commandMode();
            list = service.config().restrictions().blockedCommands();
        } else {
            mode = cc.mode() == xuanmo.arcartxsuite.abyss.template.WorldRules.CommandControl.ControlMode.BLACKLIST
                ? AbyssModuleConfiguration.CommandMode.BLACKLIST
                : AbyssModuleConfiguration.CommandMode.WHITELIST;
            list = cc.commands();
        }
        boolean hit = false;
        for (String b : list) {
            if (cmd.equalsIgnoreCase(b) || rawLower.startsWith(b.toLowerCase() + " ")) {
                hit = true;
                break;
            }
        }
        boolean block = switch (mode) {
            case NONE -> false;
            case BLACKLIST -> hit;
            case WHITELIST -> !hit;
        };
        if (block) {
            event.setCancelled(true);
            player.sendMessage(service.msg("dungeon.command-blocked"));
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

    /** 副本内回血统计（自然恢复/药水/图腾全算）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        AbyssInstance i = service.instances().getInstance(p);
        if (i == null || service.stats() == null) return;
        // 实际恢复量 = min(请求量, 生命缺口)，避免满血边缘虚增统计
        double maxHealth = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
            ? p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
            : 20.0;
        double effective = Math.min(event.getAmount(), Math.max(0, maxHealth - p.getHealth()));
        if (effective > 0) {
            service.stats().recordHealing(i, p.getUniqueId(), effective);
        }
    }
}
