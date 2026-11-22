package xuanmo.arcartxsuite.pickup.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration;
import java.util.logging.Logger;

public final class PickupService implements Listener {

    private static final long PREOPEN_DELAY_TICKS = 5L;
    private static final long OPENED_PACKET_DELAY_TICKS = 1L;
    /** 等待 UI 打开回调的超时；超时只解除 opening，不把未确认界面当成已打开。 */
    private static final long OPEN_CONFIRM_DELAY_TICKS = 40L;
    private static final long UNCONFIRMED_OPEN_RETRY_TICKS = 200L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PickupModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final ItemBridgeAPI itemStackBridge;
    private final String uiId;
    private final Set<UUID> openedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attemptedOpen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask initialOpenTask, retryOpenTask;
    private final Map<UUID, Deque<Map<String, Object>>> pendingPickups = new ConcurrentHashMap<>();
    /** 每玩家待推送队列上限：超出丢最旧（防掉线/未开界面时无界堆积）。 */
    private static final int MAX_PENDING_PICKUPS = 64;
    /** 溢出告警节流间隔（毫秒）。 */
    private static final long OVERFLOW_WARN_INTERVAL_MS = 60_000L;
    private volatile long lastOverflowWarnAt;
    /** 手动关闭拾取通知的玩家集合 */
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private Supplier<EventBusCapability> eventBusProvider;
    private java.util.function.Function<ItemStack, String> itemNameResolver;
    private boolean active;

    public PickupService(
        JavaPlugin plugin,
        Logger logger,
        PickupModuleConfiguration configuration,
        PacketBridgeAPI bridge,
        ItemBridgeAPI itemStackBridge,
        String uiId
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
        this.itemStackBridge = itemStackBridge;
        this.uiId = uiId;
    }

    public void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bridge.registerUiCloseCallback(uiId, player -> {
            openedPlayers.remove(player.getUniqueId());
            openingPlayers.remove(player.getUniqueId());
            attemptedOpen.remove(player.getUniqueId());
            sessions.remove(player.getUniqueId());
        });
        initialOpenTask = Bukkit.getScheduler().runTaskLater(plugin, this::openForOnlinePlayers, PREOPEN_DELAY_TICKS);
        // 二次兜底：首次打开时客户端可能尚未就绪，延迟重试
        retryOpenTask = Bukkit.getScheduler().runTaskLater(plugin, this::openForOnlinePlayers, PREOPEN_DELAY_TICKS + OPEN_CONFIRM_DELAY_TICKS);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void setItemNameResolver(java.util.function.Function<ItemStack, String> resolver) {
        itemNameResolver = resolver;
    }

