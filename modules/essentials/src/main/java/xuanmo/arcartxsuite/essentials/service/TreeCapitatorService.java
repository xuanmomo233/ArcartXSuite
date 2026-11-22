package xuanmo.arcartxsuite.essentials.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

/**
 * 一键砍树服务 (TreeCapitator)。
 */
public final class TreeCapitatorService implements Listener {

    private static final int MAX_LEAF_CHECKS = 50_000;

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String permission;
    private final int maxBlocks;
    private final boolean requireAxe;
    private final boolean requireSneak;
    private final boolean consumeDurability;
    private final Set<Material> logTypes = EnumSet.noneOf(Material.class);
    private final boolean breakLeaves;
    private final int leafRadius;
    private final String treeFelledMsg;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private UUID fellingPlayer;
    private boolean suppressToolDamage;
    private boolean running;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback callbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(()->{});

    public TreeCapitatorService(JavaPlugin plugin, ConfigurationSection section, String treeFelledMsg) {
        this.plugin = plugin;
        this.treeFelledMsg = treeFelledMsg;
        this.enabled = section.getBoolean("enabled", true);
        this.permission = section.getString("permission", "axs.essentials.treecap");
        this.maxBlocks = Math.max(1, Math.min(1024, section.getInt("max-blocks", 128)));
        this.requireAxe = section.getBoolean("require-axe", true);
        this.requireSneak = section.getBoolean("require-sneak", false);
        this.consumeDurability = section.getBoolean("consume-durability", true);
        this.breakLeaves = section.getBoolean("break-leaves", true);
        this.leafRadius = Math.max(0, Math.min(8, section.getInt("leaf-radius", 4)));
        for (String type : section.getStringList("log-types")) {
            Material material = Material.matchMaterial(type);
            if (material != null) logTypes.add(material);
        }
    }

    public void start() {
        running=enabled;
        if (enabled) Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        running=false;
        callbacks.close();
        HandlerList.unregisterAll(this);
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
    }

    public java.util.concurrent.CompletionStage<Void> stopped(){return callbacks.stopped();}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if(!running)return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Block root = event.getBlock();
        if (id.equals(fellingPlayer) || pending.containsKey(id) || maxBlocks < 2
            || !isLog(root.getType()) || !canFell(player)) return;
        int slot = player.getInventory().getHeldItemSlot();
        Material tool = player.getInventory().getItemInMainHand().getType();
        pending.put(id, Bukkit.getScheduler().runTask(plugin, () -> callbacks.call(() -> {
            pending.remove(id);
            if (running && !event.isCancelled() && player.isOnline() && player.getWorld().equals(root.getWorld())
                && loaded(root) && root.getType().isAir() && sameTool(player, slot, tool) && canFell(player)) {
                fell(player, root, slot, tool);
            }
            return null;
        }, null)));
    }

    private void fell(Player player, Block root, int slot, Material tool) {
        List<Block> broken = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        broken.add(root);
        visited.add(root);
        enqueueLogs(root, visited, queue);
        fellingPlayer = player.getUniqueId();
        suppressToolDamage = !consumeDurability;
        try {
            while (running && !queue.isEmpty() && broken.size() < maxBlocks && sameTool(player, slot, tool)) {
                Block log = queue.remove();
                if (loaded(log) && isLog(log.getType()) && player.getWorld().equals(log.getWorld())
                    && player.breakBlock(log)) {
                    broken.add(log);
                    enqueueLogs(log, visited, queue);
                }
            }
            if (broken.size() <= 1) return;
            if (breakLeaves && sameTool(player, slot, tool)) {
                suppressToolDamage = true;
                breakLeaves(player, broken);
            }
            if (treeFelledMsg != null && !treeFelledMsg.isEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    treeFelledMsg.replace("{count}", String.valueOf(broken.size()))));
            }
        } finally {
            suppressToolDamage = false;
            fellingPlayer = null;
        }
    }

    private void enqueueLogs(Block source, Set<Block> visited, Deque<Block> queue) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block next = source.getRelative(dx, dy, dz);
                    if (loaded(next) && visited.add(next) && isLog(next.getType())) queue.add(next);
                }
            }
        }
    }

    private void breakLeaves(Player player, List<Block> logs) {
        Set<Block> visited = new HashSet<>();
        int checked = 0;
        int attempts = 0;
        for (Block log : logs) {
            for (int dx = -leafRadius; dx <= leafRadius; dx++) {
                for (int dy = -leafRadius; dy <= leafRadius; dy++) {
                    for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                        if (!running || ++checked > MAX_LEAF_CHECKS || attempts >= maxBlocks
                            || !player.isOnline() || !player.getWorld().equals(log.getWorld())) return;
                        Block leaf = log.getRelative(dx, dy, dz);
                        if (loaded(leaf) && visited.add(leaf) && isLeaf(leaf.getType())
                            && leaf.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) {
                            attempts++;
                            player.breakBlock(leaf);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent event) {
        if (suppressToolDamage && event.getPlayer().getUniqueId().equals(fellingPlayer)) event.setCancelled(true);
    }

    private boolean canFell(Player player) {
        return running && (permission == null || permission.isEmpty() || player.hasPermission(permission))
            && (!requireSneak || player.isSneaking())
            && (!requireAxe || isAxe(player.getInventory().getItemInMainHand().getType()));
    }

    private static boolean sameTool(Player player, int slot, Material material) {
        return player.isOnline() && player.getInventory().getHeldItemSlot() == slot
            && player.getInventory().getItemInMainHand().getType() == material;
    }

    private static boolean loaded(Block block) {
        return block.getY() >= block.getWorld().getMinHeight() && block.getY() < block.getWorld().getMaxHeight()
            && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private boolean isLog(Material material) {
        if (!logTypes.isEmpty()) return logTypes.contains(material);
        return Tag.LOGS.isTagged(material);
    }

    private boolean isLeaf(Material material) {
        return Tag.LEAVES.isTagged(material);
    }

    private boolean isAxe(Material material) {
        return material == Material.WOODEN_AXE || material == Material.STONE_AXE
            || material == Material.IRON_AXE || material == Material.GOLDEN_AXE
            || material == Material.DIAMOND_AXE || material == Material.NETHERITE_AXE;
    }
}

