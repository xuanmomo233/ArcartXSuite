package xuanmo.arcartxsuite.regions.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
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
        if (isDenied(event.getBlock().getLocation(), RegionFlag.BLOCK_PLACE, event.getPlayer())
            || event instanceof BlockMultiPlaceEvent multi && multi.getReplacedBlockStates().stream()
                .anyMatch(state -> isDenied(state.getLocation(), RegionFlag.BLOCK_PLACE, event.getPlayer()))) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noBuild());
        }
    }

    // ─── 玩家交互 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // 踩踏（物理交互）：耕地 → TRAMPLE，其余压力板等 → 不归 USE 管
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            if (event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND
                && isDenied(event.getClickedBlock().getLocation(), RegionFlag.TRAMPLE, event.getPlayer())) {
                event.setCancelled(true);
            }
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Material type = block.getType();
        Player player = event.getPlayer();
        var rules = manager.rulesAt(block.getLocation());
        if (rules.interaction(type, player).state() == RegionFlag.State.DENY
            || isContainer(type) && rules.decide(RegionFlag.CHEST_ACCESS, player).state() == RegionFlag.State.DENY) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noInteract());
        }
    }

    // ─── 实体交互（喂食/上马/交易等，归 USE）───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isDenied(event.getRightClicked().getLocation(), RegionFlag.USE, event.getPlayer())) {
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
        if (isDenied(victim.getLocation(), RegionFlag.PVP, attacker)
            || isDenied(attacker.getLocation(), RegionFlag.PVP, attacker)) {
            event.setCancelled(true);
            msg(attacker, config.messages().noPvp());
        }
    }

    // ─── 怪物伤害 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (getPlayerAttacker(event.getDamager()) != null) return;
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvincibility(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && manager.queryFlagForPlayer(player.getLocation(), RegionFlag.INVINCIBILITY, player) == RegionFlag.State.ALLOW) {
            event.setCancelled(true);
        }
    }

    // ─── 爆炸 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        RegionFlag flag;
        if (event.getEntity() instanceof org.bukkit.entity.TNTPrimed) flag = RegionFlag.TNT;
        else if (event.getEntity() instanceof org.bukkit.entity.Creeper) flag = RegionFlag.CREEPER_EXPLOSION;
        else if (event.getEntity() instanceof org.bukkit.entity.Wither) flag = RegionFlag.WITHER_DAMAGE;
        else if (event.getEntity() instanceof org.bukkit.entity.Fireball) flag = RegionFlag.GHAST_FIREBALL;
        else flag = RegionFlag.OTHER_EXPLOSION;

        event.blockList().removeIf(manager.blockRules(flag)::denied);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(manager.blockRules(RegionFlag.OTHER_EXPLOSION)::denied);
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

    // 活塞本体、活塞头、被移动方块的起点和终点均受保护。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace face = pistonFace(event.getBlock(), event.getDirection());
        if (pistonDenied(event.getBlock(), event.getBlocks(), face, face)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace face = pistonFace(event.getBlock(), event.getDirection());
        if (pistonDenied(event.getBlock(), event.getBlocks(), face, face.getOppositeFace())) event.setCancelled(true);
    }

    private static BlockFace pistonFace(Block piston, BlockFace fallback) {
        return piston.getBlockData() instanceof org.bukkit.block.data.Directional directional
            ? directional.getFacing() : fallback;
    }

    private boolean pistonDenied(Block piston, java.util.List<Block> blocks, BlockFace face, BlockFace movement) {
        if (isDeniedAt(piston.getLocation(), RegionFlag.PISTONS)
            || isDeniedAt(piston.getRelative(face).getLocation(), RegionFlag.PISTONS)) return true;
        for (Block block : blocks) {
            if (isDeniedAt(block.getLocation(), RegionFlag.PISTONS)
                || isDeniedAt(block.getRelative(movement).getLocation(), RegionFlag.PISTONS)) return true;
        }
        return false;
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

    // ─── 末影人拿方块 / 凋灵破坏 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Enderman) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.ENDERMAN_GRIEF)) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof org.bukkit.entity.Wither) {
            if (isDeniedAt(event.getBlock().getLocation(), RegionFlag.WITHER_DAMAGE)) {
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isDenied(player.getLocation(), RegionFlag.ITEM_PICKUP, player)
            || isDenied(event.getItem().getLocation(), RegionFlag.ITEM_PICKUP, player)) {
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

    // ─── 药水泼溅 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (isDeniedAt(event.getEntity().getLocation(), RegionFlag.POTION_SPLASH)) {
            event.setCancelled(true);
        }
    }

    // ─── 载具 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            if (isDenied(event.getVehicle().getLocation(), RegionFlag.VEHICLE_DESTROY, player)) {
                event.setCancelled(true);
                msg(player, config.messages().noBuild());
            }
        } else if (isDeniedAt(event.getVehicle().getLocation(), RegionFlag.VEHICLE_DESTROY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleCreate(org.bukkit.event.vehicle.VehicleCreateEvent event) {
        if (isDeniedAt(event.getVehicle().getLocation(), RegionFlag.VEHICLE_PLACE)) {
            event.setCancelled(true);
        }
    }

    // ─── 骑乘 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMount(org.spigotmc.event.entity.EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isDenied(event.getMount().getLocation(), RegionFlag.RIDE, player)) {
            event.setCancelled(true);
            msg(player, config.messages().noInteract());
        }
    }

    // ─── 睡觉 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (isDenied(event.getBed().getLocation(), RegionFlag.SLEEP, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noInteract());
        }
    }

    // ─── 经验掉落（死亡不掉经验）───
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (isDeniedAt(event.getEntity().getLocation(), RegionFlag.EXP_DROP)) {
            event.setDroppedExp(0);
        }
    }

    // ─── 飞行 ───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        if (isDenied(event.getPlayer().getLocation(), RegionFlag.FLY, event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().setFlying(false);
            msg(event.getPlayer(), config.messages().noPermissionRegion());
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        enforceFlight(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        enforceFlight(event.getPlayer());
    }

    public void enforceFlight(Player player) {
        if (player.isFlying() && isDenied(player.getLocation(), RegionFlag.FLY, player)) player.setFlying(false);
    }

    // ─── 挂物破坏（画/物品框，归 BLOCK_BREAK）───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (isDenied(event.getEntity().getLocation(), RegionFlag.BLOCK_BREAK, player)) {
                event.setCancelled(true);
                msg(player, config.messages().noBuild());
            }
        }
    }

    // ─── 桶装/倒液体（倒=BLOCK_PLACE，装=BLOCK_BREAK）───
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isDenied(event.getBlock().getLocation(), RegionFlag.BLOCK_PLACE, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noBuild());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isDenied(event.getBlock().getLocation(), RegionFlag.BLOCK_BREAK, event.getPlayer())) {
            event.setCancelled(true);
            msg(event.getPlayer(), config.messages().noBuild());
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
}
