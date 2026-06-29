/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.taboolib.common5.RealTime;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u000f\b&\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bR\u0012\u0010\t\u001a\u00020\nX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\fR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/core/quest/addon/data/PlanType;", "", "realTime", "Link/ptms/chemdah/taboolib/common5/RealTime;", "unit", "Link/ptms/chemdah/taboolib/common5/RealTime$Type;", "value", "", "(Link/ptms/chemdah/taboolib/common5/RealTime;Link/ptms/chemdah/taboolib/common5/RealTime$Type;I)V", "nextTime", "", "getNextTime", "()J", "getRealTime", "()Link/ptms/chemdah/taboolib/common5/RealTime;", "setRealTime", "(Link/ptms/chemdah/taboolib/common5/RealTime;)V", "getUnit", "()Link/ptms/chemdah/taboolib/common5/RealTime$Type;", "setUnit", "(Link/ptms/chemdah/taboolib/common5/RealTime$Type;)V", "getValue", "()I", "setValue", "(I)V", "Chemdah"})
public abstract class PlanType {
    @NotNull
    private RealTime realTime;
    @NotNull
    private RealTime.Type unit;
    private int value;

    public PlanType(@NotNull RealTime realTime, @NotNull RealTime.Type unit, int value2) {
        Intrinsics.checkNotNullParameter((Object)realTime, (String)"realTime");
        Intrinsics.checkNotNullParameter((Object)unit, (String)"unit");
        this.realTime = realTime;
        this.unit = unit;
        this.value = value2;
    }

    @NotNull
    public final RealTime getRealTime() {
        return this.realTime;
    }

    public final void setRealTime(@NotNull RealTime realTime) {
        Intrinsics.checkNotNullParameter((Object)realTime, (String)"<set-?>");
        this.realTime = realTime;
    }

    @NotNull
    public final RealTime.Type getUnit() {
        return this.unit;
    }

    public final void setUnit(@NotNull RealTime.Type type) {
        Intrinsics.checkNotNullParameter((Object)type, (String)"<set-?>");
        this.unit = type;
    }

    public final int getValue() {
        return this.value;
    }

    public final void setValue(int n) {
        this.value = n;
    }

    public abstract long getNextTime();
}

