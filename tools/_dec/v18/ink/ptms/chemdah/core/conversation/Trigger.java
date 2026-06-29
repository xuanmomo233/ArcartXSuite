/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001:\u0001\u0012B\u0007\b\u0016\u00a2\u0006\u0002\u0010\u0002B\u0013\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\u0002\u0010\u0006J\u000f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u00c6\u0003J\u0019\u0010\n\u001a\u00020\u00002\u000e\b\u0002\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u00c6\u0001J\u0013\u0010\u000b\u001a\u00020\f2\b\u0010\r\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000e\u001a\u00020\u000fH\u00d6\u0001J\t\u0010\u0010\u001a\u00020\u0011H\u00d6\u0001R\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/conversation/Trigger;", "", "()V", "id", "", "Link/ptms/chemdah/core/conversation/Trigger$Id;", "(Ljava/util/List;)V", "getId", "()Ljava/util/List;", "component1", "copy", "equals", "", "other", "hashCode", "", "toString", "", "Id", "Chemdah"})
public final class Trigger {
    @NotNull
    private final List<Id> id;

    public Trigger(@NotNull List<Id> id2) {
        Intrinsics.checkNotNullParameter(id2, (String)"id");
        this.id = id2;
    }

    @NotNull
    public final List<Id> getId() {
        return this.id;
    }

    public Trigger() {
        this(new ArrayList());
    }

    @NotNull
    public final List<Id> component1() {
        return this.id;
    }

    @NotNull
    public final Trigger copy(@NotNull List<Id> id2) {
        Intrinsics.checkNotNullParameter(id2, (String)"id");
        return new Trigger(id2);
    }

    public static /* synthetic */ Trigger copy$default(Trigger trigger2, List list2, int n, Object object) {
        if ((n & 1) != 0) {
            list2 = trigger2.id;
        }
        return trigger2.copy(list2);
    }

    @NotNull
    public String toString() {
        return "Trigger(id=" + this.id + ')';
    }

    public int hashCode() {
        return ((Object)this.id).hashCode();
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Trigger)) {
            return false;
        }
        Trigger trigger2 = (Trigger)other;
        return Intrinsics.areEqual(this.id, trigger2.id);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001J\u0016\u0010\u0011\u001a\u00020\r2\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0003J\t\u0010\u0012\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/conversation/Trigger$Id;", "", "namespace", "", "value", "(Ljava/lang/String;Ljava/lang/String;)V", "getNamespace", "()Ljava/lang/String;", "getValue", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "isNPC", "toString", "Chemdah"})
    public static final class Id {
        @NotNull
        private final String namespace;
        @NotNull
        private final String value;

        public Id(@NotNull String namespace, @NotNull String value2) {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            this.namespace = namespace;
            this.value = value2;
        }

        @NotNull
        public final String getNamespace() {
            return this.namespace;
        }

        @NotNull
        public final String getValue() {
            return this.value;
        }

        public final boolean isNPC(@NotNull String namespace, @NotNull String value2) {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            return StringsKt.equals((String)namespace, (String)this.namespace, (boolean)true) && StringsKt.equals((String)value2, (String)this.value, (boolean)true);
        }

        @NotNull
        public final String component1() {
            return this.namespace;
        }

        @NotNull
        public final String component2() {
            return this.value;
        }

        @NotNull
        public final Id copy(@NotNull String namespace, @NotNull String value2) {
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            return new Id(namespace, value2);
        }

        public static /* synthetic */ Id copy$default(Id id2, String string, String string2, int n, Object object) {
            if ((n & 1) != 0) {
                string = id2.namespace;
            }
            if ((n & 2) != 0) {
                string2 = id2.value;
            }
            return id2.copy(string, string2);
        }

        @NotNull
        public String toString() {
            return "Id(namespace=" + this.namespace + ", value=" + this.value + ')';
        }

        public int hashCode() {
            int result = this.namespace.hashCode();
            result = result * 31 + this.value.hashCode();
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id)) {
                return false;
            }
            Id id2 = (Id)other;
            if (!Intrinsics.areEqual((Object)this.namespace, (Object)id2.namespace)) {
                return false;
            }
            return Intrinsics.areEqual((Object)this.value, (Object)id2.value);
        }
    }
}

