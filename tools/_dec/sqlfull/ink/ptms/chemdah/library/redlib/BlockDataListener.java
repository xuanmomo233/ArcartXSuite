/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.DataBlock;
import ink.ptms.chemdah.library.redlib.event.DataBlockDestroyEvent;
import ink.ptms.chemdah.library.redlib.event.DataBlockMoveEvent;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

class BlockDataListener
implements Listener {
    private final BlockDataManager manager;

    public BlockDataListener(BlockDataManager manager, Plugin plugin2) {
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents((Listener)this, plugin2);
    }

    private void fireDestroy(DataBlock db, Event parent, DataBlockDestroyEvent.DestroyCause cause) {
        if (db == null) {
            return;
        }
        DataBlockDestroyEvent ev = new DataBlockDestroyEvent(db, parent, cause);
        Bukkit.getPluginManager().callEvent((Event)ev);
        if (!ev.isCancelled()) {
            this.manager.remove(db);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent e) {
        DataBlock db = this.manager.getDataBlock(e.getBlock(), false);
        this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.PLAYER_BREAK);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onExplode(BlockExplodeEvent e) {
        this.handleExplosion(e.blockList(), (Cancellable)e, DataBlockDestroyEvent.DestroyCause.BLOCK_EXPLOSION);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onExplode(EntityExplodeEvent e) {
        this.handleExplosion(e.blockList(), (Cancellable)e, DataBlockDestroyEvent.DestroyCause.ENTITY_EXPLOSION);
    }

    private void handleExplosion(List<Block> blocks2, Cancellable e, DataBlockDestroyEvent.DestroyCause cause) {
        ArrayList toRemove = new ArrayList();
        blocks2.forEach(b -> {
            DataBlock db = this.manager.getDataBlock((Block)b, false);
            if (db == null) {
                return;
            }
            DataBlockDestroyEvent ev = new DataBlockDestroyEvent(db, (Event)e, cause);
            Bukkit.getPluginManager().callEvent((Event)ev);
            if (ev.isCancelled()) {
                if (e instanceof EntityExplodeEvent) {
                    ((EntityExplodeEvent)e).blockList().remove(b);
                } else if (e instanceof BlockExplodeEvent) {
                    ((BlockExplodeEvent)e).blockList().remove(b);
                } else {
                    e.setCancelled(true);
                }
            } else {
                toRemove.add(db);
            }
        });
        if (e.isCancelled()) {
            return;
        }
        toRemove.forEach(this.manager::remove);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onCombust(BlockBurnEvent e) {
        DataBlock db = this.manager.getDataBlock(e.getBlock(), false);
        this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.COMBUST);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onFade(BlockFadeEvent e) {
        DataBlock db = this.manager.getDataBlock(e.getBlock(), false);
        this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.FADE);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        DataBlock db = this.manager.getDataBlock(e.getBlock(), false);
        this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.LEAVES_DECAY);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        this.handlePiston(e.getBlocks(), (BlockPistonEvent)e);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        this.handlePiston(e.getBlocks(), (BlockPistonEvent)e);
    }

    private void handlePiston(List<Block> blocks2, BlockPistonEvent e) {
        ArrayList toMove = new ArrayList();
        blocks2.forEach(b -> {
            DataBlock db = this.manager.getDataBlock((Block)b, false);
            if (db == null) {
                return;
            }
            Block destination = db.getBlock().getRelative(e.getDirection());
            DataBlockMoveEvent ev = new DataBlockMoveEvent(db, destination, (Event)e);
            Bukkit.getPluginManager().callEvent((Event)ev);
            if (!ev.isCancelled()) {
                toMove.add(db);
            } else {
                e.setCancelled(true);
            }
        });
        if (e.isCancelled()) {
            return;
        }
        HashMap<Block, Map> moved = new HashMap<Block, Map>();
        toMove.forEach(db -> {
            Block destination = db.getBlock().getRelative(e.getDirection());
            moved.put(destination, db.data);
        });
        toMove.forEach(this.manager::remove);
        moved.forEach((block, data2) -> {
            this.manager.getDataBlock((Block)block, (boolean)false).data = data2;
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (MinecraftVersion.INSTANCE.getMajorLegacy() < 11300 || !(e.getToBlock().getBlockData() instanceof Waterlogged)) {
            DataBlock db = this.manager.getDataBlock(e.getToBlock(), false);
            this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.LIQUID);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onCake(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.CAKE && e.getClickedBlock().getData() == 5) {
            DataBlock db = this.manager.getDataBlock(e.getClickedBlock(), false);
            this.fireDestroy(db, (Event)e, DataBlockDestroyEvent.DestroyCause.CAKE);
        }
    }

    private boolean isLegacyAir(Material material) {
        return MinecraftVersion.INSTANCE.getMajorLegacy() >= 11500 ? material.isAir() : material == Material.AIR;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (this.isLegacyAir(e.getTo())) {
            DataBlockDestroyEvent.DestroyCause destroyCause;
            if (e.getEntity() instanceof Zombie) {
                destroyCause = DataBlockDestroyEvent.DestroyCause.ZOMBIE_BREAK_DOOR;
            } else if (e.getEntity() instanceof Silverfish) {
                destroyCause = DataBlockDestroyEvent.DestroyCause.SILVERFISH;
            } else if (e.getEntity() instanceof Wither) {
                destroyCause = DataBlockDestroyEvent.DestroyCause.WITHER;
            } else if (e.getEntity() instanceof EnderDragon) {
                destroyCause = DataBlockDestroyEvent.DestroyCause.ENDER_DRAGON;
            } else {
                if (e.getEntity() instanceof Enderman || e.getEntity() instanceof FallingBlock) {
                    e.setCancelled(true);
                    return;
                }
                destroyCause = DataBlockDestroyEvent.DestroyCause.ENTITY;
            }
            DataBlock db = this.manager.getDataBlock(e.getBlock(), false);
            this.fireDestroy(db, (Event)e, destroyCause);
        }
    }
}

