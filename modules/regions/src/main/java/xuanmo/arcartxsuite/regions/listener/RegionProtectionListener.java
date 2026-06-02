package xuanmo.arcartxsuite.regions.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager;

/**
 * 区域保护事件监听器 — 处理所有 40+ flags 的保护逻辑。
 */
public final class RegionProtectionListener implements Listener {

    private final RegionManager manager;
    private final RegionsConfiguration config;

    public RegionProtectionListener(RegionManager manager, RegionsConfiguration config) {
        this.manager = manager;
        this.config = config;
    }

    // ─── 方块破坏 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isDenied(event.getBlock().getLocation(), RegionFlag.BLOCK_BREAK, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noBuild());
        }
    }

    // ─── 方块放置 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isDenied(event.getBlock().getLocation(), RegionFlag.BLOCK_PLACE, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noBuild());
        }
    }

    // ─── 玩家交互 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (isDenied(event.getClickedBlock().getLocation(), RegionFlag.USE, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noInteract());
        }
    }

    // ─── 容器访问 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestAccess(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if (!isContainer(type)) return;
        if (isDenied(event.getClickedBlock().getLocation(), RegionFlag.CHEST_ACCESS, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noInteract());
        }
    }

    // ─── PVP ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (isDeniedAt(victim.getLocation(), RegionFlag.PVP)) {
            event.setCancelled(true);
            msg(attacker, config.messages().noPvp());
        }
    }

    // ─── 怪物伤害 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getDamager() instanceof Player) return;
        if (event.getDamager() instanceof Monster || event.getDamager() instanceof org.bukkit.entity.Projectile) {
            if (isDeniedAt(event.getEntity().getLocation(), RegionFlag.MOB_DAMAGE)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 动物伤害 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (isDenied(event.getEntity().getLocation(), RegionFlag.DAMAGE_ANIMALS, attacker)) {
            event.setCancelled(true);
        }
    }

    // ─── 摔落伤害 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (isDeniedAt(event.getEntity().getLocation(), RegionFlag.FALL_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    // ─── 爆炸 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        RegionFlag flag;
        if (event.getEntity() instanceof org.bukkit.entity.TNTPrimed) flag = RegionFlag.TNT;
        else if (event.getEntity() instanceof org.bukkit.entity.Creeper) flag = RegionFlag.CREEPER_EXPLOSION;
        else flag = RegionFlag.OTHER_EXPLOSION;

        event.blockList().removeIf(block -> isDeniedAt(block.getLocation(), flag));
    }

    // ─── 火焰蔓延 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.FIRE_SPREAD)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 方块燃烧 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // ─── 闪电 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightning(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.LIGHTNING)) {
                event.setCancelled(true);
            }
        } else if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.LAVA_FIRE)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 树叶腐烂 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    // ─── 冰/雪 形成/融化 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Material newType = event.getNewState().getType();
        if (newType == Material.ICE || newType == Material.FROSTED_ICE) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.ICE_FORM)) event.setCancelled(true);
        } else if (newType == Material.SNOW) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.SNOW_FALL)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.ICE || type == Material.FROSTED_ICE) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.ICE_MELT)) event.setCancelled(true);
        } else if (type == Material.SNOW || type == Material.SNOW_BLOCK) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.SNOW_MELT)) event.setCancelled(true);
        } else if (type == Material.FARMLAND) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.SOIL_DRY)) event.setCancelled(true);
        }
    }

    // ─── 作物/藤蔓生长 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.CROP_GROWTH)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVineGrow(BlockSpreadEvent event) {
        Material type = event.getSource().getType();
        if (type == Material.VINE || type.name().contains("CAVE_VINES")) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.VINE_GROWTH)) event.setCancelled(true);
        } else if (type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.MUSHROOM_SPREAD)) event.setCancelled(true);
        } else if (type == Material.GRASS_BLOCK) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.GRASS_SPREAD)) event.setCancelled(true);
        }
    }

    // ─── 水/岩浆流动 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.WATER) {
            if (isDeniedAt(event.getToBlock().getLocation(), RegionFlag.WATER_FLOW)) event.setCancelled(true);
        } else if (type == Material.LAVA) {
            if (isDeniedAt(event.getToBlock().getLocation(), RegionFlag.LAVA_FLOW)) event.setCancelled(true);
        }
    }

    // ─── 活塞 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isDeniedAt(block.getLocation(), RegionFlag.PISTONS)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isDeniedAt(block.getLocation(), RegionFlag.PISTONS)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ─── 怪物生成 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (event.getEntity() instanceof Monster) {
            if (isDeniedAt(event.getLocation(), RegionFlag.MOB_SPAWNING)) event.setCancelled(true);
        } else if (event.getEntity() instanceof Animals) {
            if (isDeniedAt(event.getLocation(), RegionFlag.ANIMAL_SPAWNING)) event.setCancelled(true);
        }
    }

    // ─── 末影人拿方块 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Enderman) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.ENDERMAN_GRIEF)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 物品丢弃/拾取 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isDenied(event.getPlayer().getLocation(), RegionFlag.ITEM_DROP, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (isDenied(event.getPlayer().getLocation(), RegionFlag.ITEM_PICKUP, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ─── 饥饿 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFoodLevel() < player.getFoodLevel()) {
            if (isDeniedAt(player.getLocation(), RegionFlag.HUNGER)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 自然恢复 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
            || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            RegionFlag.State state = manager.queryFlag(
                player.getWorld().getName(), player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ(), RegionFlag.HEAL);
            // DENY = 禁止自然恢复（反向逻辑不常见，仅当显式 DENY 时拦截）
            if (state == RegionFlag.State.DENY) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 末影珍珠传送 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderpearl(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (isDenied(event.getTo(), RegionFlag.ENDERPEARL, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noPermissionRegion());
        }
    }

    // ─── 紫颂果传送 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChorusFruit(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        if (isDenied(event.getTo(), RegionFlag.CHORUS_FRUIT, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ─── 进入/离开 区域 + greeting/farewell ───
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        String world = to.getWorld().getName();
        Region currentRegion = manager.getHighestPriorityRegion(world, to.getBlockX(), to.getBlockY(), to.getBlockZ());
        String currentId = currentRegion != null ? currentRegion.id() : null;
        String lastId = manager.getLastRegion(player.getUniqueId());

        if (java.util.Objects.equals(lastId, currentId)) return;

        // 离开旧区域
        if (lastId != null) {
            Region oldRegion = manager.getRegion(lastId, world);
            if (oldRegion != null) {
                String farewell = oldRegion.getFlagData(RegionFlag.FAREWELL);
                if (farewell != null && !farewell.isBlank()) {
                    sendNotification(player, ChatColor.translateAlternateColorCodes('&', farewell));
                } else if (config.notifications().showActionbar()) {
                    sendNotification(player, ChatColor.translateAlternateColorCodes('&',
                        config.messages().regionLeave().replace("{region}", lastId)));
                }
            }
        }

        // 进入新区域
        if (currentId != null) {
            // Entry 标志检查
            if (manager.queryFlagForPlayer(to, RegionFlag.ENTRY, player) == RegionFlag.State.DENY) {
                event.setCancelled(true);
                msg(player, config.messages().noPermissionRegion());
                return;
            }
            String greeting = currentRegion.getFlagData(RegionFlag.GREETING);
            if (greeting != null && !greeting.isBlank()) {
                sendNotification(player, ChatColor.translateAlternateColorCodes('&', greeting));
            } else if (config.notifications().showActionbar()) {
                sendNotification(player, ChatColor.translateAlternateColorCodes('&',
                    config.messages().regionEnter().replace("{region}", currentId)));
            }
        }

        manager.setLastRegion(player.getUniqueId(), currentId);
    }

    // ─── 药水泼溅 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (isDeniedAt(event.getEntity().getLocation(), RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
        }
    }

    // ─── 工具方法 ───

    private boolean isDenied(Location loc, RegionFlag flag, Player player) {
        return manager.queryFlagForPlayer(loc, flag, player) == RegionFlag.State.DENY;
    }

    private boolean isDeniedAt(Location loc, RegionFlag flag) {
        return manager.queryFlag(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), flag) == RegionFlag.State.DENY;
    }

    private Player getPlayerAttacker(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean isContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST
            || type == Material.BARREL || type == Material.HOPPER
            || type == Material.DROPPER || type == Material.DISPENSER
            || type == Material.SHULKER_BOX || type.name().contains("SHULKER_BOX")
            || type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER
            || type == Material.BREWING_STAND;
    }

    private void msg(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().prefix() + message));
    }

    private void sendNotification(Player player, String message) {
        switch (config.notifications().displayMode().toLowerCase()) {
            case "title" -> player.sendTitle("", message, 5, 40, 10);
            case "chat" -> player.sendMessage(message);
            default -> player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        }
    }
}
