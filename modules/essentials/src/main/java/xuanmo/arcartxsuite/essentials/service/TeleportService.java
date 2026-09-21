package xuanmo.arcartxsuite.essentials.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * 传送服务。
 * <p>
 * 实现 {@link Listener}，负责家（home）、传送点（warp）、出生点（spawn）、
 * TPA 请求、/top、/back 以及带预热的延迟传送等功能。
 * 通过 {@link EssentialsRepository} 持久化位置数据，并监听玩家移动与退出事件
 * 以取消预热中的传送和清理待处理 TPA 请求。
 */
public final class TeleportService implements Listener {

    private final JavaPlugin plugin;
    private final EssentialsConfiguration config;
    private final EssentialsRepository repository;
    private final PlayerManagementService playerService;
    private final Logger logger;

    // TPA 请求: target -> sender
    private final Map<UUID, TpaRequest> pendingTpa = new ConcurrentHashMap<>();
    // 传送等待中的玩家
    private final Map<UUID, Integer> warmups = new ConcurrentHashMap<>();
    // 出生点后端（懒加载，按 MV 是否激活动态选择）
    private volatile SpawnBackend localSpawnBackend;
    private volatile SpawnBackend multiverseSpawnBackend;

    /**
     * 构造传送服务。
     *
     * @param plugin        宿主插件实例
     * @param config        模块配置
     * @param repository    数据仓储
     * @param playerService 玩家管理服务（用于 /back 位置栈）
     * @param logger        日志器
     */
    public TeleportService(JavaPlugin plugin, EssentialsConfiguration config,
                           EssentialsRepository repository, PlayerManagementService playerService, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.playerService = playerService;
        this.logger = logger;
    }

    /**
     * 注册事件监听器。
     */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 注销事件监听器，取消全部预热任务并清空待处理 TPA 请求。
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (int taskId : warmups.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        warmups.clear();
        pendingTpa.clear();
    }

    // ─── Home ───

