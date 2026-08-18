package xuanmo.arcartxsuite.battlepass.source;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.event.TaczGunDamageEvent;
import xuanmo.arcartxsuite.api.event.TaczGunKillEvent;

/**
 * 原版 Bukkit 事件降级监听器。
 * <p>
 * 监听器通过规范化后的 Bukkit 事件全类名查找回调。所有事件回调均在 MONITOR
 * 阶段执行，异常只影响当前回调，不影响其他任务。
 * <p>
 * 除原版 Bukkit 事件外，还监听 TACZ 枪械事件（{@link TaczGunDamageEvent} / {@link TaczGunKillEvent}），
 * 作为 combateffect 模块未加载时的降级路径，确保枪械伤害和击杀也能被战令任务追踪。
 */
public final class BukkitEventSourceListener implements Listener {

    private static final String ENTITY_DEATH = EntityDeathEvent.class.getName();
    private static final String ENTITY_DAMAGE = EntityDamageByEntityEvent.class.getName();
    private static final String PLAYER_JOIN = PlayerJoinEvent.class.getName();
    private static final String ENTITY_PICKUP = EntityPickupItemEvent.class.getName();
    private static final String PLAYER_FISH = PlayerFishEvent.class.getName();
    private static final String BLOCK_BREAK = BlockBreakEvent.class.getName();
    private static final String BLOCK_PLACE = BlockPlaceEvent.class.getName();
    private static final String CRAFT_ITEM = CraftItemEvent.class.getName();
    private static final String ITEM_CONSUME = PlayerItemConsumeEvent.class.getName();
    private static final String PLAYER_DEATH = PlayerDeathEvent.class.getName();
    private static final String TACZ_DAMAGE = TaczGunDamageEvent.class.getName();
    private static final String TACZ_KILL = TaczGunKillEvent.class.getName();
    private static final Set<String> SUPPORTED_EVENT_CLASSES = Set.of(
        EntityDeathEvent.class.getName(),
        EntityDamageByEntityEvent.class.getName(),
        PlayerJoinEvent.class.getName(),
        "org.bukkit.event.player.PlayerPickupItemEvent",
        EntityPickupItemEvent.class.getName(),
        PlayerFishEvent.class.getName(),
        BlockBreakEvent.class.getName(),
        BlockPlaceEvent.class.getName(),
        CraftItemEvent.class.getName(),
        PlayerItemConsumeEvent.class.getName(),
        PlayerDeathEvent.class.getName(),
        TaczGunDamageEvent.class.getName(),
        TaczGunKillEvent.class.getName()
    );

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, BiConsumer<Player, Map<String, String>>> callbacks;

    private BukkitEventSourceListener(
        Plugin plugin,
        Logger logger,
        Map<String, BiConsumer<Player, Map<String, String>>> callbacks
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.callbacks = callbacks;
    }

    /**
     * 根据事件类名到回调的映射创建并注册监听器。
     *
     * @param plugin 插件实例
     * @param logger 日志记录器
     * @param callbacks 规范化 Bukkit 事件类名到分发回调的映射
     * @return 已注册的监听器实例
     */
    public static BukkitEventSourceListener register(
        Plugin plugin,
        Logger logger,
        Map<String, BiConsumer<Player, Map<String, String>>> callbacks
    ) {
        BukkitEventSourceListener listener =
            new BukkitEventSourceListener(plugin, logger, callbacks);
        if (callbacks != null && !callbacks.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            logger.info("[battlepass] 已注册 Bukkit 事件监听器: " + callbacks.keySet());
        }
        return listener;
    }

