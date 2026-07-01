package xuanmo.arcartxsuite.pickup.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
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
 * 3. 处理客户端交互包（pick / scroll_up / scroll_down），执行拾取或切换选中。
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
    private final Supplier<WarehouseAutoDepositable> warehouseAutoDepositableSupplier;
    /** 过滤引擎：根据配置决定哪些物品应在面板中显示 */
    private final LootFilterEngine filterEngine;
    /** 目标 HUD 的运行时 UI ID */
    private final String uiId;
    /** 交互 Menu 的运行时 UI ID */
    private final String interactUiId;

    /** 每位玩家的掉落物状态（可见列表 + 选中索引） */
    private final Map<UUID, PlayerLootState> playerStates = new HashMap<>();
    /** HUD 已成功打开的玩家集合（常驻 HUD，进服/reload 后开启） */
    private final Set<UUID> hudOpenPlayers = new HashSet<>();
    /** 当前 Menu 已打开的玩家集合 */
    private final Set<UUID> menuOpenPlayers = new HashSet<>();
    /** 手动关闭拾取功能的玩家集合 */
    private final Set<UUID> disabledPlayers = new HashSet<>();

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
        String uiId,
        String interactUiId,
        Supplier<WarehouseAutoDepositable> warehouseAutoDepositableSupplier
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.packetGuard = packetGuard;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.warehouseAutoDepositableSupplier = warehouseAutoDepositableSupplier == null ? () -> null : warehouseAutoDepositableSupplier;
        this.filterEngine = new LootFilterEngine(configuration.filter());
        this.uiId = uiId;
        this.interactUiId = interactUiId;
    }

    /** 启动服务：注册事件监听器、启动周期扫描任务、为所有在线玩家打开常驻 HUD。 */
    public void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ScannerConfig scannerConfig = configuration.scanner();
        scanTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::scanTick, PREOPEN_DELAY_TICKS, scannerConfig.scanIntervalTicks()
        );
        // reload 场景：为已在线玩家重新打开 HUD
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                openHudForPlayer(player);
            }
        }, 20L);
    }

    /** 关闭服务：取消定时任务、注销事件监听、关闭所有玩家的 HUD、清理状态。 */
    public void shutdown() {
        active = false;
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        HandlerList.unregisterAll(this);
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
    }

    /**
     * 宿主按键回调入口：处理交互键（F），返回 true 表示已消费。
     * <p>
     * loot_interact 的打开/拾取由客户端 loot_panel.yml 的 keyPress 直接处理（Screen.open / Packet.send）。
     * 本方法仅用于事件消费判定：当玩家附近有可拾取物品时消费事件，阻止后续 handler（如 Conversation）处理。
     */
    public boolean handleInteractKeyFromHost(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return false;
        }
        if (disabledPlayers.contains(player.getUniqueId())) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        PlayerLootState lootState = playerStates.get(playerId);

        // 有物品 → 消费（客户端自行处理菜单打开/拾取）
        if (lootState != null && !lootState.visibleItems.isEmpty()) {
            return true;
        }
        return false;
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
            playerStates.remove(playerId);
            menuOpenPlayers.remove(playerId);
        } else {
            disabledPlayers.remove(playerId);
        }
        return !shouldDisable;
    }

    /** 查询玩家的拾取功能是否开启。 */
    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    /**
     * 客户端初始化完成回调：清除残留的 opening 状态并立即为该玩家执行一次扫描。
     * 由 {@link xuanmo.arcartxsuite.pickup.PickupModule#createInitializedHandler()} 调用。
     */
    public void onClientInitialized(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
        openHudForPlayer(player);
    }

    /**
     * 拦截玩家自动拾取事件。
     * 当 disable-auto-pickup 启用时，取消所有玩家的自动拾取行为（已关闭拾取的玩家不拦截）。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!active || !configuration.scanner().disableAutoPickup()) {
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
        playerStates.remove(playerId);
        hudOpenPlayers.remove(playerId);
        menuOpenPlayers.remove(playerId);
    }

    /**
     * 处理来自客户端的交互包。
     *
     * @param player 发包的玩家
     * @param action 动作类型：pick（拾取选中）、scroll_up（上移）、scroll_down（下移）
     * @param data   附加数据（目前未使用）
     * @return 是否成功处理
     */
    public boolean handleClientPacket(Player player, String action, List<String> data) {
        if (!active || player == null || !player.isOnline()) {
            return false;
        }
        String guardAction = guardAction(action);
        if (packetGuard != null && !packetGuard.allow(player, "pickup", guardAction, configuration.debug())) {
            return true;
        }
        switch (action) {
            case "pick" -> handlePick(player, -1);
            case "open_menu" -> menuOpenPlayers.add(player.getUniqueId());
            case "scroll_up" -> handleScroll(player, -1);
            case "scroll_down" -> handleScroll(player, 1);
            case "close_menu" -> menuOpenPlayers.remove(player.getUniqueId());
            default -> {
                // pick_0 ~ pick_7：点击指定条目拾取
                if (action.startsWith("pick_")) {
                    try {
                        int index = Integer.parseInt(action.substring(5));
                        handlePick(player, index);
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private static String guardAction(String action) {
        if (action == null || action.isBlank()) {
            return "pick";
        }
        if (action.startsWith("pick_")) {
            return "pick";
        }
        return action;
    }

    /**
     * 处理拾取操作：将指定或当前选中的掉落物放入玩家背包，若背包满则保留在地面。
     *
     * @param targetIndex 指定拾取索引，-1 表示使用当前 selectedIndex
     */
    private void handlePick(Player player, int targetIndex) {
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null || state.visibleItems.isEmpty()) {
            return;
        }
        int selectedIndex = targetIndex >= 0 ? targetIndex : state.selectedIndex;
        if (selectedIndex < 0 || selectedIndex >= state.visibleItems.size()) {
            return;
        }

        LootEntry entry = state.visibleItems.get(selectedIndex);
        Item itemEntity = findItemEntity(player, entry.entityUuid);
        if (itemEntity == null || itemEntity.isDead() || !itemEntity.isValid()) {
            state.visibleItems.remove(selectedIndex);
            if (state.selectedIndex >= state.visibleItems.size()) {
                state.selectedIndex = Math.max(0, state.visibleItems.size() - 1);
            }
            sendUpdate(player, state);
            return;
        }

        ItemStack stack = itemEntity.getItemStack();
        long storedInWarehouse = 0L;
        int remainingForInventory = stack.getAmount();
        if (configuration.scanner().warehouseAutoDeposit()) {
            WarehouseAutoDepositable warehouseAutoDepositable = warehouseAutoDepositableSupplier.get();
            if (warehouseAutoDepositable != null) {
                WarehouseAutoDepositable.DepositResult depositResult =
                    warehouseAutoDepositable.depositToPersonalWarehouse(player, stack);
                storedInWarehouse = Math.max(0L, depositResult.storedAmount());
                remainingForInventory = Math.max(0, depositResult.remainingAmount());
                debug("PICK-WAREHOUSE player=" + player.getName()
                    + " item=" + stack.getType().name()
                    + " stored=" + storedInWarehouse
                    + " remaining=" + remainingForInventory
                    + " success=" + depositResult.success());
            }
        }

        if (storedInWarehouse > 0L && remainingForInventory <= 0) {
            itemEntity.remove();
            state.visibleItems.remove(selectedIndex);
            player.sendMessage(ChatColor.GREEN + "已自动存入仓库 " + storedInWarehouse + " 件物品。");
            if (state.selectedIndex >= state.visibleItems.size()) {
                state.selectedIndex = Math.max(0, state.visibleItems.size() - 1);
            }
            sendUpdate(player, state);
            debug("PICK player=" + player.getName() + " item=" + stack.getType().name()
                + " x" + stack.getAmount() + " warehouseOnly=true");
            return;
        }

        ItemStack inventoryStack = stack.clone();
        inventoryStack.setAmount(remainingForInventory);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(inventoryStack);
        if (leftover.isEmpty()) {
            itemEntity.remove();
            state.visibleItems.remove(selectedIndex);
        } else {
            ItemStack remaining = leftover.values().iterator().next();
            itemEntity.setItemStack(remaining);
            player.sendMessage(ChatColor.RED + "背包空间不足，部分物品未能拾取。");
        }

        if (storedInWarehouse > 0L) {
            player.sendMessage(ChatColor.GREEN + "已自动存入仓库 " + storedInWarehouse + " 件物品。");
        }

        if (state.selectedIndex >= state.visibleItems.size()) {
            state.selectedIndex = Math.max(0, state.visibleItems.size() - 1);
        }
        sendUpdate(player, state);
        debug("PICK player=" + player.getName()
            + " item=" + stack.getType().name()
            + " x" + stack.getAmount()
            + " warehouseStored=" + storedInWarehouse
            + " inventoryRequested=" + remainingForInventory);
    }

    /** 处理选中切换：循环移动选中索引（到顶/底时环绕）。 */
    private void handleScroll(Player player, int direction) {
        PlayerLootState state = playerStates.get(player.getUniqueId());
        if (state == null || state.visibleItems.isEmpty()) {
            return;
        }
        int newIndex = state.selectedIndex + direction;
        if (newIndex < 0) {
            newIndex = state.visibleItems.size() - 1;
        } else if (newIndex >= state.visibleItems.size()) {
            newIndex = 0;
        }
        state.selectedIndex = newIndex;
        sendSelectionUpdate(player, state);
    }

    /** 周期扫描 tick：遍历所有在线玩家，对每位玩家执行掉落物扫描。 */
    private void scanTick() {
        if (!active || !packetBridge.isAvailable()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            scanForPlayer(player);
        }
    }

    /** 为指定玩家扫描附近掉落物，过滤后对比变化并推送到客户端。 */
    private void scanForPlayer(Player player) {
        if (!player.isOnline() || disabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();

        ScannerConfig scannerConfig = configuration.scanner();
        Location playerLocation = player.getLocation();
        double radius = scannerConfig.scanRadius();
        int maxDisplay = scannerConfig.maxDisplay();
        int pickupDelayTicks = scannerConfig.pickupDelayTicks();

        List<LootEntry> entries = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item itemEntity) || itemEntity.isDead() || !itemEntity.isValid()) {
                continue;
            }
            if (itemEntity.getPickupDelay() > pickupDelayTicks) {
                continue;
            }
            ItemStack stack = itemEntity.getItemStack();
            if (!filterEngine.shouldDisplay(stack)) {
                continue;
            }
            entries.add(new LootEntry(
                entity.getUniqueId(),
                stack.getType().name(),
                resolveDisplayName(stack),
                stack.getAmount(),
                itemStackBridge.itemToJson(stack).orElse("")
            ));
            if (entries.size() >= maxDisplay) {
                break;
            }
        }

        if (scannerConfig.mergeSameItems()) {
            entries = mergeEntries(entries);
        }

        PlayerLootState state = playerStates.computeIfAbsent(playerId, k -> new PlayerLootState());
        if (!entriesChanged(state.visibleItems, entries)) {
            return;
        }

        state.visibleItems = entries;
        if (state.selectedIndex >= entries.size()) {
            state.selectedIndex = Math.max(0, entries.size() - 1);
        }

        sendUpdate(player, state);
    }

    /** 向客户端发送完整的物品列表更新包。 */
    private void sendUpdate(Player player, PlayerLootState state) {
        if (!active || !packetBridge.isAvailable() || !hudOpenPlayers.contains(player.getUniqueId())) {
            return;
        }
        Map<String, Object> payload = buildPayload(state);
        boolean success = packetBridge.sendPacket(player, uiId, "update", payload);
        debug("UPDATE player=" + player.getName() + " items=" + state.visibleItems.size()
            + " selected=" + state.selectedIndex + " success=" + success);
    }

    /** 向客户端仅发送选中索引变更包（轻量更新）。 */
    private void sendSelectionUpdate(Player player, PlayerLootState state) {
        if (!active || !packetBridge.isAvailable() || !hudOpenPlayers.contains(player.getUniqueId())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedIndex", state.selectedIndex);
        packetBridge.sendPacket(player, uiId, "select", payload);
    }

    /** 构建发送到客户端的 payload 数据，包含所有槽位的物品信息。 */
    private Map<String, Object> buildPayload(PlayerLootState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", state.visibleItems.size());
        payload.put("selectedIndex", state.selectedIndex);

        for (int i = 0; i < configuration.scanner().maxDisplay(); i++) {
            String prefix = "item" + i;
            if (i < state.visibleItems.size()) {
                LootEntry entry = state.visibleItems.get(i);
                payload.put(prefix + "Visible", true);
                payload.put(prefix + "Amount", entry.amount);
                payload.put(prefix + "ItemJson", entry.itemJson);
                payload.put(prefix + "Uuid", entry.entityUuid.toString());
            } else {
                payload.put(prefix + "Visible", false);
                payload.put(prefix + "Amount", 0);
                payload.put(prefix + "ItemJson", "");
                payload.put(prefix + "Uuid", "");
            }
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
        if (hudOpenPlayers.contains(playerId)) {
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
                if (player.isOnline() && hudOpenPlayers.contains(playerId)) {
                    scanForPlayer(player);
                }
            }, 5L);
        }
    }

    /** 根据 UUID 在玩家附近查找对应的掉落物实体。 */
    private Item findItemEntity(Player player, UUID entityUuid) {
        double radius = configuration.scanner().scanRadius();
        for (Entity entity : player.getNearbyEntities(radius + 1, radius + 1, radius + 1)) {
            if (entity.getUniqueId().equals(entityUuid) && entity instanceof Item item) {
                return item;
            }
        }
        return null;
    }

    /** 合并同类物品：相同材质+相同名称的条目合并数量，保留第一个实体的 UUID。 */
    private List<LootEntry> mergeEntries(List<LootEntry> entries) {
        Map<String, LootEntry> merged = new LinkedHashMap<>();
        for (LootEntry entry : entries) {
            String key = entry.material + ":" + entry.displayName;
            LootEntry existing = merged.get(key);
            if (existing != null) {
                merged.put(key, new LootEntry(
                    existing.entityUuid,
                    existing.material,
                    existing.displayName,
                    existing.amount + entry.amount,
                    existing.itemJson
                ));
            } else {
                merged.put(key, entry);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 对比新旧列表，判断是否有变化（避免无意义的重复推送）。 */
    private boolean entriesChanged(List<LootEntry> oldList, List<LootEntry> newList) {
        if (oldList.size() != newList.size()) return true;
        for (int i = 0; i < oldList.size(); i++) {
            LootEntry a = oldList.get(i);
            LootEntry b = newList.get(i);
            if (!a.entityUuid.equals(b.entityUuid) || a.amount != b.amount) {
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

    /** 解析物品显示名：优先取 ItemMeta 自定义名，其次将材质名转为首字母大写格式。 */
    private String resolveDisplayName(ItemStack itemStack) {
        if (itemStack == null) return "Unknown";
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        Material material = itemStack.getType();
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1));
        }
        return builder.length() == 0 ? material.name() : builder.toString();
    }

    /** 玩家的掉落物面板状态。 */
    private static class PlayerLootState {
        /** 当前可见的掉落物条目列表 */
        List<LootEntry> visibleItems = new ArrayList<>();
        /** 当前选中项索引（0-based） */
        int selectedIndex = 0;
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
        int amount,
        String itemJson
    ) {
    }
}


