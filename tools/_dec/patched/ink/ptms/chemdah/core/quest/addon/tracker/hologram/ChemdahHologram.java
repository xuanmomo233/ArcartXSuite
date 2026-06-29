/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.tracker.hologram;

import java.util.List;
import kotlin.Metadata;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\bf\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H&J\u0010\u0010\u0004\u001a\u00020\u00032\u0006\u0010\u0005\u001a\u00020\u0006H&J\u0016\u0010\u0007\u001a\u00020\u00032\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\tH&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u000b\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/quest/addon/tracker/hologram/ChemdahHologram;", "", "remove", "", "teleport", "location", "Lorg/bukkit/Location;", "update", "content", "", "", "Chemdah"})
public interface ChemdahHologram {
    public void teleport(@NotNull Location var1);

    public void update(@NotNull List<String> var1);

    public void remove();
}

