/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u0000*\u0004\b\u0000\u0010\u0001*\u0004\b\u0001\u0010\u00022\u00020\u0003B\u0015\u0012\u0006\u0010\u0004\u001a\u00028\u0000\u0012\u0006\u0010\u0005\u001a\u00028\u0001\u00a2\u0006\u0002\u0010\u0006J\u000e\u0010\u000e\u001a\u00028\u0000H\u00c6\u0003\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\u000f\u001a\u00028\u0001H\u00c6\u0003\u00a2\u0006\u0002\u0010\bJ.\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00028\u00010\u00002\b\b\u0002\u0010\u0004\u001a\u00028\u00002\b\b\u0002\u0010\u0005\u001a\u00028\u0001H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0011J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0003H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\u0012\u0010\u0017\u001a\u000e\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00028\u00010\u0018J\t\u0010\u0019\u001a\u00020\u001aH\u00d6\u0001R\u001c\u0010\u0004\u001a\u00028\u0000X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\u000b\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u001c\u0010\u0005\u001a\u00028\u0001X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\u000b\u001a\u0004\b\f\u0010\b\"\u0004\b\r\u0010\n\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/util/Couple;", "K", "V", "", "key", "value", "(Ljava/lang/Object;Ljava/lang/Object;)V", "getKey", "()Ljava/lang/Object;", "setKey", "(Ljava/lang/Object;)V", "Ljava/lang/Object;", "getValue", "setValue", "component1", "component2", "copy", "(Ljava/lang/Object;Ljava/lang/Object;)Link/ptms/chemdah/util/Couple;", "equals", "", "other", "hashCode", "", "toPair", "Lkotlin1822/Pair;", "toString", "", "Chemdah"})
public final class Couple<K, V> {
    private K key;
    private V value;

    public Couple(K key, V value2) {
        this.key = key;
        this.value = value2;
    }

    public final K getKey() {
        return this.key;
    }

    public final void setKey(K k) {
        this.key = k;
    }

    public final V getValue() {
        return this.value;
    }

    public final void setValue(V v) {
        this.value = v;
    }

    @NotNull
    public final Pair<K, V> toPair() {
        return TuplesKt.to(this.key, this.value);
    }

    public final K component1() {
        return this.key;
    }

    public final V component2() {
        return this.value;
    }

    @NotNull
    public final Couple<K, V> copy(K key, V value2) {
        return new Couple<K, V>(key, value2);
    }

    public static /* synthetic */ Couple copy$default(Couple couple, Object object, Object object2, int n, Object object3) {
        if ((n & 1) != 0) {
            object = couple.key;
        }
        if ((n & 2) != 0) {
            object2 = couple.value;
        }
        return couple.copy(object, object2);
    }

    @NotNull
    public String toString() {
        return "Couple(key=" + this.key + ", value=" + this.value + ')';
    }

    public int hashCode() {
        int result = this.key == null ? 0 : this.key.hashCode();
        result = result * 31 + (this.value == null ? 0 : this.value.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Couple)) {
            return false;
        }
        Couple couple = (Couple)other;
        if (!Intrinsics.areEqual(this.key, couple.key)) {
            return false;
        }
        return Intrinsics.areEqual(this.value, couple.value);
    }
}

