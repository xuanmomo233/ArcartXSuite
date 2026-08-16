package xuanmo.arcartxsuite.battlepass.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.Plugin;

/**
 * 原版 Bukkit 事件降级监听器。
 * <p>
 * 当 ArcartX EventBus 发布模块不存在时，battlepass 通过此监听器直接监听原版 Bukkit 事件，
 * 从事件对象中提取 payload 字段（与 EventBus payload key 保持一致），再回调任务引擎处理。
 * <p>
 * 支持的原版事件类型及 payload 提取规则：
 * <ul>
 *   <li>{@code EntityDeathEvent} → {@code entityType}, {@code entityName}</li>
 *   <li>{@code EntityDamageByEntityEvent} → {@code attacker}, {@code targetType}, {@code targetName}, {@code damage}</li>
 *   <li>{@code PlayerJoinEvent} → （无 payload 字段）</li>
 *   <li>{@code PlayerPickupItemEvent} → {@code material}, {@code amount}</li>
 *   <li>{@code PlayerFishEvent} → {@code fishType}, {@code state}</li>
 * </ul>
 * <p>
 * 击杀事件需要从 {@link EntityDeathEvent} 中获取击杀者（{@code entity.getKiller()}），
 * 伤害事件需要确认 damager 是 Player，拾取事件直接取 player。
 */
public final class BukkitEventSourceListener implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final BiConsumer<Player, Map<String, String>> killCallback;
    private final BiConsumer<Player, Map<String, String>> damageCallback;
    private final BiConsumer<Player, Map<String, String>> joinCallback;
    private final BiConsumer<Player, Map<String, String>> pickupCallback;
    private final BiConsumer<Player, Map<String, String>> fishCallback;

    private BukkitEventSourceListener(Plugin plugin, Logger logger,
                                       BiConsumer<Player, Map<String, String>> killCallback,
                                       BiConsumer<Player, Map<String, String>> damageCallback,
                                       BiConsumer<Player, Map<String, String>> joinCallback,
                                       BiConsumer<Player, Map<String, String>> pickupCallback,
                                       BiConsumer<Player, Map<String, String>> fishCallback) {
        this.plugin = plugin;
        this.logger = logger;
        this.killCallback = killCallback;
        this.damageCallback = damageCallback;
        this.joinCallback = joinCallback;
        this.pickupCallback = pickupCallback;
        this.fishCallback = fishCallback;
    }

    /**
     * 根据需要监听的事件类型集合创建并注册监听器。
     *
     * @param plugin          插件实例
     * @param logger          日志器
     * @param eventClasses    需要监听的 Bukkit 事件全类名集合
     * @param killCallback    击杀事件回调（player, payload）
     * @param damageCallback  伤害事件回调（player, payload）
     * @param joinCallback    登录事件回调（player, payload）
     * @param pickupCallback  拾取事件回调（player, payload）
     * @param fishCallback    钓鱼事件回调（player, payload）
     * @return 已注册的监听器实例，调用 {@link #unregister()} 注销
     */
    public static BukkitEventSourceListener register(Plugin plugin, Logger logger,
                                                      java.util.Set<String> eventClasses,
                                                      BiConsumer<Player, Map<String, String>> killCallback,
                                                      BiConsumer<Player, Map<String, String>> damageCallback,
                                                      BiConsumer<Player, Map<String, String>> joinCallback,
                                                      BiConsumer<Player, Map<String, String>> pickupCallback,
                                                      BiConsumer<Player, Map<String, String>> fishCallback) {
        BukkitEventSourceListener listener = new BukkitEventSourceListener(
            plugin, logger, killCallback, damageCallback, joinCallback, pickupCallback, fishCallback);
        // 只在有对应回调时才注册（避免不必要的监听）
        boolean needKill = eventClasses.contains(EntityDeathEvent.class.getName()) && killCallback != null;
        boolean needDamage = eventClasses.contains(EntityDamageByEntityEvent.class.getName()) && damageCallback != null;
        boolean needJoin = eventClasses.contains(PlayerJoinEvent.class.getName()) && joinCallback != null;
        boolean needPickup = eventClasses.contains(PlayerPickupItemEvent.class.getName()) && pickupCallback != null;
        boolean needFish = eventClasses.contains(PlayerFishEvent.class.getName()) && fishCallback != null;
        if (needKill || needDamage || needJoin || needPickup || needFish) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            logger.info("[battlepass] 原版事件降级监听已注册: kill=" + needKill + " damage=" + needDamage
                + " join=" + needJoin + " pickup=" + needPickup + " fish=" + needFish);
        }
        return listener;
    }

    /**
     * 注销此监听器的所有事件监听。
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    // ─── 击杀事件：EntityDeathEvent → axs.combateffect.kill_entity ───

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (killCallback == null) return;
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("entityType", entity.getType().name());
        payload.put("entityName", entity.getName());
        try {
            killCallback.accept(killer, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[battlepass] 击杀事件处理异常", e);
        }
    }

    // ─── 伤害事件：EntityDamageByEntityEvent → axs.combateffect.damage_dealt ───

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (damageCallback == null) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!attacker.isOnline()) return;
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("attacker", attacker.getName());
        payload.put("targetType", target.getType().name());
        payload.put("targetName", target.getName());
        payload.put("damage", String.valueOf(event.getFinalDamage()));
        try {
            damageCallback.accept(attacker, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[battlepass] 伤害事件处理异常", e);
        }
    }

    // ─── 登录事件：PlayerJoinEvent → axs.loginview.login_success ───

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (joinCallback == null) return;
        Player player = event.getPlayer();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("auth_mode", "offline");
        payload.put("account_type", "offline");
        try {
            joinCallback.accept(player, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[battlepass] 登录事件处理异常", e);
        }
    }

    // ─── 拾取事件：PlayerPickupItemEvent → axs.pickup.item_pickup ───

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (pickupCallback == null) return;
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        org.bukkit.inventory.ItemStack itemStack = event.getItem().getItemStack();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("material", itemStack.getType().name());
        payload.put("amount", String.valueOf(itemStack.getAmount()));
        try {
            pickupCallback.accept(player, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[battlepass] 拾取事件处理异常", e);
        }
    }

    // ─── 钓鱼事件：PlayerFishEvent → axs.fishing.success ───

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (fishCallback == null) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        if (event.getCaught() != null) {
            payload.put("fishType", event.getCaught().getType().name());
        }
        payload.put("state", event.getState().name());
        try {
            fishCallback.accept(player, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[battlepass] 钓鱼事件处理异常", e);
        }
    }
}
