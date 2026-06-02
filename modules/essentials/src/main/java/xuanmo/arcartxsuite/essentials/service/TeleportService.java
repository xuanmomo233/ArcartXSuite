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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

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

    public TeleportService(JavaPlugin plugin, EssentialsConfiguration config,
                           EssentialsRepository repository, PlayerManagementService playerService, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.playerService = playerService;
        this.logger = logger;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (int taskId : warmups.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        warmups.clear();
        pendingTpa.clear();
    }

    // ─── Home ───

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

    public List<String> getHomeNames(Player player) {
        try {
            return List.copyOf(repository.getHomes(player.getUniqueId()).keySet());
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Warp ───

    public void setWarp(Player player, String name) {
        try {
            repository.setWarp(name, player.getLocation());
            player.sendMessage(prefix() + config.messages().warpSet().replace("{name}", name));
        } catch (SQLException e) {
            logError("setWarp", e);
        }
    }

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

    public List<String> getWarpNames() {
        try {
            return repository.getWarpNames();
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ─── Spawn ───

    public void setSpawn(Player player) {
        try {
            repository.setSpawn(player.getLocation());
            player.sendMessage(prefix() + config.messages().spawnSet());
        } catch (SQLException e) {
            logError("setSpawn", e);
        }
    }

    public void teleportSpawn(Player player) {
        try {
            Location loc = repository.getSpawn();
            if (loc == null) {
                loc = player.getWorld().getSpawnLocation();
            }
            delayedTeleport(player, loc, config.messages().spawnTeleported());
        } catch (SQLException e) {
            logError("teleportSpawn", e);
        }
    }

    // ─── TPA ───

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

    public void teleportTop(Player player) {
        Location loc = player.getLocation();
        int highestY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        Location top = new Location(loc.getWorld(), loc.getX(), highestY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
        delayedTeleport(player, top, ChatColor.GREEN + "已传送到最高方块。");
    }

    // ─── /back ───

    public void back(Player player) {
        Location loc = playerService.popBackLocation(player);
        if (loc == null) {
            player.sendMessage(prefix() + config.messages().backNone());
            return;
        }
        delayedTeleport(player, loc, config.messages().backTeleported());
    }

    // ─── Teleport with delay ───

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

    public record TpaRequest(UUID sender, boolean here, long timestamp) {}
}
