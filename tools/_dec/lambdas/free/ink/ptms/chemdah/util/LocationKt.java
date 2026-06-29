/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0010\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0004\u001a\n\u0010\u0000\u001a\u00020\u0001*\u00020\u0001\u001a\u0012\u0010\u0002\u001a\u00020\u0003*\u00020\u00012\u0006\u0010\u0004\u001a\u00020\u0001\u001a\u0012\u0010\u0005\u001a\u00020\u0003*\u00020\u00012\u0006\u0010\u0004\u001a\u00020\u0001\u001a\n\u0010\u0006\u001a\u00020\u0001*\u00020\u0001\u00a8\u0006\u0007"}, d2={"finite", "Lorg/bukkit/Location;", "safeDistance", "", "loc", "safeDistanceIgnoreY", "toCenter", "Chemdah"})
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

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static final double safeDistance(@NotNull Location $this$safeDistance, @NotNull Location loc) {
        Intrinsics.checkNotNullParameter((Object)$this$safeDistance, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)loc, (String)"loc");
        if ($this$safeDistance.getWorld() == null) return Double.MAX_VALUE;
        World world = $this$safeDistance.getWorld();
        World world2 = loc.getWorld();
        if (!Intrinsics.areEqual((Object)(world != null ? world.getName() : null), (Object)(world2 != null ? world2.getName() : null))) return Double.MAX_VALUE;
        double d = $this$safeDistance.distance(loc);
        return d;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static final double safeDistanceIgnoreY(@NotNull Location $this$safeDistanceIgnoreY, @NotNull Location loc) {
        Intrinsics.checkNotNullParameter((Object)$this$safeDistanceIgnoreY, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)loc, (String)"loc");
        if ($this$safeDistanceIgnoreY.getWorld() == null) return Double.MAX_VALUE;
        World world = $this$safeDistanceIgnoreY.getWorld();
        World world2 = loc.getWorld();
        if (!Intrinsics.areEqual((Object)(world != null ? world.getName() : null), (Object)(world2 != null ? world2.getName() : null))) return Double.MAX_VALUE;
        double dx = $this$safeDistanceIgnoreY.getX() - loc.getX();
        double dz = $this$safeDistanceIgnoreY.getZ() - loc.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        return d;
    }
}

