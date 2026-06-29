/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.nms.ItemTag
 *  io.netty.util.internal.ConcurrentSet
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.database.ChangeTracker;
import ink.ptms.chemdah.taboolib.module.nms.ItemTag;
import io.netty.util.internal.ConcurrentSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000n\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010#\n\u0002\u0010'\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010&\n\u0002\b\u0005\n\u0002\u0010 \n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010$\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b&\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u0014\u001a\u00020\u0015H&J\u0010\u0010\u0016\u001a\u00020\u000e2\u0006\u0010\u0017\u001a\u00020\u0007H&J\u0010\u0010\u0018\u001a\u00020\u000e2\u0006\u0010\u0019\u001a\u00020\u0001H&J\b\u0010\u001a\u001a\u00020\u0000H&J\u001a\u0010\u001b\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u001e0\u001d0\u001cH&J\b\u0010\u001f\u001a\u00020 H&J\"\u0010!\u001a\u00020\u00152\u0018\u0010\"\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u001e0$0#H&J\u0013\u0010%\u001a\u0004\u0018\u00010\u001e2\u0006\u0010\u0017\u001a\u00020\u0007H\u00a6\u0002J\u0019\u0010%\u001a\u00020\u001e2\u0006\u0010\u0017\u001a\u00020\u00072\u0006\u0010&\u001a\u00020\u0001H\u00a6\u0002J\b\u0010'\u001a\u00020\u000eH&J\b\u0010(\u001a\u00020\u000eH&J\u000e\u0010)\u001a\b\u0012\u0004\u0012\u00020\u00070*H&J\u000e\u0010+\u001a\u00020\u00152\u0006\u0010,\u001a\u00020\u0000J\u0010\u0010-\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u0007H&J(\u0010.\u001a\u00020\u00152\u001e\u0010/\u001a\u001a\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u001e0$\u0012\u0004\u0012\u00020\u000e00H&J\u0019\u00101\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u00072\u0006\u0010\u0019\u001a\u00020\u0001H\u00a6\u0002J\b\u00102\u001a\u00020\u0007H&J\u0014\u00103\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u000104H&J\b\u00105\u001a\u000206H&J\u0014\u00107\u001a\u00020\u00152\f\u00108\u001a\b\u0012\u0004\u0012\u00020\u00000#R\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\u0004R\u0012\u0010\r\u001a\u00020\u000eX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000fR$\u0010\u0011\u001a\u00020\u000e2\u0006\u0010\u0010\u001a\u00020\u000e@DX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u000f\"\u0004\b\u0012\u0010\u0013\u00a8\u00069"}, d2={"Link/ptms/chemdah/core/DataContainer;", "", "eventFactory", "Link/ptms/chemdah/core/DataContainerEventFactory;", "(Link/ptms/chemdah/core/DataContainerEventFactory;)V", "drops", "Lio/netty/util/internal/ConcurrentSet;", "", "getDrops", "()Lio/netty/util/internal/ConcurrentSet;", "getEventFactory", "()Link/ptms/chemdah/core/DataContainerEventFactory;", "setEventFactory", "isChanged", "", "()Z", "<set-?>", "isSilence", "setSilence", "(Z)V", "clear", "", "containsKey", "key", "containsValue", "value", "copy", "entries", "", "", "Link/ptms/chemdah/core/Data;", "flush", "Link/ptms/chemdah/core/database/ChangeTracker;", "forEach", "consumer", "Ljava/util/function/Consumer;", "", "get", "def", "isEmpty", "isNotEmpty", "keys", "", "merge", "meta", "remove", "removeIf", "predicate", "Ljava/util/function/Function;", "set", "toJson", "toMap", "", "toNBT", "Link/ptms/chemdah/taboolib/module/nms/ItemTag;", "unchanged", "func", "Chemdah"})
public abstract class DataContainer {
    @NotNull
    private DataContainerEventFactory eventFactory;
    @NotNull
    private final ConcurrentSet<String> drops;
    private boolean isSilence;

    public DataContainer(@NotNull DataContainerEventFactory eventFactory) {
        Intrinsics.checkNotNullParameter((Object)eventFactory, (String)"eventFactory");
        this.eventFactory = eventFactory;
        this.drops = new ConcurrentSet();
    }

    @NotNull
    public final DataContainerEventFactory getEventFactory() {
        return this.eventFactory;
    }

    public final void setEventFactory(@NotNull DataContainerEventFactory dataContainerEventFactory) {
        Intrinsics.checkNotNullParameter((Object)dataContainerEventFactory, (String)"<set-?>");
        this.eventFactory = dataContainerEventFactory;
    }

    @NotNull
    public final ConcurrentSet<String> getDrops() {
        return this.drops;
    }

    public final boolean isSilence() {
        return this.isSilence;
    }

    protected final void setSilence(boolean bl) {
        this.isSilence = bl;
    }

    public abstract boolean isChanged();

    @Nullable
    public abstract Data get(@NotNull String var1);

    @NotNull
    public abstract Data get(@NotNull String var1, @NotNull Object var2);

    public abstract void set(@NotNull String var1, @NotNull Object var2);

    public abstract void remove(@NotNull String var1);

    public abstract void clear();

    public abstract boolean containsKey(@NotNull String var1);

    public abstract boolean containsValue(@NotNull Object var1);

    @NotNull
    public abstract Set<Map.Entry<String, Data>> entries();

    @NotNull
    public abstract List<String> keys();

    @NotNull
    public abstract DataContainer copy();

    @NotNull
    public abstract ChangeTracker flush();

    public abstract void removeIf(@NotNull Function<Map.Entry<String, Data>, Boolean> var1);

    public abstract void forEach(@NotNull Consumer<Map.Entry<String, Data>> var1);

    @NotNull
    public abstract Map<String, Object> toMap();

    @NotNull
    public abstract ItemTag toNBT();

    @NotNull
    public abstract String toJson();

    public abstract boolean isEmpty();

    public abstract boolean isNotEmpty();

    public final void merge(@NotNull DataContainer meta) {
        Intrinsics.checkNotNullParameter((Object)meta, (String)"meta");
        meta.forEach(arg_0 -> DataContainer.merge$lambda$0(this, arg_0));
    }

    public final void unchanged(@NotNull Consumer<DataContainer> func) {
        Intrinsics.checkNotNullParameter(func, (String)"func");
        this.isSilence = true;
        func.accept(this);
        this.isSilence = false;
    }

    private static final void merge$lambda$0(DataContainer this$0, Map.Entry entry) {
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        Intrinsics.checkNotNullParameter((Object)entry, (String)"<name for destructuring parameter 0>");
        String key = (String)entry.getKey();
        Data data2 = (Data)entry.getValue();
        this$0.set(key, data2.getData());
    }
}

