package xuanmo.arcartxsuite.pickup.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private static final long FALLBACK_OPEN_PACKET_DELAY_TICKS = 40L;
    /** 玩家进服后延迟多少 tick 尝试打开 HUD（等待客户端 HUD 体系就绪） */
    private static final long JOIN_OPEN_DELAY_TICKS = 40L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PickupModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final ItemBridgeAPI itemStackBridge;
    private final String uiId;
    private final Set<UUID> openedPlayers = new HashSet<>();
    private final Set<UUID> openingPlayers = new HashSet<>();
    private final Map<UUID, Deque<Map<String, Object>>> pendingPickups = new HashMap<>();
    /** 手动关闭拾取通知的玩家集合 */
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private Supplier<EventBusCapability> eventBusProvider;
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
        Bukkit.getScheduler().runTaskLater(plugin, this::openForOnlinePlayers, PREOPEN_DELAY_TICKS);
        // reload 后 ArcartX 客户端重建 HUD 可能需要更长时间，安排二次确认重试
        Bukkit.getScheduler().runTaskLater(plugin, this::openForOnlinePlayers, PREOPEN_DELAY_TICKS + FALLBACK_OPEN_PACKET_DELAY_TICKS);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void shutdown() {
        active = false;
        HandlerList.unregisterAll(this);
        for (UUID playerId : openedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                bridge.closeUi(player, uiId);
            }
        }
        openedPlayers.clear();
        openingPlayers.clear();
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
        } else {
            disabledPlayers.remove(playerId);
        }
        return !shouldDisable;
    }

    /** 查询玩家的拾取通知是否开启。 */
    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    /**
     * 客户端初始化完成回调：为该玩家打开常驻通知 HUD。
     * 由 PickupModule#createInitializedHandler() 调用，
     * 覆盖「插件启动后才进服」的玩家。
     */
    public void onClientInitialized(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
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
        if (!active || player == null || !player.isOnline() || itemStack == null) {
            return false;
        }
        if (amount <= 0 || itemStack.getType() == Material.AIR) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (disabledPlayers.contains(playerId)) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemJson", itemStackBridge.itemToJson(itemStack).orElse(""));
        payload.put("amount", amount);

        if (openedPlayers.contains(playerId)) {
            scheduleSend(player, payload, OPENED_PACKET_DELAY_TICKS);
            return true;
        }

        Map<String, Object> queuedPayload = enqueuePickup(playerId, payload);
        if (!ensureUiOpen(player)) {
            removeQueuedPayload(playerId, queuedPayload);
            logPickupPacket(player, payload, false);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isOnline()) {
            return;
        }
        if (disabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemJson", itemStackBridge.itemToJson(itemStack).orElse(""));
        payload.put("amount", itemStack.getAmount());

        UUID playerId = player.getUniqueId();
        if (openedPlayers.contains(playerId)) {
            scheduleSend(player, payload, OPENED_PACKET_DELAY_TICKS);
            return;
        }

        Map<String, Object> queuedPayload = enqueuePickup(playerId, payload);
        if (!ensureUiOpen(player)) {
            removeQueuedPayload(playerId, queuedPayload);
            logPickupPacket(player, payload, false);
        }
        publishPickupEvent(player, itemStack);
    }

    private void publishPickupEvent(Player player, ItemStack itemStack) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> eventPayload = new HashMap<>();
        eventPayload.put("material", itemStack.getType().name());
        eventPayload.put("amount", String.valueOf(itemStack.getAmount()));
        eventBus.publish("axs.pickup.item_pickup", player, eventPayload);
    }

    /**
     * 玩家进服时打开常驻通知 HUD。
     * <p>
     * 延迟到客户端 HUD 体系就绪后再发包；onClientInitialized 作为更可靠的
     * 补充入口存在，二者通过 openedPlayers/openingPlayers 去重。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && player.isOnline()) {
                ensureUiOpen(player);
            }
        }, JOIN_OPEN_DELAY_TICKS);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openedPlayers.remove(playerId);
        openingPlayers.remove(playerId);
        pendingPickups.remove(playerId);
    }

    private boolean ensureUiOpen(Player player) {
        if (player == null || !player.isOnline() || !bridge.isAvailable()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (openedPlayers.contains(playerId)) {
            return true;
        }
        if (openingPlayers.contains(playerId)) {
            return true;
        }

        openingPlayers.add(playerId);
        boolean callbackOpen = bridge.openUiWithCallback(player, uiId, () -> handleUiOpened(player));
        if (callbackOpen) {
            logOpenMode(player, "callback");
            return true;
        }

        if (bridge.openUi(player, uiId)) {
            logOpenMode(player, "fallback-delay");
            Bukkit.getScheduler().runTaskLater(plugin, () -> handleUiOpened(player), FALLBACK_OPEN_PACKET_DELAY_TICKS);
            return true;
        }

        openingPlayers.remove(playerId);
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
        if (!active || player == null || !player.isOnline() || !bridge.isAvailable()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        openingPlayers.remove(playerId);
        openedPlayers.add(playerId);
        flushPendingPickups(player);
    }

    private Map<String, Object> enqueuePickup(UUID playerId, Map<String, Object> payload) {
        Map<String, Object> queuedPayload = new LinkedHashMap<>(payload);
        pendingPickups.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(queuedPayload);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !player.isOnline() || !bridge.isAvailable()) {
                return;
            }
            boolean success = bridge.sendPacket(player, uiId, "pick", payload);
            logPickupPacket(player, payload, success);
        }, Math.max(0L, delayTicks));
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


