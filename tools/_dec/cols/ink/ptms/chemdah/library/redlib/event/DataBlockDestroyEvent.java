/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.event;

import ink.ptms.chemdah.library.redlib.DataBlock;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

public class DataBlockDestroyEvent
extends Event
implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Event parent;
    private boolean cancelled = false;
    private final DataBlock db;
    private final DestroyCause cause;

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public DataBlockDestroyEvent(DataBlock db, Event parent, DestroyCause cause) {
        this.db = db;
        this.parent = parent;
        this.cause = cause;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancelParent() {
        Block block;
        BlockExplodeEvent e;
        this.setCancelled(true);
        if (this.parent instanceof Cancellable) {
            ((Cancellable)this.parent).setCancelled(true);
        }
        if (this.parent instanceof BlockExplodeEvent) {
            e = (BlockExplodeEvent)this.parent;
            block = this.db.getBlock();
            e.blockList().remove(this.db.getBlock());
            if (!this.cancelled) {
                e.blockList().add(block);
            }
        }
        if (this.parent instanceof EntityExplodeEvent) {
            e = (EntityExplodeEvent)this.parent;
            block = this.db.getBlock();
            e.blockList().remove(this.db.getBlock());
            if (!this.cancelled) {
                e.blockList().add(block);
            }
        }
    }

    public DestroyCause getCause() {
        return this.cause;
    }

    public Event getParent() {
        return this.parent;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    public DataBlock getDataBlock() {
        return this.db;
    }

    public Block getBlock() {
        return this.db.getBlock();
    }

    public static enum DestroyCause {
        PLAYER_BREAK,
        COMBUST,
        FADE,
        LEAVES_DECAY,
        ENTITY_EXPLOSION,
        BLOCK_EXPLOSION,
        ENTITY,
        LIQUID,
        CAKE,
        ZOMBIE_BREAK_DOOR,
        SILVERFISH,
        WITHER,
        ENDER_DRAGON,
        FALLING_BLOCK,
        ENDERMAN;


        public boolean isTransferred() {
            return this == FALLING_BLOCK || this == ENDERMAN;
        }
    }
}

