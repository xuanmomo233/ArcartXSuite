package xuanmo.arcartxsuite.essentials.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import java.util.logging.Logger;

/**
 * 一键砍树服务 (TreeCapitator)。
 */
public final class TreeCapitatorService implements Listener {

    private static final BlockFace[] NEIGHBORS = {
        BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST,
        // 上层对角
    };

    private final JavaPlugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private final String permission;
    private final int maxBlocks;
    private final boolean requireAxe;
    private final boolean requireSneak;
    private final boolean consumeDurability;
    private final Set<Material> logTypes;
    private final boolean breakLeaves;
    private final int leafRadius;
    private final String treeFelledMsg;

    public TreeCapitatorService(JavaPlugin plugin,
        Logger logger, ConfigurationSection section, String treeFelledMsg) {
        this.plugin = plugin;
        this.logger = logger;
        this.treeFelledMsg = treeFelledMsg;

        this.enabled = section.getBoolean("enabled", true);
        this.permission = section.getString("permission", "axs.essentials.treecap");
        this.maxBlocks = section.getInt("max-blocks", 128);
        this.requireAxe = section.getBoolean("require-axe", true);
        this.requireSneak = section.getBoolean("require-sneak", false);
        this.consumeDurability = section.getBoolean("consume-durability", true);
        this.breakLeaves = section.getBoolean("break-leaves", true);
        this.leafRadius = section.getInt("leaf-radius", 4);

        List<String> types = section.getStringList("log-types");
        if (types.isEmpty()) {
            this.logTypes = EnumSet.noneOf(Material.class);
        } else {
            this.logTypes = EnumSet.noneOf(Material.class);
            for (String t : types) {
                Material m = Material.matchMaterial(t);
                if (m != null) logTypes.add(m);
            }
        }
    }

    public void start() {
        if (!enabled) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();

        // 权限检查
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) return;

        // 潜行检查
        if (requireSneak && !player.isSneaking()) return;

        // 斧头检查
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (requireAxe && !isAxe(tool.getType())) return;

        Block block = event.getBlock();
        if (!isLog(block.getType())) return;

        // 执行连锁砍伐
        Set<Block> toBreak = findConnectedLogs(block);
        if (toBreak.size() <= 1) return; // 单个方块不触发

        int broken = 0;
        for (Block log : toBreak) {
            if (broken >= maxBlocks) break;
            log.breakNaturally(tool);
            broken++;
        }

        // 破坏树叶
        if (breakLeaves) {
            Set<Block> leaves = findNearbyLeaves(toBreak);
            for (Block leaf : leaves) {
                leaf.breakNaturally();
            }
        }

        // 消耗耐久
        if (consumeDurability && tool.getItemMeta() instanceof Damageable dmg) {
            int newDamage = dmg.getDamage() + broken;
            if (newDamage >= tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
            } else {
                dmg.setDamage(newDamage);
                tool.setItemMeta(dmg);
            }
        }

        // 提示
        if (treeFelledMsg != null && !treeFelledMsg.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                treeFelledMsg.replace("{count}", String.valueOf(broken))));
        }
    }

    private Set<Block> findConnectedLogs(Block start) {
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block current = queue.poll();
            // 搜索相邻方块 (26 方向)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) { // 只向上搜索
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (!visited.contains(neighbor) && isLog(neighbor.getType())) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return visited;
    }

    private Set<Block> findNearbyLeaves(Set<Block> logs) {
        Set<Block> leaves = new HashSet<>();
        for (Block log : logs) {
            for (int dx = -leafRadius; dx <= leafRadius; dx++) {
                for (int dy = -leafRadius; dy <= leafRadius; dy++) {
                    for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                        Block b = log.getRelative(dx, dy, dz);
                        if (isLeaf(b.getType()) && !leaves.contains(b)) {
                            leaves.add(b);
                        }
                    }
                }
            }
        }
        return leaves;
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

