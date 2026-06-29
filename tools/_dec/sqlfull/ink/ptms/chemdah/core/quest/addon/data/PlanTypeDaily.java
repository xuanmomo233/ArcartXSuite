/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.PlanType;
import ink.ptms.chemdah.taboolib.common5.RealTime;
import java.util.Calendar;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\n\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\nJ\b\u0010\u0015\u001a\u00020\u0016H\u0016R\u001a\u0010\b\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\t\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\f\"\u0004\b\u0010\u0010\u000eR\u0014\u0010\u0011\u001a\u00020\u00128VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/addon/data/PlanTypeDaily;", "Link/ptms/chemdah/core/quest/addon/data/PlanType;", "realTime", "Link/ptms/chemdah/taboolib/common5/RealTime;", "unit", "Link/ptms/chemdah/taboolib/common5/RealTime$Type;", "value", "", "hour", "minute", "(Link/ptms/chemdah/taboolib/common5/RealTime;Link/ptms/chemdah/taboolib/common5/RealTime$Type;III)V", "getHour", "()I", "setHour", "(I)V", "getMinute", "setMinute", "nextTime", "", "getNextTime", "()J", "toString", "", "Chemdah"})
public class PlanTypeDaily
extends PlanType {
    private int hour;
    private int minute;

    public PlanTypeDaily(@NotNull RealTime realTime, @NotNull RealTime.Type unit, int value2, int hour, int minute) {
        Intrinsics.checkNotNullParameter((Object)realTime, (String)"realTime");
        Intrinsics.checkNotNullParameter((Object)unit, (String)"unit");
        super(realTime, unit, value2);
        this.hour = hour;
        this.minute = minute;
    }

    public final int getHour() {
        return this.hour;
    }

    public final void setHour(int n) {
        this.hour = n;
    }

    public final int getMinute() {
        return this.minute;
    }

    public final void setMinute(int n) {
        this.minute = n;
    }

    @Override
    public long getNextTime() {
        Calendar $this$_get_nextTime__u24lambda_u240 = Calendar.getInstance();
        boolean bl = false;
        $this$_get_nextTime__u24lambda_u240.setTimeInMillis(this.getRealTime().nextTime(RealTime.Type.DAY, this.getValue()));
        $this$_get_nextTime__u24lambda_u240.add(10, this.hour);
        $this$_get_nextTime__u24lambda_u240.add(12, this.minute);
        return $this$_get_nextTime__u24lambda_u240.getTimeInMillis();
    }

    @NotNull
    public String toString() {
        return "PlanTypeDaily(realTime=" + this.getRealTime() + ", unit=" + this.getUnit() + ", value=" + this.getValue() + ", hour=" + this.hour + ", minute=" + this.minute + ')';
    }
}