    /**
     * 设置玩家的家，支持权限覆盖上限检查。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void setHome(Player player, String name) {
        try {
            int count = repository.homeCount(player.getUniqueId());
            int max = config.teleport().maxHomes();
            // 检查权限覆盖: axs.essentials.homes.<number>
            for (int i = 100; i > max; i--) {
                if (player.hasPermission("axs.essentials.homes." + i)) {
                    max = i;
                    break;
                }
            }
            Location existing = repository.getHome(player.getUniqueId(), name);
            if (existing == null && count >= max) {
                player.sendMessage(prefix() + config.messages().homeLimit().replace("{max}", String.valueOf(max)));
                return;
            }
            repository.setHome(player.getUniqueId(), name, player.getLocation());
            player.sendMessage(prefix() + config.messages().homeSet().replace("{name}", name));
        } catch (SQLException e) {
            logError("setHome", e);
            player.sendMessage(prefix() + ChatColor.RED + "设置家失败。");
        }
    }

    /**
     * 删除玩家的指定家。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void deleteHome(Player player, String name) {
        try {
            Location loc = repository.getHome(player.getUniqueId(), name);
            if (loc == null) {
                player.sendMessage(prefix() + config.messages().homeNotFound().replace("{name}", name));
                return;
            }
            repository.deleteHome(player.getUniqueId(), name);
            player.sendMessage(prefix() + config.messages().homeDeleted().replace("{name}", name));
        } catch (SQLException e) {
            logError("deleteHome", e);
        }
    }

    /**
     * 将玩家延迟传送到指定的家。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void teleportHome(Player player, String name) {
        try {
            Location loc = repository.getHome(player.getUniqueId(), name);
            if (loc == null) {
                player.sendMessage(prefix() + config.messages().homeNotFound().replace("{name}", name));
                return;
            }
            delayedTeleport(player, loc, config.messages().homeTeleported().replace("{name}", name));
        } catch (SQLException e) {
            logError("teleportHome", e);
        }
    }

    /**
     * 返回玩家全部家的名称列表。
     *
     * @param player 目标玩家
     * @return 家名称不可变列表，查询失败时返回空列表
     */
    public List<String> getHomeNames(Player player) {
        try {
            return List.copyOf(repository.getHomes(player.getUniqueId()).keySet());
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Warp ───

    /**
     * 以玩家当前位置设置公共传送点。
     *
     * @param player 执行者
     * @param name   传送点名称
     */
    public void setWarp(Player player, String name) {
        try {
            repository.setWarp(name, player.getLocation());
            player.sendMessage(prefix() + config.messages().warpSet().replace("{name}", name));
        } catch (SQLException e) {
            logError("setWarp", e);
        }
    }

    /**
     * 删除指定公共传送点。
     *
     * @param player 执行者
     * @param name   传送点名称
     */
    public void deleteWarp(Player player, String name) {
        try {
            Location loc = repository.getWarp(name);
            if (loc == null) {
                player.sendMessage(prefix() + config.messages().warpNotFound().replace("{name}", name));
                return;
            }
            repository.deleteWarp(name);
            player.sendMessage(prefix() + config.messages().warpDeleted().replace("{name}", name));
        } catch (SQLException e) {
            logError("deleteWarp", e);
        }
    }

    /**
     * 将玩家延迟传送到指定公共传送点。
     *
     * @param player 目标玩家
     * @param name   传送点名称
     */
    public void teleportWarp(Player player, String name) {
        try {
            Location loc = repository.getWarp(name);
            if (loc == null) {
                player.sendMessage(prefix() + config.messages().warpNotFound().replace("{name}", name));
                return;
            }
            delayedTeleport(player, loc, config.messages().warpTeleported().replace("{name}", name));
        } catch (SQLException e) {
            logError("teleportWarp", e);
        }
    }

    /**
     * 返回全部公共传送点名称列表。
     *
     * @return 传送点名称不可变列表，查询失败时返回空列表
     */
    public List<String> getWarpNames() {
        try {
            return repository.getWarpNames();
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Spawn ───

    /**
     * 以玩家当前位置设置服务器出生点。
     * <p>
     * 安装 Multiverse-Core 时写入该世界的 MV {@code spawn-location} 配置；
     * 未安装时写入模块数据库 {@code spawn} 表。
     *
     * @param player 执行者
     */
    public void setSpawn(Player player) {
        boolean success = spawnBackend().setSpawn(player.getLocation());
        player.sendMessage(prefix() + (success
            ? config.messages().spawnSet()
            : config.messages().spawnFailed()));
    }

    /**
     * 查询指定世界的出生点（经当前 {@link SpawnBackend}：MV 已加载时返回该世界的
     * MV spawn，否则返回本地存储的全局 spawn）。
     * <p>
     * 未设置时返回 {@code null}——不回退世界默认 spawn，兜底策略由调用方决定。
     * 供模块内使用及 {@code EssentialsQueryable} 能力对外暴露。
     *
     * @param world 上下文世界（MV 后端按世界取 spawn；本地后端忽略）
     * @return 出生点位置，未设置或查询失败返回 {@code null}
     */
    public @Nullable Location getSpawn(@NotNull World world) {
        return spawnBackend().getSpawn(world);
    }

    /**
     * 将玩家延迟传送到出生点，未设置时回退到世界默认出生点。
     *
     * @param player 目标玩家
     */
    public void teleportSpawn(Player player) {
        Location loc = spawnBackend().getSpawn(player.getWorld());
        if (loc == null) {
            loc = player.getWorld().getSpawnLocation();
        }
        delayedTeleport(player, loc, config.messages().spawnTeleported());
    }

    /**
     * 解析当前应使用的出生点后端。
     * <p>
     * Multiverse-Core 已加载时使用 {@link MultiverseSpawnBackend}，
     * 否则使用 {@link LocalSpawnBackend}。每次调用动态判断，
     * MV 后加载或卸载时可无缝切换。
     *
     * @return 当前生效的出生点后端
     */
    private SpawnBackend spawnBackend() {
        if (multiverseActive()) {
            SpawnBackend backend = multiverseSpawnBackend;
            if (backend == null) {
                backend = new MultiverseSpawnBackend(logger);
                multiverseSpawnBackend = backend;
            }
            return backend;
        }
        return localSpawnBackend();
    }

    /**
     * 返回本地出生点后端（懒初始化）。
     *
     * @return 本地后端实例
     */
    private SpawnBackend localSpawnBackend() {
        SpawnBackend backend = localSpawnBackend;
        if (backend == null) {
            backend = new LocalSpawnBackend(repository, logger);
            localSpawnBackend = backend;
        }
        return backend;
    }

    /**
     * Multiverse-Core 是否已安装且 API 就绪。
     * <p>
     * 必须先查插件启用再触碰 MV 类（否则无 MV 环境下类解析失败）。
     *
     * @return MV 可接管出生点时返回 {@code true}
     */
    private boolean multiverseActive() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")
                && MultiverseSpawnBackend.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    // ─── TPA ───

    /**
     * 向目标玩家发送 TPA 请求，并在超时后自动清除。
     *
     * @param sender 请求发起者
     * @param target 请求接收者
     * @param here   {@code true} 为 tpahere（请求对方过来），{@code false} 为 tpa（请求过去）
     */
    public void sendTpa(Player sender, Player target, boolean here) {
        UUID targetUuid = target.getUniqueId();
        pendingTpa.put(targetUuid, new TpaRequest(sender.getUniqueId(), here, System.currentTimeMillis()));
        sender.sendMessage(prefix() + config.messages().tpaSent().replace("{player}", target.getName()));
        target.sendMessage(prefix() + (here
            ? config.messages().tpaHereReceived().replace("{player}", sender.getName())
            : config.messages().tpaReceived().replace("{player}", sender.getName())));

        // 自动过期
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest req = pendingTpa.get(targetUuid);
            if (req != null && req.sender().equals(sender.getUniqueId())) {
                pendingTpa.remove(targetUuid);
                Player s = Bukkit.getPlayer(req.sender());
                if (s != null) s.sendMessage(prefix() + config.messages().tpaExpired());
            }
        }, config.teleport().tpaTimeout() * 20L);
    }

    /**
     * 接受当前待处理的 TPA 请求并执行延迟传送。
     *
     * @param player 请求接收者
     */
    public void acceptTpa(Player player) {
        TpaRequest req = pendingTpa.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(prefix() + config.messages().tpaNoPending());
            return;
        }
        Player sender = Bukkit.getPlayer(req.sender());
        if (sender == null || !sender.isOnline()) {
            player.sendMessage(prefix() + config.messages().playerNotFound().replace("{player}", "请求者"));
            return;
        }
        player.sendMessage(prefix() + config.messages().tpaAccepted());
        sender.sendMessage(prefix() + config.messages().tpaAccepted());
        if (req.here()) {
            delayedTeleport(player, sender.getLocation(), null);
        } else {
            delayedTeleport(sender, player.getLocation(), null);
        }
    }

    /**
     * 拒绝当前待处理的 TPA 请求并通知双方。
     *
     * @param player 请求接收者
     */
    public void denyTpa(Player player) {
        TpaRequest req = pendingTpa.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(prefix() + config.messages().tpaNoPending());
            return;
        }
        player.sendMessage(prefix() + config.messages().tpaDenied());
        Player sender = Bukkit.getPlayer(req.sender());
        if (sender != null) sender.sendMessage(prefix() + config.messages().tpaDenied());
    }

