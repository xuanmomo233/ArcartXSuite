package xuanmo.arcartxsuite.essentials.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
            afkTaskId = AxsScheduler.runTaskTimer(plugin, this::checkAfk, 200L, 200L).getTaskId();
        }
    }

    /**
     * 注销事件监听器、取消定时任务并清空全部运行时状态。
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (afkTaskId != -1) {
            AxsScheduler.cancelTask(afkTaskId);
            afkTaskId = -1;
        }
        flyEnabled.clear();
        godEnabled.clear();
        vanished.clear();
        afkPlayers.clear();
        lastActivity.clear();
        backStacks.clear();
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