    public void shutdown() {
        active = false;
        if (initialOpenTask != null) initialOpenTask.cancel();
        if (retryOpenTask != null) retryOpenTask.cancel();
        bridge.unregisterUiCloseCallback(uiId);
        HandlerList.unregisterAll(this);
        var allOpen = new java.util.HashSet<>(openedPlayers);
        allOpen.addAll(openingPlayers);
        for (UUID playerId : allOpen) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                bridge.closeUi(player, uiId);
            }
        }
        openedPlayers.clear();
        openingPlayers.clear();
        attemptedOpen.clear();
        sessions.clear();
        pendingPickups.clear();
        disabledPlayers.clear();
    }

    /**
     * 切换玩家的拾取通知开关。
     *
     * @param playerId 玩家 UUID
     * @param enabled  true=开启，false=关闭，null=切换
     * @return 切换后的状态（true=已开启）
     */
    public boolean setEnabled(UUID playerId, Boolean enabled) {
        boolean currentlyDisabled = disabledPlayers.contains(playerId);
        boolean shouldDisable;
        if (enabled == null) {
            shouldDisable = !currentlyDisabled;
        } else {
            shouldDisable = !enabled;
        }
        if (shouldDisable) {
            disabledPlayers.add(playerId);
            sessions.remove(playerId);
            openingPlayers.remove(playerId);
            attemptedOpen.remove(playerId);
            // 立即关闭 HUD 并清理该玩家的打开状态与待发队列
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && bridge.isAvailable()) {
                bridge.closeUi(player, uiId);
            }
            openedPlayers.remove(playerId);
            pendingPickups.remove(playerId);
        } else {
            disabledPlayers.remove(playerId);
            // 立即重新打开 HUD（若当前未打开）
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                ensureUiOpen(player);
            }
        }
        return !shouldDisable;
    }

    /** 查询玩家的拾取通知是否开启。 */
    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    /**
     * 客户端初始化完成回调：为该玩家打开常驻通知 HUD。
     * 由 PickupModule#createInitializedHandler() 调用。
     * <p>
     * 这是玩家进服后打开 HUD 的唯一入口。PlayerJoinEvent 时客户端尚未就绪，
     * openUi 发的包无法被处理，因此不再监听 PlayerJoinEvent。
     * 客户端初始化完成是 HUD 就绪的可靠信号，此时 openUi 才能真正生效。
     */
    public void onClientInitialized(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        openedPlayers.remove(id);
        openingPlayers.remove(id);
        attemptedOpen.remove(id);
        sessions.remove(id);
        ensureUiOpen(player);
    }

    /**
     * 主动推送一条拾取通知（供 Warehouse 自动入库后补发 HUD 提示）。
     * <p>
     * Warehouse 在 LOWEST 优先级取消了 EntityPickupItemEvent，
     * 本类的监听器带 ignoreCancelled = true 因此不会触发，需要由此方法补发。
     *
     * @param player    目标玩家
     * @param itemStack 物品原型（不会被修改）
     * @param amount    实际获得数量
     * @return true 表示已排入推送流程
     */
    public boolean notifyPickup(Player player, ItemStack itemStack, int amount) {
        return notifyPickupPrepared(player, itemStack, amount, "");
    }

    public boolean notifyPickupPrepared(Player player, ItemStack itemStack, int amount, String previewJson) {
        if (!active || player == null || !player.isOnline() || itemStack == null) {
            if (configuration.debug()) {
                logger.info("[PickupDiag] notifyPickup early-return-1 active=" + active
                    + " player=" + (player != null ? player.getName() : "null")
                    + " online=" + (player != null && player.isOnline())
                    + " itemStack=" + (itemStack != null ? itemStack.getType() : "null"));
            }
            return false;
        }
        if (amount <= 0 || itemStack.getType() == Material.AIR) {
            if (configuration.debug()) {
                logger.info("[PickupDiag] notifyPickup early-return-2 amount=" + amount
                    + " type=" + itemStack.getType());
            }
            return false;
        }
        UUID playerId = player.getUniqueId();
        publishPickupEvent(player, itemStack, amount);
        if (disabledPlayers.contains(playerId)) {
            if (configuration.debug()) {
                logger.info("[PickupDiag] notifyPickup early-return-3 reason=disabled-by-player");
            }
            return false;
        }

        Map<String, Object> payload = notificationPayload(itemStack, amount, previewJson);

        if (openedPlayers.contains(playerId)) {
            if (configuration.debug()) {
                logger.info("[PickupDiag] notifyPickup already-opened -> scheduleSend");
            }
            scheduleSend(player, payload, OPENED_PACKET_DELAY_TICKS);
            return true;
        }

        Map<String, Object> queuedPayload = enqueuePickup(playerId, payload);
        boolean opened = ensureUiOpen(player);
        if (!opened) {
            removeQueuedPayload(playerId, queuedPayload);
            if (configuration.debug()) {
                logger.info("[PickupDiag] notifyPickup ensureUiOpen-failed"
                    + " bridgeAvailable=" + (bridge != null && bridge.isAvailable())
                    + " uiId=" + uiId
                    + " openedPlayers.contains=" + openedPlayers.contains(playerId));
            }
            logPickupPacket(player, payload, false);
            return false;
        }
        if (configuration.debug()) {
            logger.info("[PickupDiag] notifyPickup ensureUiOpen-ok -> queued");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        notifyPickup(player, stack, stack.getAmount());
    }

    private Map<String, Object> notificationPayload(ItemStack itemStack, int amount, String previewJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemJson", previewJson == null || previewJson.isBlank()
            ? itemStackBridge.itemToPreviewJson(itemStack).filter(json -> !json.isBlank())
                .orElse("{\"id\":\"minecraft:air\",\"Count\":1b}")
            : previewJson);
        if (itemNameResolver != null) {
            payload.put("name", itemNameResolver.apply(itemStack));
        } else {
            var meta = itemStack.getItemMeta();
            payload.put("name", meta != null && meta.hasDisplayName() ? meta.getDisplayName()
                : xuanmo.arcartxsuite.api.item.VanillaItemNameTable.translate(itemStack.getType()));
        }
        payload.put("amount", amount);
        return payload;
    }

    private void publishPickupEvent(Player player, ItemStack itemStack, int amount) {
        try {
            if (eventBusProvider == null) return;
            EventBusCapability eventBus = eventBusProvider.get();
            if (eventBus == null) return;
            Map<String, String> eventPayload = new HashMap<>();
            eventPayload.put("material", itemStack.getType().name());
            eventPayload.put("amount", String.valueOf(amount));
            eventBus.publish("axs.pickup.item_pickup", player, eventPayload);
        } catch (RuntimeException exception) {
            logger.warning("拾取事件通知失败: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openedPlayers.remove(playerId);
        pendingPickups.remove(playerId);
        openingPlayers.remove(playerId);
        attemptedOpen.remove(playerId);
        sessions.remove(playerId);
        disabledPlayers.remove(playerId);
    }

    private boolean ensureUiOpen(Player player) {
        if (!active || player == null || !player.isOnline() || disabledPlayers.contains(player.getUniqueId()) || !bridge.isAvailable()) {
            if (configuration.debug()) {
                logger.info("[PickupDiag] ensureUiOpen skip player=" + (player != null ? player.getName() : "null")
                    + " online=" + (player != null && player.isOnline())
                    + " bridgeAvailable=" + (bridge != null && bridge.isAvailable()));
            }
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (openedPlayers.contains(playerId) || openingPlayers.contains(playerId)) {
            return true;
        }

        Object session = sessions.computeIfAbsent(playerId, ignored -> new Object());
        openingPlayers.add(playerId);
        boolean opened = bridge.openUiWithCallback(player, uiId, () -> {
            if (sessions.get(playerId) == session) handleUiOpened(player);
        });
        if (configuration.debug()) {
            logger.info("[PickupDiag] ensureUiOpen openUi player=" + player.getName()
                + " uiId=" + uiId + " returned=" + opened);
        }
        if (opened) {
            logOpenMode(player, "open");
            // Give a newly initialized client the usual fast retry. With no ACK, avoid
            // reopening the same HUD in a burst on every subsequent pickup wave.
            long retry = attemptedOpen.add(playerId) ? OPEN_CONFIRM_DELAY_TICKS
                : UNCONFIRMED_OPEN_RETRY_TICKS + Math.floorMod(playerId.hashCode(), 20);
            scheduleForSession(player, () -> openingPlayers.remove(playerId), retry);
            return true;
        }
        openingPlayers.remove(playerId);
        attemptedOpen.remove(playerId);
        return false;
    }

    private void openForOnlinePlayers() {
        if (!active || !bridge.isAvailable()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureUiOpen(player);
        }
    }

    private void handleUiOpened(Player player) {
        if (!active || player == null || !player.isOnline() || disabledPlayers.contains(player.getUniqueId()) || !bridge.isAvailable()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        openingPlayers.remove(playerId);
        openedPlayers.add(playerId);
        flushPendingPickups(player);
    }

    private Map<String, Object> enqueuePickup(UUID playerId, Map<String, Object> payload) {
        Map<String, Object> queuedPayload = new LinkedHashMap<>(payload);
        Deque<Map<String, Object>> queue = pendingPickups.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        if (queue.size() >= MAX_PENDING_PICKUPS) {
            queue.pollFirst();
            long now = System.currentTimeMillis();
            if (now - lastOverflowWarnAt > OVERFLOW_WARN_INTERVAL_MS) {
                lastOverflowWarnAt = now;
                logger.warning("[Pickup] 玩家 " + playerId + " 待推送拾取队列超过 " + MAX_PENDING_PICKUPS + "，丢弃最旧条目（可能客户端未打开界面或掉线残留）");
            }
        }
        queue.addLast(queuedPayload);
        return queuedPayload;
    }

    private void removeQueuedPayload(UUID playerId, Map<String, Object> payload) {
        Deque<Map<String, Object>> queue = pendingPickups.get(playerId);
        if (queue == null) {
            return;
        }
        queue.remove(payload);
        if (queue.isEmpty()) {
            pendingPickups.remove(playerId);
        }
    }

    private void flushPendingPickups(Player player) {
        Deque<Map<String, Object>> queue = pendingPickups.remove(player.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        long delay = OPENED_PACKET_DELAY_TICKS;
        while (!queue.isEmpty()) {
            scheduleSend(player, queue.removeFirst(), delay++);
        }
    }

    private void scheduleSend(Player player, Map<String, Object> payload, long delayTicks) {
        scheduleForSession(player, () -> {
            if (!active || !player.isOnline() || !bridge.isAvailable()) {
                return;
            }
            boolean success = bridge.sendPacket(player, uiId, "pick", payload);
            logPickupPacket(player, payload, success);
        }, Math.max(0L, delayTicks));
    }

    private void scheduleForSession(Player player, Runnable action, long delayTicks) {
        UUID id = player.getUniqueId();
        Object session = sessions.computeIfAbsent(id, ignored -> new Object());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && player.isOnline() && !disabledPlayers.contains(id) && sessions.get(id) == session) action.run();
        }, delayTicks);
    }

    private void logOpenMode(Player player, String mode) {
        if (configuration.debug()) {
            this.logger.info("ArcartXPickup 打开 HUD -> player=" + player.getName() + " | mode=" + mode);
        }
    }

    private void logPickupPacket(Player player, Map<String, Object> payload, boolean success) {
        if (configuration.debug()) {
            this.logger.info(
                "ArcartXPickup 发包 -> player="
                    + player.getName()
                    + " | success="
                    + success
                    + " | payload="
                    + payload
            );
        }
    }

}


