package xuanmo.arcartxsuite.pickup.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration.ScannerConfig;
import xuanmo.arcartxsuite.pickup.filter.LootFilterEngine;
import java.util.logging.Logger;

/**
 * 掉落物扫描服务（扫描模式核心）。
 * <p>
 * 职责：
 * 1. 周期性扫描玩家附近的掉落物实体，通过过滤引擎筛选后发送到客户端 HUD 面板。
 * 2. 拦截 {@link EntityPickupItemEvent}，禁止玩家自动拾取。
 * 3. 用稳定清单承接快捷键和可选详细面板，以固定快照分批收取。
 */
public final class LootScannerService implements Listener {

    /** UI 打开前的初始延迟（ticks） */
    private static final long PREOPEN_DELAY_TICKS = 5L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PickupModuleConfiguration configuration;
    private final PacketGuardAPI packetGuard;
    private final PacketBridgeAPI packetBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final VanillaItemNameBridge vanillaItemNameBridge;
    private final ItemMatcherAPI itemMatcher;
    private final Supplier<WarehouseAutoDepositable> warehouseAutoDepositableSupplier;
    /** 过滤引擎：根据配置决定哪些物品应在面板中显示 */
    private final LootFilterEngine filterEngine;
    /** 目标 HUD 的运行时 UI ID */
    private final String uiId;
    /** 交互 Menu 的运行时 UI ID */
    private final String interactUiId;
    private final MessageProvider messages;

    /** 每位玩家的掉落物状态（可见列表 + 选中索引） */
    private final Map<UUID, PlayerLootState> playerStates = new HashMap<>();
    /** HUD 已成功打开的玩家集合（常驻 HUD，进服/reload 后开启） */
    private final Set<UUID> hudOpenPlayers = new HashSet<>();
    /** 当前 Menu 已打开的玩家集合 */
    private final Set<UUID> menuOpenPlayers = new HashSet<>();
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();
    private final Map<UUID, String> sessionTokens = new HashMap<>();
    /** 手动关闭拾取功能的玩家集合 */
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Set<UUID> manualPickups = new HashSet<>();

    private final Map<UUID, ItemStack> previewStacks = new HashMap<>();
    private final Map<UUID, String> previewJsons = new HashMap<>();
    private BukkitTask reopenTask;
    private BukkitTask collectionTask;
    private final Map<UUID, CollectionJob> collectionJobs = new LinkedHashMap<>();
    private int collectionCursor;
    private Supplier<xuanmo.arcartxsuite.api.capability.EventBusCapability> eventBusProvider = () -> null;

    public void setEventBusProvider(Supplier<xuanmo.arcartxsuite.api.capability.EventBusCapability> provider) {
        eventBusProvider = provider;
    }

    /** 周期扫描定时任务 */
    private BukkitTask scanTask;
    /** 服务是否处于活跃状态 */
    private boolean active;

    public LootScannerService(
        JavaPlugin plugin,
        Logger logger,
        PickupModuleConfiguration configuration,
        PacketGuardAPI packetGuard,
        PacketBridgeAPI packetBridge,
        ItemBridgeAPI itemStackBridge,
        VanillaItemNameBridge vanillaItemNameBridge,
        ItemMatcherAPI itemMatcher,
        String uiId,
        String interactUiId,
        Supplier<WarehouseAutoDepositable> warehouseAutoDepositableSupplier,
        MessageProvider messages
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.packetGuard = packetGuard;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.itemMatcher = itemMatcher;
        this.warehouseAutoDepositableSupplier = warehouseAutoDepositableSupplier == null ? () -> null : warehouseAutoDepositableSupplier;
        this.messages = messages;
        this.filterEngine = new LootFilterEngine(configuration.filter(), itemMatcher);
        this.uiId = uiId;
        this.interactUiId = interactUiId;
    }

