/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J\b\u0010\u000b\u001a\u00020\u0003H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/addon/data/TrackToLocation;", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "center", "", "(Ljava/lang/String;)V", "getCenter", "()Ljava/lang/String;", "getLocation", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "identifier", "Chemdah"})
public final class TrackToLocation
implements TrackCenter {
    @NotNull
    private final String center;

    public TrackToLocation(@NotNull String center2) {
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        this.center = center2;
    }

    @NotNull
    public final String getCenter() {
        return this.center;
    }

    @Override
    @NotNull
    public String identifier() {
        return this.center;
    }

    @Override
    @NotNull
    public Location getLocation(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Location location = new InferArea.Single(this.center, false).getPositions().get(0).clone();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"InferArea.Single(center,\u2026lse).positions[0].clone()");
        return location;
    }
}

