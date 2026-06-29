/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core;

import ink.ptms.chemdah.core.quest.ConditionNumber;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.core.quest.selector.InferEntity;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\n\n\u0000\n\u0002\u0010\u0005\n\u0000\n\u0002\u0010\u000b\n\u0002\b\r\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0016\u0018\u0000 52\u00020\u0001:\u00015B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bB\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\t\u00a2\u0006\u0002\u0010\nB\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fB\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\r\u00a2\u0006\u0002\u0010\u000eB\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u000f\u00a2\u0006\u0002\u0010\u0010B\u000f\b\u0014\u0012\u0006\u0010\u0002\u001a\u00020\u0001\u00a2\u0006\u0002\u0010\u0011J\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001dJ\u0013\u0010\u001f\u001a\u00020\u000f2\b\u0010 \u001a\u0004\u0018\u00010\u0001H\u0096\u0002J\b\u0010!\u001a\u00020\u0003H\u0016J\u0006\u0010\"\u001a\u00020\u000fJ\u0006\u0010#\u001a\u00020\rJ\u0006\u0010$\u001a\u00020%J\u0006\u0010&\u001a\u00020\u0007J\u0006\u0010'\u001a\u00020\u0005J\u0006\u0010(\u001a\u00020)J\u0006\u0010*\u001a\u00020+J\u0006\u0010,\u001a\u00020-J\u0006\u0010.\u001a\u00020\u0003J\u0006\u0010/\u001a\u00020\tJ\u0006\u00100\u001a\u000201J\u0006\u00102\u001a\u00020\u000bJ\b\u00103\u001a\u00020\u001eH\u0016J\u0006\u00104\u001a\u000201R\u001a\u0010\u0012\u001a\u00020\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0010R\u0011\u0010\u0016\u001a\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u001c\u0010\u0019\u001a\u0004\u0018\u00010\u0001X\u0084\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u0018\"\u0004\b\u001b\u0010\u0011\u00a8\u00066"}, d2={"Link/ptms/chemdah/core/Data;", "", "value", "", "(I)V", "", "(F)V", "", "(D)V", "", "(J)V", "", "(S)V", "", "(B)V", "", "(Z)V", "(Ljava/lang/Object;)V", "changed", "getChanged", "()Z", "setChanged", "data", "getData", "()Ljava/lang/Object;", "selfValue", "getSelfValue", "setSelfValue", "asList", "", "", "equals", "other", "hashCode", "toBoolean", "toByte", "toConditionNumber", "Link/ptms/chemdah/core/quest/ConditionNumber;", "toDouble", "toFloat", "toInferBlock", "Link/ptms/chemdah/core/quest/selector/InferBlock;", "toInferEntity", "Link/ptms/chemdah/core/quest/selector/InferEntity;", "toInferItem", "Link/ptms/chemdah/core/quest/selector/InferItem;", "toInt", "toLong", "toPosition", "Link/ptms/chemdah/core/quest/selector/InferArea;", "toShort", "toString", "toVector", "Companion", "Chemdah"})
public class Data {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final Object data;
    private boolean changed;
    @Nullable
    private Object selfValue;

    @NotNull
    public final Object getData() {
        return this.data;
    }

    public final boolean getChanged() {
        return this.changed;
    }

    public final void setChanged(boolean bl) {
        this.changed = bl;
    }

    @Nullable
    protected final Object getSelfValue() {
        return this.selfValue;
    }

    protected final void setSelfValue(@Nullable Object object) {
        this.selfValue = object;
    }

    public Data(int value2) {
        this.data = value2;
    }

    public Data(float value2) {
        this.data = Float.valueOf(value2);
    }

    public Data(double value2) {
        this.data = value2;
    }

    public Data(long value2) {
        this.data = value2;
    }

    public Data(short value2) {
        this.data = value2;
    }

    public Data(byte value2) {
        this.data = value2;
    }

    public Data(boolean value2) {
        this.data = value2;
    }

    protected Data(@NotNull Object value2) {
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        this.data = value2;
    }

    public final int toInt() {
        return Coerce.toInteger((Object)this.data);
    }

    public final float toFloat() {
        return Coerce.toFloat((Object)this.data);
    }

    public final double toDouble() {
        return Coerce.toDouble((Object)this.data);
    }

    public final long toLong() {
        return Coerce.toLong((Object)this.data);
    }

    public final short toShort() {
        return Coerce.toShort((Object)this.data);
    }

    public final byte toByte() {
        return Coerce.toByte((Object)this.data);
    }

    public final boolean toBoolean() {
        return Coerce.toBoolean((Object)this.data);
    }

    @NotNull
    public final InferArea toVector() {
        if (!(this.selfValue instanceof InferArea)) {
            this.selfValue = InferArea.Companion.toInferArea(this.toString(), true);
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.selector.InferArea");
        return (InferArea)object;
    }

    @NotNull
    public final InferArea toPosition() {
        if (!(this.selfValue instanceof InferArea)) {
            this.selfValue = InferArea.Companion.toInferArea$default(InferArea.Companion, this.toString(), false, 1, null);
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.selector.InferArea");
        return (InferArea)object;
    }

    @NotNull
    public final InferEntity toInferEntity() {
        if (!(this.selfValue instanceof InferEntity)) {
            this.selfValue = InferEntity.Companion.toInferEntity(CollectionKt.asList((Object)this.data));
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.selector.InferEntity");
        return (InferEntity)object;
    }

    @NotNull
    public final InferBlock toInferBlock() {
        if (!(this.selfValue instanceof InferBlock)) {
            this.selfValue = InferBlock.Companion.toInferBlock(CollectionKt.asList((Object)this.data));
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.selector.InferBlock");
        return (InferBlock)object;
    }

    @NotNull
    public final InferItem toInferItem() {
        if (!(this.selfValue instanceof InferItem)) {
            this.selfValue = InferItem.Companion.toInferItem(CollectionKt.asList((Object)this.data));
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.selector.InferItem");
        return (InferItem)object;
    }

    @NotNull
    public final ConditionNumber toConditionNumber() {
        if (!(this.selfValue instanceof ConditionNumber)) {
            this.selfValue = new ConditionNumber(this.data.toString());
        }
        Object object = this.selfValue;
        Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.ConditionNumber");
        return (ConditionNumber)object;
    }

    @NotNull
    public final List<String> asList() {
        return CollectionKt.asList((Object)this.data);
    }

    @NotNull
    public String toString() {
        if (this.data instanceof List && ((List)this.data).size() == 1) {
            return String.valueOf(((List)this.data).get(0));
        }
        return this.data.toString();
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Data)) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.data, (Object)((Data)other).data);
    }

    public int hashCode() {
        return this.data.hashCode();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0001\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/Data$Companion;", "", "()V", "unsafe", "Link/ptms/chemdah/core/Data;", "any", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Data unsafe(@NotNull Object any) {
            Intrinsics.checkNotNullParameter((Object)any, (String)"any");
            return new Data(any);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

