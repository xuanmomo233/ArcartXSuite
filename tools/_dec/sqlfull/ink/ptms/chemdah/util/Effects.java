/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J6\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0006\u001a\u00020\u00052\u0006\u0010\u0007\u001a\u00020\u00052\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\f\u001a\u00020\u000b\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/util/Effects;", "", "()V", "drawArrow", "", "Link/ptms/chemdah/taboolib/common/util/Location;", "start", "target", "density", "", "len", "", "angle", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nEffects.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Effects.kt\nink/ptms/chemdah/util/Effects\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,38:1\n1855#2,2:39\n*S KotlinDebug\n*F\n+ 1 Effects.kt\nink/ptms/chemdah/util/Effects\n*L\n30#1:39,2\n*E\n"})
public final class Effects {
    @NotNull
    public static final Effects INSTANCE = new Effects();

    private Effects() {
    }

    @NotNull
    public final List<Location> drawArrow(@NotNull Location start, @NotNull Location target, int density, double len, double angle) {
        Intrinsics.checkNotNullParameter((Object)start, (String)"start");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Vector st = new Vector(start.getX(), start.getY(), start.getZ());
        Vector ed = new Vector(target.getX(), target.getY(), target.getZ());
        Vector vector = ed.clone().subtract(st);
        Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"ed.clone().subtract(st)");
        Vector dl = vector;
        double length = dl.clone().length();
        Vector vector2 = dl.clone().multiply(1.0 / length);
        Intrinsics.checkNotNullExpressionValue((Object)vector2, (String)"dl.clone().multiply(1 / length)");
        Vector uniV = vector2;
        Vector vector3 = uniV.clone().rotateAroundY(Math.toRadians(angle));
        Intrinsics.checkNotNullExpressionValue((Object)vector3, (String)"uniV.clone().rotateAroundY(Math.toRadians(angle))");
        Vector uniVr = vector3;
        Vector vector4 = uniV.clone().rotateAroundY(Math.toRadians(-angle));
        Intrinsics.checkNotNullExpressionValue((Object)vector4, (String)"uniV.clone().rotateAroundY(Math.toRadians(-angle))");
        Vector uniVl = vector4;
        double l = len / (double)density;
        ArrayList<Location> result = new ArrayList<Location>();
        Vector vector5 = st.clone().add(uniV);
        String string = start.getWorld();
        Intrinsics.checkNotNull((Object)string);
        result.add(vector5.toLocation(string));
        Iterable $this$forEach$iv = (Iterable)RangesKt.until((int)0, (int)density);
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.iterator();
        while (iterator.hasNext()) {
            Vector point2;
            Vector point;
            int element$iv;
            int i = element$iv = ((IntIterator)iterator).nextInt();
            boolean bl = false;
            Intrinsics.checkNotNullExpressionValue((Object)st.clone().add(uniV).subtract(uniVr.clone().multiply(l * (double)(i + 1))), (String)"st.clone().add(uniV).sub\u2026().multiply(l * (i + 1)))");
            Intrinsics.checkNotNullExpressionValue((Object)st.clone().add(uniV).subtract(uniVl.clone().multiply(l * (double)(i + 1))), (String)"st.clone().add(uniV).sub\u2026().multiply(l * (i + 1)))");
            String string2 = start.getWorld();
            Intrinsics.checkNotNull((Object)string2);
            result.add(point.toLocation(string2));
            String string3 = start.getWorld();
            Intrinsics.checkNotNull((Object)string3);
            result.add(point2.toLocation(string3));
        }
        return result;
    }

    public static /* synthetic */ List drawArrow$default(Effects effects, Location location, Location location2, int n, double d, double d2, int n2, Object object) {
        if ((n2 & 0x10) != 0) {
            d2 = 45.0;
        }
        return effects.drawArrow(location, location2, n, d, d2);
    }
}