    // ─── /top ───

    /**
     * 将玩家传送到当前坐标的最高非空气方块上方。
     *
     * @param player 目标玩家
     */
    public void teleportTop(Player player) {
        Location loc = player.getLocation();
        int highestY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        Location top = new Location(loc.getWorld(), loc.getX(), highestY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
        delayedTeleport(player, top, ChatColor.GREEN + "已传送到最高方块。");
    }

    // ─── /back ───

    /**
     * 将玩家延迟传送回 /back 栈中的上一个位置。
     *
     * @param player 目标玩家
     */
    public void back(Player player) {
        Location loc = playerService.popBackLocation(player);
        if (loc == null) {
            player.sendMessage(prefix() + config.messages().backNone());
            return;
        }
        delayedTeleport(player, loc, config.messages().backTeleported());
    }

    // ─── Teleport with delay ───

    /**
     * 延迟传送玩家到指定位置，预热期间移动将取消传送。
     * <p>
     * 拥有 {@code axs.essentials.teleport.bypass-delay} 权限或延迟为 0 时即时传送。
     *
     * @param player         目标玩家
     * @param target         目标位置
     * @param successMessage 传送成功后的提示消息，为 null 或空则不发送
     */
    public void delayedTeleport(Player player, Location target, String successMessage) {
        int delay = config.teleport().teleportDelay();
        if (delay <= 0 || player.hasPermission("axs.essentials.teleport.bypass-delay")) {
            executeTeleport(player, target, successMessage);
            return;
        }

        player.sendMessage(prefix() + config.messages().teleportWarmup()
            .replace("{seconds}", String.valueOf(delay)));

        Location startLoc = player.getLocation().clone();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            warmups.remove(player.getUniqueId());
            if (player.isOnline()) {
                executeTeleport(player, target, successMessage);
            }
        }, delay * 20L).getTaskId();

