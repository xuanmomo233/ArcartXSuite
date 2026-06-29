/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common5.Coerce;
import kotlin.Metadata;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0014\n\u0000\n\u0002\u0010\u0006\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\u001a\u0016\u0010\u0000\u001a\u00020\u0001*\u0004\u0018\u00010\u00022\b\b\u0002\u0010\u0003\u001a\u00020\u0001\u001a\u0016\u0010\u0004\u001a\u00020\u0005*\u0004\u0018\u00010\u00022\b\b\u0002\u0010\u0003\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"asDouble", "", "", "def", "asInt", "", "Chemdah"})
public final class NumberKt {
    public static final int asInt(@Nullable Object $this$asInt, int def) {
        Object object = $this$asInt;
        if (object == null) {
            object = def;
        }
        return Coerce.toInteger((Object)object);
    }

    public static /* synthetic */ int asInt$default(Object object, int n, int n2, Object object2) {
        if ((n2 & 1) != 0) {
            n = 0;
        }
        return NumberKt.asInt(object, n);
    }

    public static final double asDouble(@Nullable Object $this$asDouble, double def) {
        Object object = $this$asDouble;
        if (object == null) {
            object = def;
        }
        return Coerce.toDouble((Object)object);
    }

    public static /* synthetic */ double asDouble$default(Object object, double d, int n, Object object2) {
        if ((n & 1) != 0) {
            d = 0.0;
        }
        return NumberKt.asDouble(object, d);
    }
}

