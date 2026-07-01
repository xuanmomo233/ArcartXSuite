package xuanmo.arcartxsuite.regions.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * 世界规则服务 — 按世界限制飞行、活塞、交互。
 */
public final class WorldRulesService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Set<String> noFlyWorlds;
    private final String noFlyAction;
    private final Set<String> noPistonWorlds;
    private final Map<String, Set<Material>> disabledInteractions;
    private final String flyDeniedMessage;

    public WorldRulesService(JavaPlugin plugin,
        Logger logger, ConfigurationSection section) {
        this.plugin = plugin;
        this.logger = logger;
        this.flyDeniedMessage = section.getString("no-fly-message", "&c此世界禁止飞行。");

        this.noFlyWorlds = new HashSet<>(section.getStringList("no-fly-worlds"));
        this.noFlyAction = section.getString("no-fly-action", "cancel");
        this.noPistonWorlds = new HashSet<>(section.getStringList("no-piston-worlds"));
        this.disabledInteractions = new ConcurrentHashMap<>();

        ConfigurationSection interactions = section.getConfigurationSection("disabled-interactions");
        if (interactions != null) {
            for (String world : interactions.getKeys(false)) {
                Set<Material> materials = new HashSet<>();
                for (String mat : interactions.getStringList(world)) {
                    Material m = Material.matchMaterial(mat);
                    if (m != null) materials.add(m);
                }
                if (!materials.isEmpty()) disabledInteractions.put(world, materials);
            }
        }
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }

    public boolean isNoFlyWorld(String worldName) {
        return noFlyWorlds.contains(worldName);
    }

    // ─── 飞行限制 ───

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("axs.essentials.fly.bypass")) return;
        if (noFlyWorlds.contains(player.getWorld().getName())) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', flyDeniedMessage));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("axs.essentials.fly.bypass")) return;
        if (noFlyWorlds.contains(player.getWorld().getName())) {
            if (player.isFlying() || player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', flyDeniedMessage));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("axs.essentials.fly.bypass")) return;
        if (noFlyWorlds.contains(player.getWorld().getName())) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // ─── 活塞限制 ───

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (noPistonWorlds.contains(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (noPistonWorlds.contains(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    // ─── 交互限制 ───

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("axs.essentials.interact.bypass")) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Set<Material> blocked = disabledInteractions.get(player.getWorld().getName());
        if (blocked != null && blocked.contains(block.getType())) {
            event.setCancelled(true);
        }
    }
}

