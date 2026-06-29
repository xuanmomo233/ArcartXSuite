/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.util.Couple;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\"\n\u0000\n\u0002\u0010$\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\u0010%\n\u0000\n\u0002\u0010\u001c\n\u0002\b\u0005\u001a2\u0010\u0000\u001a\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u0001\"\u0004\b\u0000\u0010\u0002\"\u0004\b\u0001\u0010\u00032\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u0005\u001a@\u0010\u0006\u001a\u00020\u0007\"\u0004\b\u0000\u0010\u0002\"\u0004\b\u0001\u0010\u0003*\u0012\u0012\u0006\b\u0000\u0012\u0002H\u0002\u0012\u0006\b\u0000\u0012\u0002H\u00030\b2\u0018\u0010\t\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u00050\n\u001a4\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u0001\"\u0004\b\u0000\u0010\u0002\"\u0004\b\u0001\u0010\u0003*\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u00050\n\u001aO\u0010\u000b\u001a\u0002H\f\"\u0004\b\u0000\u0010\u0002\"\u0004\b\u0001\u0010\u0003\"\u0018\b\u0002\u0010\f*\u0012\u0012\u0006\b\u0000\u0012\u0002H\u0002\u0012\u0006\b\u0000\u0012\u0002H\u00030\b*\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u0002H\u0002\u0012\u0004\u0012\u0002H\u00030\u00050\n2\u0006\u0010\r\u001a\u0002H\f\u00a2\u0006\u0002\u0010\u000e\u00a8\u0006\u000f"}, d2={"mapOf", "", "K", "V", "couple", "Link/ptms/chemdah/util/Couple;", "putAll", "", "", "couples", "", "toMap", "M", "destination", "(Ljava/lang/Iterable;Ljava/util/Map;)Ljava/util/Map;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nMap.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Map.kt\nink/ptms/chemdah/util/MapKt\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,26:1\n1#2:27\n*E\n"})
public final class MapKt {
    @NotNull
    public static final <K, V, M extends Map<? super K, ? super V>> M toMap(@NotNull Iterable<Couple<K, V>> $this$toMap, @NotNull M destination) {
        M m;
        Intrinsics.checkNotNullParameter($this$toMap, (String)"<this>");
        Intrinsics.checkNotNullParameter(destination, (String)"destination");
        M $this$toMap_u24lambda_u240 = m = destination;
        boolean bl = false;
        MapKt.putAll($this$toMap_u24lambda_u240, $this$toMap);
        return m;
    }

    @NotNull
    public static final <K, V> Map<K, V> toMap(@NotNull Iterable<Couple<K, V>> $this$toMap) {
        Intrinsics.checkNotNullParameter($this$toMap, (String)"<this>");
        if ($this$toMap instanceof Collection) {
            Map<K, V> map;
            switch (((Collection)$this$toMap).size()) {
                case 0: {
                    map = MapsKt.emptyMap();
                    break;
                }
                case 1: {
                    map = MapKt.mapOf($this$toMap instanceof List ? (Couple)((List)$this$toMap).get(0) : $this$toMap.iterator().next());
                    break;
                }
                default: {
                    map = MapKt.toMap($this$toMap, (Map)new LinkedHashMap());
                }
            }
            return map;
        }
        return MapKt.toMap($this$toMap, (Map)new LinkedHashMap());
    }

    public static final <K, V> void putAll(@NotNull Map<? super K, ? super V> $this$putAll, @NotNull Iterable<Couple<K, V>> couples) {
        Intrinsics.checkNotNullParameter($this$putAll, (String)"<this>");
        Intrinsics.checkNotNullParameter(couples, (String)"couples");
        for (Couple<K, V> couple : couples) {
            K key = couple.component1();
            V value2 = couple.component2();
            $this$putAll.put(key, value2);
        }
    }

    @NotNull
    public static final <K, V> Map<K, V> mapOf(@NotNull Couple<K, V> couple) {
        Intrinsics.checkNotNullParameter(couple, (String)"couple");
        Map<K, V> map = Collections.singletonMap(couple.getKey(), couple.getValue());
        Intrinsics.checkNotNullExpressionValue(map, (String)"singletonMap(couple.key, couple.value)");
        return map;
    }
}

