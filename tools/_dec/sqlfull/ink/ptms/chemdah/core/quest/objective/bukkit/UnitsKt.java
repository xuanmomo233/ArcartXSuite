/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import kotlin.Metadata;
import kotlin1822.NotImplementedError;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u001a\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\"\u0011\u0010\u0000\u001a\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0002\u0010\u0003\"\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\"\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"EMPTY_EVENT", "Lorg/bukkit/event/Event;", "getEMPTY_EVENT", "()Lorg/bukkit/event/Event;", "EMPTY_ITEM", "Lorg/bukkit/inventory/ItemStack;", "getEMPTY_ITEM", "()Lorg/bukkit/inventory/ItemStack;", "EMPTY_LOCATION", "Lorg/bukkit/Location;", "getEMPTY_LOCATION", "()Lorg/bukkit/Location;", "Chemdah"})
public final class UnitsKt {
    @NotNull
    private static final ItemStack EMPTY_ITEM = new ItemStack(Material.AIR);
    @NotNull
    private static final Location EMPTY_LOCATION = new Location((World)Bukkit.getWorlds().get(0), 0.0, 0.0, 0.0);
    @NotNull
    private static final Event EMPTY_EVENT = new Event(){

        @NotNull
        public HandlerList getHandlers() {
            String string = "Not yet implemented";
            throw new NotImplementedError("An operation is not implemented: " + string);
        }
    };

    @NotNull
    public static final ItemStack getEMPTY_ITEM() {
        return EMPTY_ITEM;
    }

    @NotNull
    public static final Location getEMPTY_LOCATION() {
        return EMPTY_LOCATION;
    }

    @NotNull
    public static final Event getEMPTY_EVENT() {
        return EMPTY_EVENT;
    }
}

