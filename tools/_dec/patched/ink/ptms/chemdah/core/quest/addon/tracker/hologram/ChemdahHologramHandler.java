/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker.hologram;

import ink.ptms.chemdah.core.quest.addon.tracker.hologram.ChemdahHologram;
import java.util.List;
import kotlin.Metadata;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J&\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\tH&J\b\u0010\u000b\u001a\u00020\nH&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\f\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologramHandler;", "", "createHologram", "Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "player", "Lorg/bukkit/entity/Player;", "location", "Lorg/bukkit/Location;", "content", "", "", "getName", "Chemdah"})
public interface ChemdahHologramHandler {
    @NotNull
    public ChemdahHologram createHologram(@NotNull Player var1, @NotNull Location var2, @NotNull List<String> var3);

    @NotNull
    public String getName();
}

