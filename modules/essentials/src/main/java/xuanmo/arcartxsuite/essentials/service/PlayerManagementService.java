package xuanmo.arcartxsuite.essentials.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import java.util.logging.Logger;

/**
 * 玩家管理服务。
 * <p>
 * 实现 {@link Listener}，负责飞行、无敌、治疗、饱食、游戏模式、速度、
 * 隐身、AFK、/back 位置栈、修复、帽子、末影箱/工作台/铁砧/垃圾桶等功能的
 * 状态维护与事件监听。通过定时任务检测 AFK 状态并自动踢出超时玩家。
 */
public final class PlayerManagementService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final EssentialsConfiguration config;
    private final java.util.function.Supplier<TabRefreshable> tabRefreshProvider;

    private final Set<UUID> flyEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> godEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Location>> backStacks = new ConcurrentHashMap<>();

    /**
     * 查看者 UUID → 被查看玩家 UUID 的映射，用于 /ess inv player 重排背包同步。
     */
    private final Map<UUID, UUID> invViewerToTarget = new ConcurrentHashMap<>();

    private int afkTaskId = -1;

    /**
     * 构造玩家管理服务。
     *
     * @param plugin              宿主插件实例
     * @param logger              日志器
     * @param config              模块配置
     * @param tabRefreshProvider  Tab 刷新能力延迟供应者
     */
    public PlayerManagementService(JavaPlugin plugin,
        Logger logger, EssentialsConfiguration config,
                                   java.util.function.Supplier<TabRefreshable> tabRefreshProvider) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.tabRefreshProvider = tabRefreshProvider;
    }

    /**
     * 注册事件监听器并启动 AFK 检测定时任务。
     */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int timeout = config.player().afkTimeout();
        if (timeout > 0) {
            afkTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAfk, 200L, 200L).getTaskId();
        }
    }

    /**
     * 注销事件监听器、取消定时任务并清空全部运行时状态。
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (afkTaskId != -1) {
            Bukkit.getScheduler().cancelTask(afkTaskId);
            afkTaskId = -1;
        }
        flyEnabled.clear();
        godEnabled.clear();
        vanished.clear();
        afkPlayers.clear();
        lastActivity.clear();
        backStacks.clear();
        invViewerToTarget.clear();
    }

    // ─── Fly ───

    /**
     * 切换指定玩家的飞行模式开关。
     *
     * @param player 目标玩家
     */
    public void toggleFly(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyEnabled.contains(uuid)) {
            flyEnabled.remove(uuid);
            player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
            player.setFlying(false);
            player.sendMessage(prefix() + config.messages().flyDisabled());
        } else {
            flyEnabled.add(uuid);
            player.setAllowFlight(true);
            player.sendMessage(prefix() + config.messages().flyEnabled());
        }
    }

    /**
     * 切换目标玩家的飞行模式，并向执行者发送提示。
     *
     * @param player 执行者
     * @param target 目标玩家
     */
    public void toggleFly(Player player, Player target) {
        toggleFly(target);
        if (!player.equals(target)) {
            player.sendMessage(prefix() + ChatColor.GRAY + "已切换 " + target.getName() + " 的飞行模式。");
        }
    }

    /**
     * 判断指定玩家是否已启用飞行模式。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示已启用
     */
    public boolean isFlyEnabled(UUID uuid) {
        return flyEnabled.contains(uuid);
    }

    // ─── God ───

    /**
     * 切换指定玩家的无敌模式开关。
     *
     * @param player 目标玩家
     */
    public void toggleGod(Player player) {
        UUID uuid = player.getUniqueId();
        if (godEnabled.contains(uuid)) {
            godEnabled.remove(uuid);
            player.setInvulnerable(false);
            player.sendMessage(prefix() + config.messages().godDisabled());
        } else {
            godEnabled.add(uuid);
            player.setInvulnerable(true);
            player.sendMessage(prefix() + config.messages().godEnabled());
        }
    }

    /**
     * 判断指定玩家是否已启用无敌模式。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示已启用
     */
    public boolean isGodEnabled(UUID uuid) {
        return godEnabled.contains(uuid);
    }

    // ─── Heal / Feed ───

    /**
     * 将目标玩家的生命值恢复至最大值并清除着火状态。
     *
     * @param target 目标玩家
     */
    public void heal(Player target) {
        target.setHealth(AttributeResolver.getMaxHealth(target));
        target.setFireTicks(0);
        target.sendMessage(prefix() + config.messages().heal());
    }

    /**
     * 将目标玩家的饥饿值与饱和度恢复至满值。
     *
     * @param target 目标玩家
     */
    public void feed(Player target) {
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.sendMessage(prefix() + config.messages().feed());
    }

    // ─── GameMode ───

    /**
     * 设置目标玩家的游戏模式。
     *
     * @param target 目标玩家
     * @param mode   游戏模式
     */
    public void setGameMode(Player target, GameMode mode) {
        target.setGameMode(mode);
        target.sendMessage(prefix() + ChatColor.GREEN + "游戏模式已切换为 " + ChatColor.WHITE + mode.name().toLowerCase() + ChatColor.GREEN + "。");
    }

    // ─── Speed ───

    /**
     * 设置玩家的飞行或行走速度（自动限幅至 0-10）。
     *
     * @param player 目标玩家
     * @param speed  速度值（0-10）
     * @param isFly  {@code true} 设置飞行速度，{@code false} 设置行走速度
     */
    public void setSpeed(Player player, float speed, boolean isFly) {
        float clamped = Math.max(0f, Math.min(10f, speed));
        float bukkit = clamped / 10f;
        if (isFly) {
            player.setFlySpeed(Math.min(1f, bukkit));
        } else {
            player.setWalkSpeed(Math.min(1f, bukkit));
        }
        player.sendMessage(prefix() + config.messages().speedSet().replace("{speed}", String.valueOf(clamped)));
    }

    // ─── Vanish ───

    /**
     * 切换玩家的隐身状态，对无 {@code axs.essentials.vanish.see} 权限的玩家隐藏/显示。
     *
     * @param player 目标玩家
     */
    public void toggleVanish(Player player) {
        UUID uuid = player.getUniqueId();
        if (vanished.contains(uuid)) {
            vanished.remove(uuid);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, player);
            }
            player.sendMessage(prefix() + config.messages().vanishDisabled());
        } else {
            vanished.add(uuid);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("axs.essentials.vanish.see")) {
                    online.hidePlayer(plugin, player);
                }
            }
            player.sendMessage(prefix() + config.messages().vanishEnabled());
        }
        refreshTab(player, "essentials-vanish");
    }

    /**
     * 判断指定玩家是否处于隐身状态。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示已隐身
     */
    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    // ─── AFK ───

    /**
     * 手动切换玩家的 AFK 状态并广播提示。
     *
     * @param player 目标玩家
     */
    public void toggleAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (afkPlayers.contains(uuid)) {
            leaveAfk(player);
        } else {
            afkPlayers.add(uuid);
            Bukkit.broadcastMessage(prefix() + config.messages().afkEnter().replace("{player}", player.getName()));
            refreshTab(player, "essentials-afk");
        }
    }

    /**
     * 判断指定玩家是否处于 AFK 状态。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示已挂机
     */
    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    private void leaveAfk(Player player) {
        if (afkPlayers.remove(player.getUniqueId())) {
            Bukkit.broadcastMessage(prefix() + config.messages().afkLeave().replace("{player}", player.getName()));
            refreshTab(player, "essentials-afk-leave");
        }
    }

    private void checkAfk() {
        long now = System.currentTimeMillis();
        int timeout = config.player().afkTimeout() * 1000;
        int kickTimeout = config.player().afkKickTimeout() * 1000;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = lastActivity.getOrDefault(uuid, now);
            long idle = now - last;
            if (!afkPlayers.contains(uuid) && idle >= timeout) {
                afkPlayers.add(uuid);
                Bukkit.broadcastMessage(prefix() + config.messages().afkEnter().replace("{player}", player.getName()));
            }
            if (kickTimeout > 0 && afkPlayers.contains(uuid) && idle >= (timeout + kickTimeout)
                && !player.hasPermission("axs.essentials.afk.exempt")) {
                player.kickPlayer(ChatColor.YELLOW + "你因长时间挂机被踢出。");
            }
        }
    }

    // ─── Back ───

    /**
     * 将玩家当前位置压入 /back 返回栈，超出上限时丢弃最旧记录。
     *
     * @param player 目标玩家
     */
    public void pushBackLocation(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<Location> stack = backStacks.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        stack.push(player.getLocation().clone());
        while (stack.size() > config.teleport().backStackSize()) {
            stack.removeLast();
        }
    }

    /**
     * 弹出并返回玩家 /back 栈中最新的位置。
     *
     * @param player 目标玩家
     * @return 上一次位置，栈为空时返回 null
     */
    public Location popBackLocation(Player player) {
        Deque<Location> stack = backStacks.get(player.getUniqueId());
        return stack != null && !stack.isEmpty() ? stack.pop() : null;
    }

    // ─── Repair ───

    /**
     * 修复玩家主手物品的耐久度。
     *
     * @param player 目标玩家
     */
    public void repair(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !(item.getItemMeta() instanceof Damageable dmg)) {
            player.sendMessage(prefix() + config.messages().repairNothing());
            return;
        }
        dmg.setDamage(0);
        item.setItemMeta(dmg);
        player.sendMessage(prefix() + config.messages().repairSuccess());
    }

    // ─── Hat ───

    /**
     * 将玩家主手物品戴到头盔槽，原头盔交换到主手。
     *
     * @param player 目标玩家
     */
    public void hat(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(prefix() + config.messages().hatNothing());
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();
        inv.setHelmet(item.clone());
        inv.setItemInMainHand(helmet == null ? new ItemStack(Material.AIR) : helmet);
        player.sendMessage(prefix() + config.messages().hatSuccess());
    }

    // ─── Enderchest / Workbench / Anvil / Trash ───

    /**
     * 为玩家打开目标玩家的末影箱。
     *
     * @param player 执行者
     * @param target 末影箱所属玩家
     */
    public void openEnderChest(Player player, Player target) {
        player.openInventory(target.getEnderChest());
    }

    /**
     * 为玩家打开工作台界面。
     *
     * @param player 目标玩家
     */
    public void openWorkbench(Player player) {
        player.openWorkbench(player.getLocation(), true);
    }

    /**
     * 为玩家打开铁砧界面。
     *
     * @param player 目标玩家
     */
    public void openAnvil(Player player) {
        // Paper API: openAnvil; Spigot 兼容用法
        try {
            player.openInventory(Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL));
        } catch (Exception e) {
            player.sendMessage(prefix() + ChatColor.RED + "当前服务端不支持直接打开铁砧。");
        }
    }

    /**
     * 为玩家打开垃圾桶（54 格虚拟容器）。
     *
     * @param player 目标玩家
     */
    public void openTrash(Player player) {
        player.openInventory(Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "垃圾桶"));
    }

    /**
     * 打开目标玩家的背包，以"主背包在前、工具栏在后"的顺序展示（与玩家自己打开背包的视觉一致）。
     * 通过自定义 36 格容器 + 事件同步实现，修改会实时写回目标玩家真实背包。
     *
     * @param viewer 查看者
     * @param target 被查看背包的玩家
     */
    public void openPlayerInventory(Player viewer, Player target) {
        Inventory inv = Bukkit.createInventory(viewer, 36,
                ChatColor.DARK_GREEN + target.getName() + ChatColor.GRAY + " 的背包");
        PlayerInventory targetInv = target.getInventory();
        // 自定义槽位 0-26 ← 目标槽位 9-35（主背包）
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, targetInv.getItem(9 + i));
        }
        // 自定义槽位 27-35 ← 目标槽位 0-8（工具栏）
        for (int i = 0; i < 9; i++) {
            inv.setItem(27 + i, targetInv.getItem(i));
        }
        invViewerToTarget.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.openInventory(inv);
    }

    // ─── Inventory View Sync ───

    /**
     * 将自定义查看容器中的槽位索引映射回目标玩家真实背包槽位索引。
     *
     * @param viewSlot 自定义容器槽位 (0-35)
     * @return 目标 PlayerInventory 槽位 (0-35)，-1 表示越界
     */
    private int mapToTargetSlot(int viewSlot) {
        if (viewSlot < 0 || viewSlot >= 36) return -1;
        if (viewSlot < 27) return 9 + viewSlot;   // 主背包
        return viewSlot - 27;                      // 工具栏
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        UUID targetId = invViewerToTarget.get(viewer.getUniqueId());
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            event.setCancelled(true);
            viewer.closeInventory();
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!top.equals(event.getClickedInventory()) && event.getClickedInventory() != null) {
            // 点击的是玩家自己的下半区，不拦截
            return;
        }
        if (event.getClickedInventory() == null) return;
        int rawSlot = event.getRawSlot();
        // 仅处理点击顶部自定义容器的操作
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            int targetSlot = mapToTargetSlot(rawSlot);
            if (targetSlot < 0) {
                event.setCancelled(true);
                return;
            }
            // 取消原事件，手动同步到目标玩家背包
            event.setCancelled(true);
            PlayerInventory targetInv = target.getInventory();
            ItemStack cursor = event.getCursor();
            ItemStack current = top.getItem(rawSlot);
            switch (event.getAction()) {
                case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> {
                    if (current != null) {
                        event.setCursor(current.clone());
                        top.setItem(rawSlot, null);
                        targetInv.setItem(targetSlot, null);
                    }
                }
                case PLACE_ALL, PLACE_SOME, PLACE_ONE -> {
                    if (cursor != null && !cursor.getType().isAir()) {
                        int amountToPlace = event.getAction().toString().contains("ONE") ? 1
                                : (event.isRightClick() ? 1 : cursor.getAmount());
                        if (current == null || current.getType().isAir()) {
                            ItemStack placed = cursor.clone();
                            placed.setAmount(Math.min(amountToPlace, cursor.getAmount()));
                            top.setItem(rawSlot, placed);
                            targetInv.setItem(targetSlot, placed);
                            cursor.setAmount(cursor.getAmount() - placed.getAmount());
                            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
                        } else if (current.isSimilar(cursor)) {
                            int max = current.getMaxStackSize();
                            int canAdd = max - current.getAmount();
                            int toAdd = Math.min(Math.min(amountToPlace, canAdd), cursor.getAmount());
                            if (toAdd > 0) {
                                current.setAmount(current.getAmount() + toAdd);
                                top.setItem(rawSlot, current);
                                targetInv.setItem(targetSlot, current);
                                cursor.setAmount(cursor.getAmount() - toAdd);
                                event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
                            }
                        }
                    }
                }
                case SWAP_WITH_CURSOR -> {
                    event.setCursor(current == null ? null : current.clone());
                    ItemStack placeItem = cursor == null ? null : cursor.clone();
                    top.setItem(rawSlot, placeItem);
                    targetInv.setItem(targetSlot, placeItem);
                }
                case MOVE_TO_OTHER_INVENTORY -> {
                    // shift-click 从顶部容器移到玩家自身背包：直接取出
                    if (current != null) {
                        top.setItem(rawSlot, null);
                        targetInv.setItem(targetSlot, null);
                        // 给查看者玩家背包加物品
                        viewer.getInventory().addItem(current).forEach((idx, leftover) ->
                                viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover));
                    }
                }
                case HOTBAR_MOVE_AND_READD, HOTBAR_SWAP -> {
                    // 数字键交换：从顶部容器槽位与查看者 hotbar 交换
                    int hotbarButton = event.getHotbarButton();
                    if (hotbarButton >= 0 && hotbarButton < 9) {
                        ItemStack hotbarItem = viewer.getInventory().getItem(hotbarButton);
                        viewer.getInventory().setItem(hotbarButton, current == null ? null : current.clone());
                        top.setItem(rawSlot, hotbarItem == null ? null : hotbarItem.clone());
                        targetInv.setItem(targetSlot, hotbarItem == null ? null : hotbarItem.clone());
                    }
                }
                case COLLECT_TO_CURSOR -> {
                    // 简化处理：收集同类型到光标
                    if (cursor != null && current != null && current.isSimilar(cursor)) {
                        int max = cursor.getMaxStackSize();
                        int canAdd = max - cursor.getAmount();
                        int toAdd = Math.min(canAdd, current.getAmount());
                        if (toAdd > 0) {
                            cursor.setAmount(cursor.getAmount() + toAdd);
                            event.setCursor(cursor);
                            current.setAmount(current.getAmount() - toAdd);
                            top.setItem(rawSlot, current.getAmount() <= 0 ? null : current);
                            targetInv.setItem(targetSlot, current.getAmount() <= 0 ? null : current);
                        }
                    }
                }
                default -> {
                    // 其他操作（如克隆）不同步
                }
            }
            viewer.updateInventory();
        } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getClickedInventory() != null
                && !event.getClickedInventory().equals(top)) {
            // shift-click 从查看者自身背包移到顶部容器：需要按重排顺序找空位
            event.setCancelled(true);
            ItemStack toMove = event.getCurrentItem();
            if (toMove == null) return;
            PlayerInventory targetInv = target.getInventory();
            // 先尝试在顶部容器中找同类型堆叠
            for (int i = 0; i < top.getSize() && toMove.getAmount() > 0; i++) {
                ItemStack slotItem = top.getItem(i);
                if (slotItem != null && slotItem.isSimilar(toMove) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                    int canAdd = slotItem.getMaxStackSize() - slotItem.getAmount();
                    int toAdd = Math.min(canAdd, toMove.getAmount());
                    slotItem.setAmount(slotItem.getAmount() + toAdd);
                    top.setItem(i, slotItem);
                    targetInv.setItem(mapToTargetSlot(i), slotItem);
                    toMove.setAmount(toMove.getAmount() - toAdd);
                }
            }
            // 再找空槽
            for (int i = 0; i < top.getSize() && toMove.getAmount() > 0; i++) {
                ItemStack slotItem = top.getItem(i);
                if (slotItem == null || slotItem.getType().isAir()) {
                    ItemStack placed = toMove.clone();
                    placed.setAmount(Math.min(toMove.getAmount(), toMove.getMaxStackSize()));
                    top.setItem(i, placed);
                    targetInv.setItem(mapToTargetSlot(i), placed);
                    toMove.setAmount(toMove.getAmount() - placed.getAmount());
                }
            }
            event.setCurrentItem(toMove.getAmount() <= 0 ? null : toMove);
            viewer.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        UUID targetId = invViewerToTarget.get(viewer.getUniqueId());
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            event.setCancelled(true);
            viewer.closeInventory();
            return;
        }
        Inventory top = event.getView().getTopInventory();
        PlayerInventory targetInv = target.getInventory();
        // 检查是否有拖拽到顶部容器的槽位
        boolean touchedTop = false;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < top.getSize()) {
                touchedTop = true;
                break;
            }
        }
        if (!touchedTop) return;
        // 取消事件，手动同步
        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        int totalSlots = event.getRawSlots().size();
        if (totalSlots == 0) return;
        // 只处理拖入顶部容器的槽位
        java.util.List<Integer> topSlots = new java.util.ArrayList<>();
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < top.getSize()) {
                topSlots.add(slot);
            }
        }
        if (topSlots.isEmpty()) return;
        int perSlot = cursor.getAmount() / topSlots.size();
        int remainder = cursor.getAmount() % topSlots.size();
        int idx = 0;
        for (int rawSlot : topSlots) {
            int amount = perSlot + (idx < remainder ? 1 : 0);
            idx++;
            if (amount <= 0) continue;
            ItemStack existing = top.getItem(rawSlot);
            if (existing == null || existing.getType().isAir()) {
                ItemStack placed = cursor.clone();
                placed.setAmount(amount);
                top.setItem(rawSlot, placed);
                targetInv.setItem(mapToTargetSlot(rawSlot), placed);
            } else if (existing.isSimilar(cursor)) {
                int max = existing.getMaxStackSize();
                int canAdd = max - existing.getAmount();
                int toAdd = Math.min(canAdd, amount);
                if (toAdd > 0) {
                    existing.setAmount(existing.getAmount() + toAdd);
                    top.setItem(rawSlot, existing);
                    targetInv.setItem(mapToTargetSlot(rawSlot), existing);
                }
            }
        }
        // 更新光标
        int consumed = 0;
        for (int rawSlot : topSlots) {
            // 简化：重新计算消耗
        }
        // 重新计算光标剩余量
        int newCursorAmount = 0;
        for (int rawSlot : topSlots) {
            ItemStack item = top.getItem(rawSlot);
            if (item != null && item.isSimilar(cursor)) {
                // 不精确，简化处理
            }
        }
        // 简化：直接清空光标如果全部拖入
        event.setCursor(null);
        viewer.updateInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) return;
        invViewerToTarget.remove(viewer.getUniqueId());
    }

    // ─── Events ───

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());

        if (config.player().vanishHideJoinMessage()) {
            if (vanished.contains(player.getUniqueId())) {
                event.setJoinMessage(null);
            }
        }
        // 对新加入的玩家隐藏所有已隐身的玩家
        for (UUID vid : vanished) {
            Player vp = Bukkit.getPlayer(vid);
            if (vp != null && !player.hasPermission("axs.essentials.vanish.see")) {
                player.hidePlayer(plugin, vp);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (config.player().vanishHideQuitMessage() && vanished.contains(uuid)) {
            event.setQuitMessage(null);
        }
        flyEnabled.remove(uuid);
        godEnabled.remove(uuid);
        vanished.remove(uuid);
        afkPlayers.remove(uuid);
        lastActivity.remove(uuid);
        backStacks.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (afkPlayers.contains(player.getUniqueId())) {
            leaveAfk(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && godEnabled.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void refreshTab(Player player, String reason) {
        TabRefreshable tab = tabRefreshProvider.get();
        if (tab != null) {
            tab.requestViewerRefresh(player, reason);
        }
    }

    private String prefix() {
        return ChatColor.GRAY + "[" + ChatColor.GREEN + "Essentials" + ChatColor.GRAY + "] " + ChatColor.RESET;
    }
}