        warmups.put(player.getUniqueId(), taskId);
    }

    private void executeTeleport(Player player, Location target, String message) {
        playerService.pushBackLocation(player);
        Location safe = config.teleport().safeTeleport() ? findSafeLocation(target) : target;
        player.teleport(safe);
        if (message != null && !message.isBlank()) {
            player.sendMessage(prefix() + message);
        }
    }

    private Location findSafeLocation(Location loc) {
        Block block = loc.getBlock();
        if (block.getType().isSolid()) {
            // 往上找安全位置
            for (int i = 1; i < 10; i++) {
                Block above = block.getRelative(0, i, 0);
                Block aboveHead = above.getRelative(0, 1, 0);
                if (!above.getType().isSolid() && !aboveHead.getType().isSolid()) {
                    return above.getLocation().add(0.5, 0, 0.5).setDirection(loc.getDirection());
                }
            }
        }
        return loc;
    }

    // ─── Events ───

    /**
     * 玩家死亡重生时，未安装 Multiverse-Core 且开启 {@code spawn.respawn-at-spawn}
     * 则强制重生于服务器出生点；床/重生锚按 {@code spawn.respect-personal-spawn} 豁免。
     *
     * @param event 重生事件
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (multiverseActive() || !config.spawn().respawnAtSpawn()) return;
        if (config.spawn().respectPersonalSpawn()
            && (event.isBedSpawn() || event.isAnchorSpawn())) return;
        Location spawn = localSpawnBackend().getSpawn(event.getPlayer().getWorld());
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    /**
     * 新玩家首次进服时，未安装 Multiverse-Core 且开启
     * {@code spawn.first-join-at-spawn} 则延迟 1 tick 传送到出生点。
     *
     * @param event 进服事件
     */
    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (multiverseActive() || !config.spawn().firstJoinAtSpawn()
            || player.hasPlayedBefore()) return;
        Location spawn = localSpawnBackend().getSpawn(player.getWorld());
        if (spawn == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(spawn);
            }
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.teleport().cancelOnMove()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!warmups.containsKey(uuid)) return;

        if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            int taskId = warmups.remove(uuid);
            Bukkit.getScheduler().cancelTask(taskId);
            player.sendMessage(prefix() + config.messages().teleportCancelled());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingTpa.remove(uuid);
        Integer taskId = warmups.remove(uuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    private String prefix() {
        return ChatColor.GRAY + "[" + ChatColor.GREEN + "Essentials" + ChatColor.GRAY + "] " + ChatColor.RESET;
    }

    private void logError(String method, Exception e) {
        logger.log(Level.WARNING, "Essentials." + method + " 失败", e);
    }

    /**
     * TPA 请求记录。
     *
     * @param sender    请求发起者 UUID
     * @param here      是否为 tpahere（请求对方过来）
     * @param timestamp 请求创建时间戳（毫秒）
     */
    public record TpaRequest(UUID sender, boolean here, long timestamp) {}
}
