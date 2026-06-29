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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\r\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\u0007\u0012\u0006\u0010\n\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u000bJ\b\u0010\u0018\u001a\u00020\u0019H\u0016R\u001a\u0010\b\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u001a\u0010\t\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\r\"\u0004\b\u0011\u0010\u000fR\u001a\u0010\n\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\r\"\u0004\b\u0013\u0010\u000fR\u0014\u0010\u0014\u001a\u00020\u00158VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/core/quest/addon/data/PlanTypeWeekly;", "Link/ptms/chemdah/core/quest/addon/data/PlanType;", "realTime", "Link/ptms/chemdah/taboolib/common5/RealTime;", "unit", "Link/ptms/chemdah/taboolib/common5/RealTime$Type;", "value", "", "day", "hour", "minute", "(Link/ptms/chemdah/taboolib/common5/RealTime;Link/ptms/chemdah/taboolib/common5/RealTime$Type;IIII)V", "getDay", "()I", "setDay", "(I)V", "getHour", "setHour", "getMinute", "setMinute", "nextTime", "", "getNextTime", "()J", "toString", "", "Chemdah"})
public class PlanTypeWeekly
extends PlanType {
    private int day;
    private int hour;
    private int minute;

    public PlanTypeWeekly(@NotNull RealTime realTime, @NotNull RealTime.Type unit, int value2, int day, int hour, int minute) {
        Intrinsics.checkNotNullParameter((Object)realTime, (String)"realTime");
        Intrinsics.checkNotNullParameter((Object)unit, (String)"unit");
        super(realTime, unit, value2);
        this.day = day;
        this.hour = hour;
        this.minute = minute;
    }

    public final int getDay() {
        return this.day;
    }

    public final void setDay(int n) {
        this.day = n;
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
        $this$_get_nextTime__u24lambda_u240.setTimeInMillis(this.getRealTime().nextTime(RealTime.Type.WEEK, this.getValue()));
        $this$_get_nextTime__u24lambda_u240.add(7, this.day);
        $this$_get_nextTime__u24lambda_u240.add(10, this.hour);
        $this$_get_nextTime__u24lambda_u240.add(12, this.minute);
        return $this$_get_nextTime__u24lambda_u240.getTimeInMillis();
    }

    @NotNull
    public String toString() {
        return "PlanTypeWeekly(realTime=" + this.getRealTime() + ", unit=" + this.getUnit() + ", value=" + this.getValue() + ", day=" + this.day + ", hour=" + this.hour + ", minute=" + this.minute + ')';
    }
}

