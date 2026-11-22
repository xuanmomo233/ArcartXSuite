package xuanmo.arcartxsuite.essentials.service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * 背包操作服务 — 自动补种、自动工具切换。
 */
public final class InventoryActionsService implements Listener {

    private final JavaPlugin plugin;
    private boolean running;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback callbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(()->{});
    private final Logger logger;

    // 补种
    private final boolean replantEnabled;
    private final String replantPermission;
    private final Set<Material> replantCrops;

    // 自动工具
    private final boolean autoToolEnabled;
    private final String autoToolPermission;
    private final boolean switchOnBreak;

    // 玩家开关状态
    private final Set<UUID> replantToggled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> autoToolToggled = ConcurrentHashMap.newKeySet();

    // 作物 → 种子 映射
    private static final Map<Material, Material> CROP_SEED_MAP = new HashMap<>();
    static {
        CROP_SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        CROP_SEED_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_SEED_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        CROP_SEED_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
    }

    public InventoryActionsService(JavaPlugin plugin,
        Logger logger, ConfigurationSection section) {
        this.plugin = plugin;
        this.logger = logger;

        ConfigurationSection replant = section.getConfigurationSection("auto-replant");
        this.replantEnabled = replant != null && replant.getBoolean("enabled", true);
        this.replantPermission = replant != null ? replant.getString("permission", "axs.essentials.replant") : "";
        this.replantCrops = EnumSet.noneOf(Material.class);
        if (replant != null) {
            for (String s : replant.getStringList("crops")) {
                Material m = Material.matchMaterial(s);
                if (m != null) replantCrops.add(m);
            }
        }

        ConfigurationSection autoTool = section.getConfigurationSection("auto-tool");
        this.autoToolEnabled = autoTool != null && autoTool.getBoolean("enabled", true);
        this.autoToolPermission = autoTool != null ? autoTool.getString("permission", "axs.essentials.autotool") : "";
        this.switchOnBreak = autoTool != null && autoTool.getBoolean("switch-on-break", true);
    }

    public void start() {
        if(!replantEnabled&&!autoToolEnabled)return;
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        running = false;
        callbacks.close();
        HandlerList.unregisterAll(this);
        replantToggled.clear();
        autoToolToggled.clear();
    }

    public java.util.concurrent.CompletionStage<Void> stopped(){return callbacks.stopped();}

    // ─── 开关状态 ───

    public boolean toggleReplant(UUID player) {
        if (replantToggled.contains(player)) { replantToggled.remove(player); return false; }
        else { replantToggled.add(player); return true; }
    }

    public boolean isReplantEnabled(UUID player) {
        return replantEnabled && !replantToggled.contains(player);
    }

    public boolean toggleAutoTool(UUID player) {
        if (autoToolToggled.contains(player)) { autoToolToggled.remove(player); return false; }
        else { autoToolToggled.add(player); return true; }
    }

    public boolean isAutoToolEnabled(UUID player) {
        return autoToolEnabled && !autoToolToggled.contains(player);
    }

    // ─── 自动补种 ───

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        if (!replantEnabled) return;
        Player player = event.getPlayer();
        if (!hasPermission(player, replantPermission)) return;
        if (!isReplantEnabled(player.getUniqueId())) return;

        Block block = event.getBlock();
        Material cropType = block.getType();
        if (!replantCrops.contains(cropType)) return;

        // 只在作物成熟时补种
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        Material seed = CROP_SEED_MAP.get(cropType);
        if (seed == null) return;

        // 检查背包是否有种子
        if (!player.getInventory().containsAtLeast(new ItemStack(seed), 1)) return;

