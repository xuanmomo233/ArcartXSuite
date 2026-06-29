/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.PlanType;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\t\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\bR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u0011\u0010\r\u001a\u00020\u00078F\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\u000fR\u001c\u0010\u0006\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u000f\"\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0013\u001a\u00020\u00148F\u00a2\u0006\u0006\u001a\u0004\b\u0015\u0010\u0016R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001a\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/quest/addon/data/Plan;", "", "type", "Link/ptms/chemdah/core/quest/addon/data/PlanType;", "count", "", "group", "", "(Link/ptms/chemdah/core/quest/addon/data/PlanType;ILjava/lang/String;)V", "getCount", "()I", "setCount", "(I)V", "debug", "getDebug", "()Ljava/lang/String;", "getGroup", "setGroup", "(Ljava/lang/String;)V", "nextTime", "", "getNextTime", "()J", "getType", "()Link/ptms/chemdah/core/quest/addon/data/PlanType;", "setType", "(Link/ptms/chemdah/core/quest/addon/data/PlanType;)V", "Chemdah"})
public final class Plan {
    @NotNull
    private PlanType type;
    private int count;
    @Nullable
    private String group;

    public Plan(@NotNull PlanType type, int count, @Nullable String group2) {
        Intrinsics.checkNotNullParameter((Object)type, (String)"type");
        this.type = type;
        this.count = count;
        this.group = group2;
    }

    @NotNull
    public final PlanType getType() {
        return this.type;
    }

    public final void setType(@NotNull PlanType planType) {
        Intrinsics.checkNotNullParameter((Object)planType, (String)"<set-?>");
        this.type = planType;
    }

    public final int getCount() {
        return this.count;
    }

    public final void setCount(int n) {
        this.count = n;
    }

    @Nullable
    public final String getGroup() {
        return this.group;
    }

    public final void setGroup(@Nullable String string) {
        this.group = string;
    }

    public final long getNextTime() {
        return this.type.getNextTime();
    }

    @NotNull
    public final String getDebug() {
        return "Plan(type=" + this.type + ", count=" + this.count + ", group=" + this.group + ')';
    }
}

