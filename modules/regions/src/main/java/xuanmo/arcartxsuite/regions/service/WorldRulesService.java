package xuanmo.arcartxsuite.regions.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
 * <p>
 * 支持运行时 CRUD（add/remove），修改后通过 {@link #persist()} 持久化回主配置文件。
 */
public final class WorldRulesService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final YamlConfiguration rootYaml;
    private final String sectionPath;
    private final Set<String> noFlyWorlds;
    private final String noFlyAction;
    private final Set<String> noPistonWorlds;
    private final Map<String, Set<Material>> disabledInteractions;
    private final String flyDeniedMessage;
    private final Consumer<WorldRulesService> persistCallback;

    public WorldRulesService(JavaPlugin plugin,
        Logger logger, ConfigurationSection section) {
        this(plugin, logger, section, null, null);
    }

    /**
     * 完整构造方法，支持持久化。
     *
     * @param rootYaml 主配置文件（用于持久化），可为 null（仅内存模式）
     * @param sectionPath world-rules 节在 rootYaml 中的路径，可为 null
     * @param persistCallback 持久化回调，可为 null
     */
    public WorldRulesService(JavaPlugin plugin,
        Logger logger, ConfigurationSection section,
        YamlConfiguration rootYaml, Consumer<WorldRulesService> persistCallback) {
        this.plugin = plugin;
        this.logger = logger;
        this.rootYaml = rootYaml;
        this.sectionPath = "world-rules";
        this.persistCallback = persistCallback;
        this.flyDeniedMessage = section.getString("no-fly-message", "&c此世界禁止飞行。");

        this.noFlyWorlds = ConcurrentHashMap.newKeySet();
        this.noFlyWorlds.addAll(section.getStringList("no-fly-worlds"));
        this.noFlyAction = section.getString("no-fly-action", "cancel");
        this.noPistonWorlds = ConcurrentHashMap.newKeySet();
        this.noPistonWorlds.addAll(section.getStringList("no-piston-worlds"));
        this.disabledInteractions = new ConcurrentHashMap<>();

        ConfigurationSection interactions = section.getConfigurationSection("disabled-interactions");
        if (interactions != null) {
            for (String world : interactions.getKeys(false)) {
                Set<Material> materials = ConcurrentHashMap.newKeySet();
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

    // ─── 运行时 CRUD（供 UI 调用）───

    public Set<String> noFlyWorlds() { return noFlyWorlds; }
    public Set<String> noPistonWorlds() { return noPistonWorlds; }
    public Map<String, Set<Material>> disabledInteractions() { return disabledInteractions; }
    public String noFlyAction() { return noFlyAction; }
    public String flyDeniedMessage() { return flyDeniedMessage; }

    public boolean toggleNoFlyWorld(String world) {
        boolean added;
        if (!noFlyWorlds.add(world)) { noFlyWorlds.remove(world); added = false; }
        else added = true;
        persist();
        return added;
    }

    public boolean toggleNoPistonWorld(String world) {
        boolean added;
        if (!noPistonWorlds.add(world)) { noPistonWorlds.remove(world); added = false; }
        else added = true;
        persist();
        return added;
    }

    /**
     * 切换某世界某材料的交互禁用。
     *
     * @return true 表示新增禁用，false 表示移除禁用
     */
    public boolean toggleDisabledInteraction(String world, Material material) {
        Set<Material> set = disabledInteractions.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet());
        boolean added;
        if (!set.add(material)) { set.remove(material); added = false; }
        else added = true;
        if (set.isEmpty()) disabledInteractions.remove(world);
        persist();
        return added;
    }

    /**
     * 将当前内存状态写回主配置文件。
     */
    public void persist() {
        if (rootYaml == null || persistCallback == null) return;
        rootYaml.set(sectionPath + ".no-fly-worlds", List.copyOf(noFlyWorlds));
        rootYaml.set(sectionPath + ".no-piston-worlds", List.copyOf(noPistonWorlds));
        // disabled-interactions: world -> [materialName...]
        Map<String, List<String>> interactions = new LinkedHashMap<>();
        for (var e : disabledInteractions.entrySet()) {
            List<String> mats = e.getValue().stream().map(Material::name).toList();
            interactions.put(e.getKey(), mats);
        }
        rootYaml.set(sectionPath + ".disabled-interactions", interactions);
        persistCallback.accept(this);
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