    /**
     * 注销监听器。
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player player = entity.getKiller();
        if (player == null || !player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("entityType", entity.getType().name());
        payload.put("entityName", entity.getName());
        payload.put("world", entity.getWorld().getName());
        dispatch(ENTITY_DEATH, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.isOnline()) return;
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("attacker", player.getName());
        payload.put("targetType", target.getType().name());
        payload.put("targetName", target.getName());
        payload.put("damage", String.valueOf(event.getFinalDamage()));
        payload.put("world", player.getWorld().getName());
        dispatch(ENTITY_DAMAGE, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("world", player.getWorld().getName());
        dispatch(PLAYER_JOIN, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isOnline()) return;
        ItemStack item = event.getItem().getItemStack();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("material", item.getType().name());
        payload.put("amount", String.valueOf(item.getAmount()));
        payload.put("world", event.getItem().getWorld().getName());
        dispatch(ENTITY_PICKUP, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        if (event.getCaught() != null) payload.put("fishType", event.getCaught().getType().name());
        payload.put("state", event.getState().name());
        payload.put("world", player.getWorld().getName());
        dispatch(PLAYER_FISH, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        dispatchBlock(BLOCK_BREAK, player, event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        dispatchBlock(BLOCK_PLACE, player, event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.isOnline()) return;
        if (event.getRecipe() == null) return;
        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("material", result.getType().name());
        payload.put("amount", String.valueOf(result.getAmount()));
        payload.put("world", player.getWorld().getName());
        dispatch(CRAFT_ITEM, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("material", event.getItem().getType().name());
        payload.put("world", player.getWorld().getName());
        dispatch(ITEM_CONSUME, player, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.isOnline()) return;
        Map<String, String> payload = new LinkedHashMap<>();
        if (player.getLastDamageCause() != null) {
            payload.put("deathCause", player.getLastDamageCause().getCause().name());
        }
        payload.put("world", player.getWorld().getName());
        dispatch(PLAYER_DEATH, player, payload);
    }

    /**
     * 监听 TACZ 枪械伤害事件。
     * <p>
     * TACZ 伤害不触发 {@link EntityDamageByEntityEvent}，必须单独监听。
     * payload 结构与 {@link #onEntityDamageByEntity} 一致，下游任务无需区分伤害来源。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaczGunDamage(TaczGunDamageEvent event) {
        Player player = event.getAttacker();
        if (!player.isOnline()) return;
        LivingEntity target = event.getTarget();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("attacker", player.getName());
        payload.put("targetType", target.getType().name());
        payload.put("targetName", target.getName());
        payload.put("damage", String.valueOf(event.getDamage()));
        payload.put("world", player.getWorld().getName());
        dispatch(TACZ_DAMAGE, player, payload);
    }

    /**
     * 监听 TACZ 枪械击杀事件。
     * <p>
     * TACZ 击杀不保证 {@code LivingEntity.getKiller()} 返回枪手，
     * 必须通过此事件准确获取击杀者。payload 结构与 {@link #onEntityDeath} 一致。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaczGunKill(TaczGunKillEvent event) {
        Player player = event.getAttacker();
        if (!player.isOnline()) return;
        LivingEntity victim = event.getKilledEntity();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("entityType", victim.getType().name());
        payload.put("entityName", victim.getName());
        payload.put("world", victim.getWorld().getName());
        dispatch(TACZ_KILL, player, payload);
    }

    private void dispatchBlock(String eventClass, Player player, Block block) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("material", block.getType().name());
        payload.put("world", block.getWorld().getName());
        dispatch(eventClass, player, payload);
    }

    /**
     * 判断 Bukkit 事件类名是否受支持。
     *
     * @param eventClass Bukkit 事件完整类名
     * @return {@code true} 表示支持该事件
     */
    public static boolean isSupportedBukkitEvent(String eventClass) {
        return SUPPORTED_EVENT_CLASSES.contains(eventClass);
    }

    /**
     * 将兼容的旧拾取事件类名规范化为当前监听事件类名。
     *
     * @param eventClass Bukkit 事件完整类名
     * @return 规范化后的事件类名
     */
    public static String normalizeBukkitEventClass(String eventClass) {
        if ("org.bukkit.event.player.PlayerPickupItemEvent".equals(eventClass)) {
            return ENTITY_PICKUP;
        }
        return eventClass;
    }

    private void dispatch(
        String eventClass, Player player, Map<String, String> payload
    ) {
        BiConsumer<Player, Map<String, String>> callback = callbacks.get(eventClass);
        if (callback == null) return;
        try {
            callback.accept(player, payload);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                "[battlepass] Bukkit 事件回调异常: " + eventClass, e);
        }
    }
}