        Bukkit.getScheduler().runTask(plugin, () -> callbacks.call(() -> {
            if (!running || event.isCancelled() || !player.isOnline() || !player.getWorld().equals(block.getWorld())
                || !hasPermission(player, replantPermission) || !isReplantEnabled(player.getUniqueId())
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                || !block.getType().isAir()) return null;
            replant(player, block, cropType, new ItemStack(seed));
            return null;
        }, null));
    }

    private void replant(Player player, Block block, Material cropType, ItemStack seed) {
        BlockData crop = Bukkit.createBlockData(cropType);
        if (!block.canPlace(crop) || !player.getInventory().containsAtLeast(seed, 1)) return;
        BlockState previous = block.getState();
        block.setBlockData(crop, false);
        boolean planted = false;
        try {
            BlockPlaceEvent placement = new BlockPlaceEvent(block, previous, block.getRelative(BlockFace.DOWN),
                seed.clone(), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placement);
            if (!running || placement.isCancelled() || !placement.canBuild() || !block.getBlockData().equals(crop)) return;
            planted = player.getInventory().removeItem(seed).isEmpty();
        } finally {
            if (!planted && block.getBlockData().equals(crop)) previous.update(true, false);
        }
    }

    // ─── 自动工具切换 ───

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(PlayerInteractEvent event) {
        if (!autoToolEnabled || !switchOnBreak) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (!hasPermission(player, autoToolPermission)) return;
        if (!isAutoToolEnabled(player.getUniqueId())) return;

        Material blockType = event.getClickedBlock().getType();
        Material bestTool = getBestTool(blockType);
        if (bestTool == null) return;

        PlayerInventory inv = player.getInventory();
        int currentSlot = inv.getHeldItemSlot();
        ItemStack current = inv.getItem(currentSlot);
        if (current != null && current.getType() == bestTool) return;

        // 查找最佳工具在背包中的位置
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && isSameToolCategory(item.getType(), bestTool)) {
                inv.setHeldItemSlot(i);
                return;
            }
        }
    }

    // ─── 工具映射 ───

    private Material getBestTool(Material blockType) {
        if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return Material.NETHERITE_PICKAXE;
        if (Tag.MINEABLE_AXE.isTagged(blockType)) return Material.NETHERITE_AXE;
        if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return Material.NETHERITE_SHOVEL;
        if (Tag.MINEABLE_HOE.isTagged(blockType)) return Material.NETHERITE_HOE;
        return null;
    }

    private boolean isSameToolCategory(Material held, Material best) {
        if (best == Material.NETHERITE_PICKAXE) return isPickaxe(held);
        if (best == Material.NETHERITE_AXE) return isAxe(held);
        if (best == Material.NETHERITE_SHOVEL) return isShovel(held);
        if (best == Material.NETHERITE_HOE) return isHoe(held);
        return false;
    }

    private boolean isPickaxe(Material m) {
        return m == Material.WOODEN_PICKAXE || m == Material.STONE_PICKAXE || m == Material.IRON_PICKAXE
            || m == Material.GOLDEN_PICKAXE || m == Material.DIAMOND_PICKAXE || m == Material.NETHERITE_PICKAXE;
    }

    private boolean isAxe(Material m) {
        return m == Material.WOODEN_AXE || m == Material.STONE_AXE || m == Material.IRON_AXE
            || m == Material.GOLDEN_AXE || m == Material.DIAMOND_AXE || m == Material.NETHERITE_AXE;
    }

    private boolean isShovel(Material m) {
        return m == Material.WOODEN_SHOVEL || m == Material.STONE_SHOVEL || m == Material.IRON_SHOVEL
            || m == Material.GOLDEN_SHOVEL || m == Material.DIAMOND_SHOVEL || m == Material.NETHERITE_SHOVEL;
    }

    private boolean isHoe(Material m) {
        return m == Material.WOODEN_HOE || m == Material.STONE_HOE || m == Material.IRON_HOE
            || m == Material.GOLDEN_HOE || m == Material.DIAMOND_HOE || m == Material.NETHERITE_HOE;
    }

    private boolean hasPermission(Player player, String perm) {
        return perm == null || perm.isEmpty() || player.hasPermission(perm);
    }
}

