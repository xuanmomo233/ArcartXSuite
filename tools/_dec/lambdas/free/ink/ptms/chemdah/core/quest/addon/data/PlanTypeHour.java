/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.RealTime
 *  ink.ptms.chemdah.taboolib.common5.RealTime$Type
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.PlanType;
import ink.ptms.chemdah.taboolib.common5.RealTime;
import java.util.Calendar;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\b\u0010\r\u001a\u00020\u000eH\u0016R\u0014\u0010\t\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/addon/data/PlanTypeHour;", "Link/ptms/chemdah/core/quest/addon/data/PlanType;", "realTime", "Link/ptms/chemdah/taboolib/common5/RealTime;", "unit", "Link/ptms/chemdah/taboolib/common5/RealTime$Type;", "value", "", "(Link/ptms/chemdah/taboolib/common5/RealTime;Link/ptms/chemdah/taboolib/common5/RealTime$Type;I)V", "nextTime", "", "getNextTime", "()J", "toString", "", "Chemdah"})
public class PlanTypeHour
extends PlanType {
    public PlanTypeHour(@NotNull RealTime realTime, @NotNull RealTime.Type unit, int value2) {
        Intrinsics.checkNotNullParameter((Object)realTime, (String)"realTime");
        Intrinsics.checkNotNullParameter((Object)unit, (String)"unit");
        super(realTime, unit, value2);
    }

    @Override
    public long getNextTime() {
        Calendar $this$_get_nextTime__u24lambda_u240 = Calendar.getInstance();
        boolean bl = false;
        $this$_get_nextTime__u24lambda_u240.setTimeInMillis(this.getRealTime().nextTime(RealTime.Type.HOUR, this.getValue()));
        return $this$_get_nextTime__u24lambda_u240.getTimeInMillis();
    }

    @NotNull
    public String toString() {
        return "PlanTypeHour(realTime=" + this.getRealTime() + ", unit=" + this.getUnit() + ", value=" + this.getValue() + ')';
    }
}