    /** 启动服务：注册事件监听器、启动周期扫描任务、为所有在线玩家打开常驻 HUD。 */
    public void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        collectionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::collectionTick, 1L, 1L);
        ScannerConfig scannerConfig = configuration.scanner();
        scanTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::scanTick, PREOPEN_DELAY_TICKS, scannerConfig.scanIntervalTicks()
        );
        // reload 场景：为已在线玩家重新打开 HUD
        packetBridge.registerUiCloseCallback(interactUiId, this::closeInteraction);
        packetBridge.registerUiCloseCallback(uiId, player -> hudOpenPlayers.remove(player.getUniqueId()));
        reopenTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                openHudForPlayer(player);
            }
        }, 20L);
    }

    /** 关闭服务：取消定时任务、注销事件监听、关闭所有玩家的 HUD、清理状态。 */
    public void shutdown() {
        active = false;
        if (collectionTask != null) collectionTask.cancel();
        collectionJobs.clear();
        if (reopenTask != null) reopenTask.cancel();
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        HandlerList.unregisterAll(this);
        packetBridge.unregisterUiCloseCallback(interactUiId);
        packetBridge.unregisterUiCloseCallback(uiId);
        for (UUID playerId : new HashSet<>(menuOpenPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) packetBridge.closeUi(player, interactUiId);
        }
        // 关闭所有玩家的常驻 HUD
        for (UUID playerId : hudOpenPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                packetBridge.closeUi(player, uiId);
            }
        }
        playerStates.clear();
        hudOpenPlayers.clear();
        menuOpenPlayers.clear();
        disabledPlayers.clear();
        tokenGuard.clearAll();
        sessionTokens.clear();
        previewStacks.clear();
        previewJsons.clear();
    }

    /**
     * 宿主按键回调入口：处理交互键，返回 true 表示已消费。
     * <p>
     * 直接收取当前类，不打开菜单；按键路由由宿主处理。
     */
    public boolean handleInteractKeyFromHost(Player player) {
        return handleHostAction(player, "pick");
    }

    /** 常驻清单快捷键；不打开菜单或接管鼠标。 */
    public boolean handleHostAction(Player player, String action) {
        if (!usable(player)) return false;
        if (("pick".equals(action) || "collect_all".equals(action)) && collectionJobs.containsKey(player.getUniqueId())) return true;
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null || "pick".equals(action) || "collect_all".equals(action)) {
            scanForPlayer(player);
            state = playerStates.get(player.getUniqueId());
        }
        if (state == null || state.visibleItems.isEmpty()) return false;
        switch (action) {
            case "pick" -> beginCollection(player, List.of(state.visibleItems.get(state.selectedIndex)));
            case "collect_all" -> {
                if (!configuration.scanner().batchEnabled()) return false;
                beginCollection(player, state.visibleItems.stream().filter(entry ->
                    !configuration.scanner().batchMatchingOnly()
                    || itemMatcher.matches(configuration.scanner().batchMatcher(), entry.prototype)).toList());
            }
            case "previous" -> handleScroll(player, -1);
            case "next" -> handleScroll(player, 1);
            case "details" -> openDetails(player, state);
            default -> { return false; }
        }
        return true;
    }

    private boolean usable(Player player) {
        return active && player != null && player.isOnline() && isEnabled(player.getUniqueId())
            && packetBridge.isAvailable();
    }

    private void openDetails(Player player, PlayerLootState state) {
        UUID id = player.getUniqueId();
        if (menuOpenPlayers.contains(id)) {
            closeInteraction(player);
            packetBridge.closeUi(player, interactUiId);
            return;
        }
        sessionTokens.put(id, tokenGuard.issue(id, uiId));
        if (!packetBridge.openUi(player, interactUiId)) {
            tokenGuard.clear(id);
            sessionTokens.remove(id);
            return;
        }
        menuOpenPlayers.add(id);
        packetBridge.sendPacket(player, uiId, "menu_state", Map.of("open", true));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (usable(player) && menuOpenPlayers.contains(id)) sendUpdate(player, state);
        }, 5L);
    }

    private void closeInteraction(Player player) {
        UUID id = player.getUniqueId();
        menuOpenPlayers.remove(id);
        tokenGuard.clear(id);
        sessionTokens.remove(id);
        if (active && player.isOnline()) packetBridge.sendPacket(player, uiId, "menu_state", Map.of("open", false));
    }

    /**
     * 切换玩家的拾取功能开关。
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
            collectionJobs.remove(playerId);
            tokenGuard.clear(playerId);
            sessionTokens.remove(playerId);
            playerStates.remove(playerId);
            menuOpenPlayers.remove(playerId);
            // 立即关闭 loot_panel HUD 与 loot_interact Menu
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && packetBridge.isAvailable()) {
                packetBridge.closeUi(player, interactUiId);
                packetBridge.closeUi(player, uiId);
            }
            hudOpenPlayers.remove(playerId);
        } else {
            disabledPlayers.remove(playerId);
            // 立即重新打开 loot_panel HUD（若当前未打开）
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                openHudForPlayer(player);
            }
        }
        return !shouldDisable;
    }

    /** 查询玩家的拾取功能是否开启。 */
    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    public boolean isAutoPickupIntercepted(UUID playerId) {
        return active && isEnabled(playerId) && (configuration.scanner().disableAutoPickup() || manualPickups.contains(playerId));
    }

    /**
     * 客户端初始化完成回调：清除残留的 opening 状态并立即为该玩家执行一次扫描。
     * 由 {@link xuanmo.arcartxsuite.pickup.PickupModule#createInitializedHandler()} 调用。
     */
    public void onClientInitialized(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
        closeInteraction(player);
        packetBridge.closeUi(player, interactUiId);
        hudOpenPlayers.remove(player.getUniqueId());
        openHudForPlayer(player);
    }

    /**
     * 拦截玩家自动拾取事件。
     * 当 disable-auto-pickup 启用时，取消所有玩家的自动拾取行为（已关闭拾取的玩家不拦截）。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!active || manualPickups.contains(event.getEntity().getUniqueId()) || !configuration.scanner().disableAutoPickup()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (disabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    /** 玩家退出时清理其所有缓存状态。 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        collectionJobs.remove(playerId);
        playerStates.remove(playerId);
        hudOpenPlayers.remove(playerId);
        menuOpenPlayers.remove(playerId);
        tokenGuard.clear(playerId);
        sessionTokens.remove(playerId);
        disabledPlayers.remove(playerId);
    }

    /** 原版掉落物合堆时转移快照配额；不把新加入的数量扩进本次请求。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(org.bukkit.event.entity.ItemMergeEvent event) {
        if (!active || collectionJobs.isEmpty()) return;
        Item source = event.getEntity();
        Item target = event.getTarget();
        int moved = Math.max(0, Math.min(source.getItemStack().getAmount(),
            Math.min(64, target.getItemStack().getMaxStackSize()) - target.getItemStack().getAmount()));
        if (moved <= 0) return;
        for (CollectionJob job : collectionJobs.values()) {
            int available = moved;
            java.util.Deque<PickRequest> updated = new java.util.ArrayDeque<>();
            for (PickRequest request : job.requests) {
                if (!request.item.getUniqueId().equals(source.getUniqueId()) || available <= 0) {
                    updated.add(request);
                    continue;
                }
                int redirected = Math.min(available, request.amount);
                updated.add(new PickRequest(target, request.prototype, redirected));
                if (request.amount > redirected) updated.add(new PickRequest(source, request.prototype, request.amount - redirected));
                available -= redirected;
            }
            job.requests.clear();
            job.requests.addAll(updated);
        }
    }

    /**
     * 处理来自客户端的交互包。
     *
     * @param player 发包的玩家
     * @param action 动作类型：pick（拾取选中）、scroll_up（上移）、scroll_down（下移）
     * @param data   会话令牌；拾取时第二项为客户端所见条目的实体 UUID
     * @return 是否成功处理
     */
    public boolean handleClientPacket(Player player, String action, List<String> data) {
        if (!active || player == null || !player.isOnline()) {
            return false;
        }
        if (disabledPlayers.contains(player.getUniqueId())) return true;
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        // 客户端回包只来自可选详细面板；快捷键走服务端独立入口。
        // 验证通过后，实际写入由固定快照队列逐个校验和执行。
        switch (action) {
            case "pick" -> {
                if (!verifyActionToken(player, data)) return true;
                if (data.size() < 2) return true;
                try { handlePick(player, UUID.fromString(data.get(1))); }
                catch (IllegalArgumentException ignored) { return true; }
            }
            case "collect_all" -> { if (verifyActionToken(player, data)) handleHostAction(player, "collect_all"); }
            case "page_up" -> { if (verifyActionToken(player, data)) handlePage(player, -1); }
            case "page_down" -> { if (verifyActionToken(player, data)) handlePage(player, 1); }
            case "scroll_up" -> { if (verifyActionToken(player, data)) handleScroll(player, -1); }
            case "scroll_down" -> { if (verifyActionToken(player, data)) handleScroll(player, 1); }
            case "close_menu" -> {
                if (verifyActionToken(player, data)) {
                    closeInteraction(player);
                    packetBridge.closeUi(player, interactUiId);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验拾取令牌：要求交互菜单已打开且 data[0] 与当前签发 token 一致。
     * 不占用 in-flight 槽位——拾取是高频连点操作，单次 token 在面板会话内可复用。
     */
    private boolean verifyActionToken(Player player, List<String> data) {
        String supplied = data != null && !data.isEmpty() ? data.get(0) : "";
        return menuOpenPlayers.contains(player.getUniqueId()) && packetBridge.isUiOpen(player, interactUiId)
            && tokenGuard.verify(player.getUniqueId(), uiId, supplied);
    }

    /**
     * 处理拾取操作：将指定或当前选中的掉落物放入玩家背包，若背包满则保留在地面。
     *
     * @param targetId 客户端所见条目的实体 UUID；不使用会随列表变化的槽位索引
     */
    private void handlePick(Player player, UUID targetId) {
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null) return;
        state.visibleItems.stream().filter(entry -> entry.entityUuid.equals(targetId)).findFirst()
            .ifPresent(entry -> beginCollection(player, List.of(entry)));
    }

    /** 接受操作时固定实体和数量，之后新增或合入的物品不属于本次收取。 */
    private void beginCollection(Player player, List<LootEntry> entries) {
        UUID id = player.getUniqueId();
        if (collectionJobs.containsKey(id) || entries.isEmpty()) return;
        Map<UUID, Item> nearby = new HashMap<>();
        double radius = configuration.scanner().scanRadius();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Item item) nearby.put(item.getUniqueId(), item);
        }
        CollectionJob job = new CollectionJob(player);
        Set<UUID> captured = new HashSet<>();
        for (LootEntry entry : entries) {
            for (UUID entityId : entry.entityUuids) {
                Item item = nearby.get(entityId);
                if (!captured.add(entityId) || !eligible(player, item) || !entry.prototype.isSimilar(item.getItemStack())) continue;
                int amount = item.getItemStack().getAmount();
                job.requests.add(new PickRequest(item, entry.prototype, amount));
                job.requested += amount;
            }
        }
        if (job.requests.isEmpty()) return;
        collectionJobs.put(id, job);
        sendUpdate(player, playerStates.get(id));
    }

    /** 全模块共享每 tick 的处理上限与软时间预算，并轮流服务各玩家。 */
    private void collectionTick() {
        if (!active || collectionJobs.isEmpty()) return;
        long deadline = System.nanoTime() + configuration.scanner().timeBudgetMs() * 1_000_000L;
        for (int i = 0; i < configuration.scanner().entitiesPerTick() && !collectionJobs.isEmpty(); i++) {
            if (i > 0 && System.nanoTime() >= deadline) break;
            List<CollectionJob> jobs = new ArrayList<>(collectionJobs.values());
            CollectionJob job = jobs.get(Math.floorMod(collectionCursor++, jobs.size()));
            if (!usable(job.player) || !job.world.equals(job.player.getWorld())) {
                collectionJobs.remove(job.player.getUniqueId());
                continue;
            }
            PickRequest request = job.requests.pollFirst();
            if (request != null) {
                try {
                    PickResult result = pickEntity(job.player, request);
                    job.warehouse += result.warehouse;
                    job.inventory += result.inventory;
                    job.notifyWarehouse &= result.notifyWarehouse;
                } catch (RuntimeException exception) {
                    logger.warning("收取掉落物失败，未处理部分保留: " + exception.getMessage());
                }
            }
            if (job.requests.isEmpty()) finishCollection(job);
        }
    }

    private void finishCollection(CollectionJob job) {
        UUID id = job.player.getUniqueId();
        collectionJobs.remove(id);
        if (!usable(job.player)) return;
        long notCollected = Math.max(0, job.requested - job.warehouse - job.inventory);
        Map<String, Object> payload = new LinkedHashMap<>();
        String summary = messages.get("hints.collection-summary", job.warehouse, job.inventory, notCollected);
        payload.put("summary", summary == null ? "收取完成" : summary);
        payload.put("warehouse", job.warehouse);
        payload.put("inventory", job.inventory);
        payload.put("notCollected", notCollected);
        packetBridge.sendPacket(job.player, uiId, "result", payload);
        if (menuOpenPlayers.contains(id)) packetBridge.sendPacket(job.player, interactUiId, "result", payload);
        if (configuration.scanner().chatSummary()) {
            String key = job.notifyWarehouse ? "hints.collection-summary" : "hints.collection-summary-quiet";
            job.player.sendMessage(messages.get(key, job.warehouse, job.inventory, notCollected));
        }
        scanForPlayer(job.player);
        PlayerLootState state = playerStates.get(id);
        if (state != null) sendUpdate(job.player, state);
    }

    private PickResult pickEntity(Player player, PickRequest request) {
        Item item = request.item;
        if (!eligible(player, item) || !request.prototype.isSimilar(item.getItemStack())) return PickResult.EMPTY;
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, item,
            Math.max(0, item.getItemStack().getAmount() - request.amount));
        manualPickups.add(player.getUniqueId());
        try { Bukkit.getPluginManager().callEvent(event); }
        finally { manualPickups.remove(player.getUniqueId()); }
        if (!usable(player) || event.isCancelled() || !eligible(player, item)
            || !request.prototype.isSimilar(item.getItemStack())) return PickResult.EMPTY;

        ItemStack original = item.getItemStack().clone();
        int amount = Math.min(request.amount, original.getAmount());
        ItemStack requested = original.clone();
        requested.setAmount(amount);
        long stored = 0;
        boolean notify = true;
        if (configuration.scanner().warehouseAutoDeposit()) {
            WarehouseAutoDepositable warehouse = warehouseAutoDepositableSupplier.get();
            if (warehouse != null) {
                notify = warehouse.shouldNotifyAutoDeposit(player);
                var result = warehouse.depositToPersonalWarehouse(player, requested);
                stored = Math.min(amount, Math.max(0L, result.storedAmount()));
            }
        }
        // 已持久化的部分先从来源扣除，背包操作失败时不能再次存入。
        if (stored > 0) setGroundAmount(item, original, original.getAmount() - (int) stored);
        int forInventory = amount - (int) stored;
        int delivered = 0;
        if (forInventory > 0) {
            ItemStack inventoryStack = requested.clone();
            inventoryStack.setAmount(forInventory);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(inventoryStack);
            delivered = forInventory - leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            setGroundAmount(item, original, original.getAmount() - (int) stored - delivered);
        }
        publishPickup(player, requested, (int) stored + delivered);
        return new PickResult(stored, delivered, notify);
    }

    private void setGroundAmount(Item item, ItemStack prototype, int amount) {
        if (amount <= 0) item.remove();
        else {
            ItemStack remaining = prototype.clone();
            remaining.setAmount(amount);
            item.setItemStack(remaining);
        }
    }

    private void publishPickup(Player player, ItemStack stack, int amount) {
        if (amount <= 0) return;
        try {
            var bus = eventBusProvider.get();
            if (bus != null) bus.publish("axs.pickup.item_pickup", player,
                Map.of("material", stack.getType().name(), "amount", String.valueOf(amount)));
        } catch (RuntimeException exception) {
            logger.warning("拾取事件通知失败: " + exception.getMessage());
        }
    }

    private boolean eligible(Player player, Item item) {
        if (!player.getCanPickupItems() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        if (item == null || item.isDead() || !item.isValid() || item.getPickupDelay() > 0
            || item.getTicksLived() < configuration.scanner().pickupDelayTicks()
            || (item.getOwner() != null && !item.getOwner().equals(player.getUniqueId()))) return false;
        return item.getWorld().equals(player.getWorld())
            && item.getLocation().distanceSquared(player.getLocation()) <= Math.pow(configuration.scanner().scanRadius(), 2)
            && filterEngine.shouldDisplay(item.getItemStack());
    }

    /** 处理选中切换：循环移动选中索引（到顶/底时环绕）。 */
    private void handleScroll(Player player, int direction) {
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null || state.visibleItems.isEmpty()) {
            return;
        }
        int newIndex = Math.floorMod(state.selectedIndex + direction, state.visibleItems.size());
        state.selectedIndex = newIndex;
        sendUpdate(player, state);
    }

    private void handlePage(Player player, int direction) {
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null || state.visibleItems.isEmpty()) return;
        int pageCount = (state.visibleItems.size() + 15) / 16;
        int page = Math.floorMod(state.selectedIndex / 16 + direction, pageCount);
        state.selectedIndex = Math.min(page * 16 + state.selectedIndex % 16, state.visibleItems.size() - 1);
        sendUpdate(player, state);
    }

    /** 周期扫描 tick：遍历所有在线玩家，对每位玩家执行掉落物扫描。 */
    private void scanTick() {
        if (!active || !packetBridge.isAvailable()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hudOpenPlayers.contains(player.getUniqueId()) || !packetBridge.isUiOpen(player, uiId)) openHudForPlayer(player);
            scanForPlayer(player);
        }
        Set<UUID> visible = new HashSet<>();
        for (PlayerLootState state : playerStates.values()) for (LootEntry entry : state.visibleItems) visible.addAll(entry.entityUuids);
        previewStacks.keySet().retainAll(visible);
        previewJsons.keySet().retainAll(visible);
    }

    /** 为指定玩家扫描附近掉落物，过滤后对比变化并推送到客户端。 */
    private void scanForPlayer(Player player) {
        if (!active || !player.isOnline() || disabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();

        ScannerConfig scannerConfig = configuration.scanner();
        Location playerLocation = player.getLocation();
        double radius = scannerConfig.scanRadius();
        int candidateCount = 0;
        boolean limited = false;

        List<LootEntry> entries = new ArrayList<>();
        Map<ItemStack, Integer> groupIndexes = new HashMap<>();
        List<Entity> nearby = new ArrayList<>(player.getNearbyEntities(radius, radius, radius).stream()
            .filter(entity -> entity instanceof Item).toList());
        nearby.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(playerLocation)));
        for (Entity entity : nearby) {
            if (!(entity instanceof Item itemEntity) || itemEntity.isDead() || !itemEntity.isValid()) {
                continue;
            }
            if (!eligible(player, itemEntity)) continue;
            if (candidateCount >= scannerConfig.maxCandidates()) { limited = true; break; }
            candidateCount++;
            ItemStack stack = itemEntity.getItemStack();
            if (!filterEngine.shouldDisplay(stack)) {
                continue;
            }
            ItemStack prototype = prototypeFor(itemEntity, stack);
            if (scannerConfig.mergeSameItems()) {
                int match = groupIndexes.getOrDefault(prototype, -1);
                if (match >= 0) {
                    LootEntry existing = entries.get(match);
                    List<UUID> members = new ArrayList<>(existing.entityUuids);
                    members.add(itemEntity.getUniqueId());
                    entries.set(match, new LootEntry(existing.entityUuid, existing.material, existing.displayName,
                        existing.amount + stack.getAmount(), existing.itemJson, List.copyOf(members), existing.prototype));
                    continue;
                }
                groupIndexes.put(prototype, entries.size());
            }
            entries.add(new LootEntry(
                entity.getUniqueId(),
                stack.getType().name(),
                resolveDisplayName(stack),
                stack.getAmount(),
                previewJson(itemEntity, stack),
                List.of(entity.getUniqueId()), prototype
            ));
        }


        // 周围无掉落物时自动关闭交互菜单（loot_interact）
        if (entries.isEmpty() && menuOpenPlayers.contains(playerId)) {
            closeInteraction(player);
            packetBridge.closeUi(player, interactUiId);
            // 通知 loot_panel 菜单已关闭，恢复 HUD 交互
            debug("MENU_AUTO_CLOSE player=" + player.getName() + " reason=no_items");
        }

        PlayerLootState state = playerStates.computeIfAbsent(playerId, k -> new PlayerLootState());
        UUID selectedId = state.visibleItems.isEmpty() ? null : state.visibleItems.get(state.selectedIndex).entityUuid;
        List<LootEntry> ordered = new ArrayList<>();
        Map<Object, LootEntry> added = new LinkedHashMap<>();
        for (LootEntry entry : entries) added.put(scannerConfig.mergeSameItems() ? entry.prototype : entry.entityUuids.get(0), entry);
        for (LootEntry previous : state.visibleItems) {
            LootEntry current = added.remove(scannerConfig.mergeSameItems() ? previous.prototype : previous.entityUuids.get(0));
            if (current != null) {
                ordered.add(new LootEntry(previous.entityUuid, current.material, current.displayName,
                    current.amount, current.itemJson, current.entityUuids, current.prototype));
            }
        }
        ordered.addAll(added.values());
        boolean changed = entriesChanged(state.visibleItems, ordered) || state.limited != limited;
        state.visibleItems = ordered;
        state.limited = limited;
        int selected = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).entityUuid.equals(selectedId)) selected = i;
        state.selectedIndex = selected >= 0 ? selected : Math.min(state.selectedIndex, Math.max(0, ordered.size() - 1));
        if (changed) sendUpdate(player, state);
    }

    /** 向客户端发送完整的物品列表更新包。 */
    private void sendUpdate(Player player, PlayerLootState state) {
        if (!active || !packetBridge.isAvailable() || !hudOpenPlayers.contains(player.getUniqueId())) {
            return;
        }
        Map<String, Object> payload = buildPayload(player, state, configuration.scanner().displayRows());
        packetBridge.sendPacket(player, uiId, "update", payload);
        if (menuOpenPlayers.contains(player.getUniqueId())) {
            packetBridge.sendPacket(player, interactUiId, "update", buildPayload(player, state, 16));
        }
    }

    private Map<String, Object> buildPayload(Player player, PlayerLootState state, int rows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        int total = state.visibleItems.size();
        int start = total == 0 ? 0 : (state.selectedIndex / rows) * rows;
        payload.put("count", Math.min(rows, total - start));
        payload.put("totalCount", total);
        payload.put("selectedIndex", state.selectedIndex - start);
        payload.put("position", total == 0 ? 0 : state.selectedIndex + 1);
        payload.put("busy", collectionJobs.containsKey(player.getUniqueId()));
        payload.put("limited", state.limited);
        payload.put("batchEnabled", configuration.scanner().batchEnabled());
        payload.put("action_token", sessionTokens.getOrDefault(player.getUniqueId(), ""));
        payload.put("pickKey", configuration.scanner().keybindName());
        payload.put("previousKey", configuration.scanner().previousKey());
        payload.put("nextKey", configuration.scanner().nextKey());
        payload.put("allKey", configuration.scanner().collectAllKey());
        payload.put("detailsKey", configuration.scanner().detailsKey());
        if (total > 0) {
            LootEntry selected = state.visibleItems.get(state.selectedIndex);
            var meta = selected.prototype.getItemMeta();
            payload.put("description", meta != null && meta.hasLore() && meta.getLore() != null
                ? List.copyOf(meta.getLore()) : List.of("无额外说明"));
        } else payload.put("description", List.of());
        for (int i = 0; i < 16; i++) {
            String prefix = "item" + i;
            LootEntry entry = i < rows && start + i < total ? state.visibleItems.get(start + i) : null;
            payload.put(prefix + "Visible", entry != null);
            payload.put(prefix + "Amount", entry == null ? 0 : entry.amount);
            payload.put(prefix + "ItemJson", entry == null ? "{\"id\":\"minecraft:air\",\"Count\":1b}" : entry.itemJson);
            payload.put(prefix + "Uuid", entry == null ? "" : entry.entityUuid.toString());
            payload.put(prefix + "Name", entry == null ? "" : entry.displayName);
        }
        return payload;
    }

    /**
     * 为玩家打开常驻 HUD。
     * 在客户端初始化完成、插件 reload、玩家进服时调用。
     */
    private void openHudForPlayer(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
        if (disabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (hudOpenPlayers.contains(playerId) && packetBridge.isUiOpen(player, uiId)) {
            // 已打开，直接触发一次扫描推送
            scanForPlayer(player);
            return;
        }
        if (!packetBridge.isAvailable()) {
            return;
        }
        boolean opened = packetBridge.openUi(player, uiId);
        if (opened) {
            hudOpenPlayers.add(playerId);
            debug("HUD_OPEN player=" + player.getName());
            // 打开后稍延迟推送当前状态
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active && player.isOnline() && hudOpenPlayers.contains(playerId) && !disabledPlayers.contains(playerId)) {
                    scanForPlayer(player);
                    PlayerLootState state = playerStates.get(playerId);
                    if (state != null) sendUpdate(player, state);
                }
            }, 5L);
        }
    }

    /** 对比新旧列表，判断是否有变化（避免无意义的重复推送）。 */
    private boolean entriesChanged(List<LootEntry> oldList, List<LootEntry> newList) {
        if (oldList.size() != newList.size()) return true;
        for (int i = 0; i < oldList.size(); i++) {
            LootEntry a = oldList.get(i);
            LootEntry b = newList.get(i);
            if (!a.entityUuid.equals(b.entityUuid) || a.amount != b.amount || !a.itemJson.equals(b.itemJson)
                || !a.entityUuids.equals(b.entityUuids) || !a.displayName.equals(b.displayName)) {
                return true;
            }
        }
        return false;
    }

    private void debug(String message) {
        if (configuration.debug()) {
            this.logger.info("[LootScanner] " + message);
        }
    }

    private String previewJson(Item item, ItemStack stack) {
        UUID id = item.getUniqueId();
        ItemStack prototype = prototypeFor(item, stack);
        if (!previewJsons.containsKey(id)) {
            previewJsons.put(id, itemStackBridge.itemToPreviewJson(prototype).filter(json -> !json.isBlank())
                .orElse("{\"id\":\"minecraft:air\",\"Count\":1b}"));
        }
        return previewJsons.getOrDefault(id, "");
    }

    private ItemStack prototypeFor(Item item, ItemStack stack) {
        UUID id = item.getUniqueId();
        ItemStack prototype = previewStacks.get(id);
        if (prototype == null || !prototype.isSimilar(stack)) {
            prototype = stack.clone();
            prototype.setAmount(1);
            previewStacks.put(id, prototype);
            previewJsons.remove(id);
        }
        return prototype;
    }

    /** 解析物品显示名：优先取自定义名，其次通过桥接翻译原版物品中文名。 */
    private String resolveDisplayName(ItemStack itemStack) {
        if (itemStack == null) return "空气";
        return vanillaItemNameBridge.resolveDisplayName(itemStack);
    }

    /** 玩家的掉落物面板状态。 */
    private static class PlayerLootState {
        /** 当前可见的掉落物条目列表 */
        List<LootEntry> visibleItems = new ArrayList<>();
        /** 当前选中项索引（0-based） */
        int selectedIndex = 0;
        boolean limited;
    }

    private record PickRequest(Item item, ItemStack prototype, int amount) { }
    private record PickResult(long warehouse, long inventory, boolean notifyWarehouse) {
        static final PickResult EMPTY = new PickResult(0, 0, true);
    }
    private static final class CollectionJob {
        final Player player;
        final org.bukkit.World world;
        final java.util.Deque<PickRequest> requests = new java.util.ArrayDeque<>();
        long requested, warehouse, inventory;
        boolean notifyWarehouse = true;
        CollectionJob(Player player) { this.player = player; this.world = player.getWorld(); }
    }

    /**
     * 掉落物条目数据。
     *
     * @param entityUuid  掉落物实体的 UUID
     * @param material    材质名（如 DIAMOND_SWORD）
     * @param displayName 物品显示名（含颜色代码）
     * @param amount      堆叠数量
     * @param itemJson    物品完整 JSON（用于客户端 setItemIcon 渲染）
     */
    private record LootEntry(
        UUID entityUuid,
        String material,
        String displayName,
        long amount,
        String itemJson,
        List<UUID> entityUuids,
        ItemStack prototype
    ) {
    }
}


