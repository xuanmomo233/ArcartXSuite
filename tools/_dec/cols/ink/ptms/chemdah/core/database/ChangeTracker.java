/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.database;

import ink.ptms.chemdah.core.Data;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B'\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007\u00a2\u0006\u0002\u0010\bJ\u0015\u0010\r\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003H\u00c6\u0003J\u000f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007H\u00c6\u0003J/\u0010\u000f\u001a\u00020\u00002\u0014\b\u0002\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u00032\u000e\b\u0002\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007H\u00c6\u0001J\u0013\u0010\u0010\u001a\u00020\u00112\b\u0010\u0012\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0004H\u00d6\u0001R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00040\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u001d\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0016"}, d2={"Link/ptms/chemdah/core/database/ChangeTracker;", "", "modified", "", "", "Link/ptms/chemdah/core/Data;", "drops", "", "(Ljava/util/Map;Ljava/util/Set;)V", "getDrops", "()Ljava/util/Set;", "getModified", "()Ljava/util/Map;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
public final class ChangeTracker {
    @NotNull
    private final Map<String, Data> modified;
    @NotNull
    private final Set<String> drops;

    public ChangeTracker(@NotNull Map<String, ? extends Data> modified, @NotNull Set<String> drops) {
        Intrinsics.checkNotNullParameter(modified, (String)"modified");
        Intrinsics.checkNotNullParameter(drops, (String)"drops");
        this.modified = modified;
        this.drops = drops;
    }

    @NotNull
    public final Map<String, Data> getModified() {
        return this.modified;
    }

    @NotNull
    public final Set<String> getDrops() {
        return this.drops;
    }

    @NotNull
    public final Map<String, Data> component1() {
        return this.modified;
    }

    @NotNull
    public final Set<String> component2() {
        return this.drops;
    }

    @NotNull
    public final ChangeTracker copy(@NotNull Map<String, ? extends Data> modified, @NotNull Set<String> drops) {
        Intrinsics.checkNotNullParameter(modified, (String)"modified");
        Intrinsics.checkNotNullParameter(drops, (String)"drops");
        return new ChangeTracker(modified, drops);
    }

    public static /* synthetic */ ChangeTracker copy$default(ChangeTracker changeTracker, Map map, Set set2, int n, Object object) {
        if ((n & 1) != 0) {
            map = changeTracker.modified;
        }
        if ((n & 2) != 0) {
            set2 = changeTracker.drops;
        }
        return changeTracker.copy(map, set2);
    }

    @NotNull
    public String toString() {
        return "ChangeTracker(modified=" + this.modified + ", drops=" + this.drops + ')';
    }

    public int hashCode() {
        int result = ((Object)this.modified).hashCode();
        result = result * 31 + ((Object)this.drops).hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangeTracker)) {
            return false;
        }
        ChangeTracker changeTracker = (ChangeTracker)other;
        if (!Intrinsics.areEqual(this.modified, changeTracker.modified)) {
            return false;
        }
        return Intrinsics.areEqual(this.drops, changeTracker.drops);
    }
}

