/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.event;

import ink.ptms.chemdah.library.redlib.DataBlock;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DataBlockMoveEvent
extends Event
implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final DataBlock db;
    private final Block destination;
    private final Event parent;
    private boolean cancelled = false;

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public DataBlockMoveEvent(DataBlock db, Block destination, Event parent) {
        this.db = db;
        this.parent = parent;
        this.destination = destination;
    }

    public Event getParent() {
        return this.parent;
    }

    public Block getDestination() {
        return this.destination;
    }

    public DataBlock getDataBlock() {
        return this.db;
    }

    public Block getBlock() {
        return this.db.getBlock();
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancelParent() {
        if (this.parent instanceof Cancellable) {
            ((Cancellable)this.parent).setCancelled(true);
        }
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }
}

