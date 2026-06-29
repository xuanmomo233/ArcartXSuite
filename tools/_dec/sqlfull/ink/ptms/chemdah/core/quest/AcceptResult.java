/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\b\u0086\b\u0018\u00002\u00020\u0001:\u0001\u0015B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\r\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003J\u001f\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0006H\u00d6\u0001R\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0016"}, d2={"Link/ptms/chemdah/core/quest/AcceptResult;", "", "type", "Link/ptms/chemdah/core/quest/AcceptResult$Type;", "(Link/ptms/chemdah/core/quest/AcceptResult$Type;)V", "reason", "", "(Link/ptms/chemdah/core/quest/AcceptResult$Type;Ljava/lang/String;)V", "getReason", "()Ljava/lang/String;", "getType", "()Link/ptms/chemdah/core/quest/AcceptResult$Type;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "Type", "Chemdah"})
public final class AcceptResult {
    @NotNull
    private final Type type;
    @Nullable
    private final String reason;

    public AcceptResult(@NotNull Type type, @Nullable String reason) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        this.type = type;
        this.reason = reason;
    }

    @NotNull
    public final Type getType() {
        return this.type;
    }

    @Nullable
    public final String getReason() {
        return this.reason;
    }

    public AcceptResult(@NotNull Type type) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        this(type, null);
    }

    @NotNull
    public final Type component1() {
        return this.type;
    }

    @Nullable
    public final String component2() {
        return this.reason;
    }

    @NotNull
    public final AcceptResult copy(@NotNull Type type, @Nullable String reason) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        return new AcceptResult(type, reason);
    }

    public static /* synthetic */ AcceptResult copy$default(AcceptResult acceptResult, Type type, String string, int n, Object object) {
        if ((n & 1) != 0) {
            type = acceptResult.type;
        }
        if ((n & 2) != 0) {
            string = acceptResult.reason;
        }
        return acceptResult.copy(type, string);
    }

    @NotNull
    public String toString() {
        return "AcceptResult(type=" + (Object)((Object)this.type) + ", reason=" + this.reason + ')';
    }

    public int hashCode() {
        int result = this.type.hashCode();
        result = result * 31 + (this.reason == null ? 0 : this.reason.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AcceptResult)) {
            return false;
        }
        AcceptResult acceptResult = (AcceptResult)other;
        if (this.type != acceptResult.type) {
            return false;
        }
        return Intrinsics.areEqual((Object)this.reason, (Object)acceptResult.reason);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\t\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\t\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/core/quest/AcceptResult$Type;", "", "(Ljava/lang/String;I)V", "ALREADY_EXISTS", "CANCELLED", "CANCELLED_BY_CONTROL", "CANCELLED_BY_AGENT", "CANCELLED_BY_EVENT", "SUCCESSFUL", "FAILED", "Chemdah"})
    public static final class Type
    extends Enum<Type> {
        public static final /* enum */ Type ALREADY_EXISTS = new Type();
        public static final /* enum */ Type CANCELLED = new Type();
        public static final /* enum */ Type CANCELLED_BY_CONTROL = new Type();
        public static final /* enum */ Type CANCELLED_BY_AGENT = new Type();
        public static final /* enum */ Type CANCELLED_BY_EVENT = new Type();
        public static final /* enum */ Type SUCCESSFUL = new Type();
        public static final /* enum */ Type FAILED = new Type();
        private static final /* synthetic */ Type[] $VALUES;

        public static Type[] values() {
            return (Type[])$VALUES.clone();
        }

        public static Type valueOf(String value2) {
            return Enum.valueOf(Type.class, value2);
        }

        static {
            $VALUES = typeArray = new Type[]{Type.ALREADY_EXISTS, Type.CANCELLED, Type.CANCELLED_BY_CONTROL, Type.CANCELLED_BY_AGENT, Type.CANCELLED_BY_EVENT, Type.SUCCESSFUL, Type.FAILED};
        }
    }
}

