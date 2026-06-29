/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0012\n\u0000\n\u0002\u0010\u000b\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\u001a\u0018\u0010\u0000\u001a\u00020\u0001*\b\u0012\u0004\u0012\u00020\u00030\u00022\u0006\u0010\u0004\u001a\u00020\u0003\u00a8\u0006\u0005"}, d2={"has", "", "", "", "value", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCollection.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Collection.kt\nink/ptms/chemdah/util/CollectionKt\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,8:1\n1747#2,3:9\n*S KotlinDebug\n*F\n+ 1 Collection.kt\nink/ptms/chemdah/util/CollectionKt\n*L\n7#1:9,3\n*E\n"})
public final class CollectionKt {
    public static final boolean has(@NotNull List<String> $this$has, @NotNull String value2) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter($this$has, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            Iterable $this$any$iv = $this$has;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)value2, (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }
}

