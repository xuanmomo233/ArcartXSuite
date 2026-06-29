/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\n\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a\n\u0010\u0000\u001a\u00020\u0001*\u00020\u0001\u001a\n\u0010\u0002\u001a\u00020\u0001*\u00020\u0001\u00a8\u0006\u0003"}, d2={"finite", "Lorg/bukkit/Location;", "toCenter", "Chemdah"})
public final class LocationKt {
    @NotNull
    public static final Location toCenter(@NotNull Location $this$toCenter) {
        Intrinsics.checkNotNullParameter((Object)$this$toCenter, (String)"<this>");
        Location location = $this$toCenter.clone();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"clone()");
        Location loc = location;
        loc.setX((double)$this$toCenter.getBlockX() + 0.5);
        loc.setY((double)$this$toCenter.getBlockY() + 0.5);
        loc.setZ((double)$this$toCenter.getBlockZ() + 0.5);
        return loc;
    }

    @NotNull
    public static final Location finite(@NotNull Location $this$finite) {
        Intrinsics.checkNotNullParameter((Object)$this$finite, (String)"<this>");
        double d = $this$finite.getX();
        if (!(!Double.isInfinite(d) && !Double.isNaN(d))) {
            $this$finite.setX(0.0);
        }
        if (!Double.isInfinite($this$finite.getY())) {
            $this$finite.setY(0.0);
        }
        if (!Double.isInfinite($this$finite.getZ())) {
            $this$finite.setZ(0.0);
        }
        if (!Float.isInfinite($this$finite.getYaw())) {
            $this$finite.setYaw(0.0f);
        }
        if (!Float.isInfinite($this$finite.getPitch())) {
            $this$finite.setPitch(0.0f);
        }
        return $this$finite;
    }
}

