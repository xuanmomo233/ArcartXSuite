/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Control;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.taboolib.common5.TimeCycle;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\u0002\u0010\nJ\u001e\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u001f0\u001e2\u0006\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020#H\u0016J\u0018\u0010$\u001a\u00020%2\u0006\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020#H\u0016R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001c\u0010\b\u001a\u0004\u0018\u00010\tX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u001c\u0010\u0006\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u0014\u0010\u0017\u001a\u00020\u00038VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0018\u0010\u0019R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u0019\"\u0004\b\u001b\u0010\u001c\u00a8\u0006&"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlRepeat;", "Link/ptms/chemdah/core/quest/addon/data/Control;", "type", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "amount", "", "period", "Link/ptms/chemdah/taboolib/common5/TimeCycle;", "group", "", "(Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;ILink/ptms/chemdah/taboolib/common5/TimeCycle;Ljava/lang/String;)V", "getAmount", "()I", "setAmount", "(I)V", "getGroup", "()Ljava/lang/String;", "setGroup", "(Ljava/lang/String;)V", "getPeriod", "()Link/ptms/chemdah/taboolib/common5/TimeCycle;", "setPeriod", "(Link/ptms/chemdah/taboolib/common5/TimeCycle;)V", "trigger", "getTrigger", "()Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getType", "setType", "(Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;)V", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "template", "Link/ptms/chemdah/core/quest/Template;", "signature", "", "Chemdah"})
public class ControlRepeat
extends Control {
    @NotNull
    private ControlTrigger type;
    private int amount;
    @Nullable
    private TimeCycle period;
    @Nullable
    private String group;

    public ControlRepeat(@NotNull ControlTrigger type, int amount, @Nullable TimeCycle period, @Nullable String group2) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        this.type = type;
        this.amount = amount;
        this.period = period;
        this.group = group2;
    }

    @NotNull
    public final ControlTrigger getType() {
        return this.type;
    }

    public final void setType(@NotNull ControlTrigger controlTrigger) {
        Intrinsics.checkNotNullParameter((Object)((Object)controlTrigger), (String)"<set-?>");
        this.type = controlTrigger;
    }

    public final int getAmount() {
        return this.amount;
    }

    public final void setAmount(int n) {
        this.amount = n;
    }

    @Nullable
    public final TimeCycle getPeriod() {
        return this.period;
    }

    public final void setPeriod(@Nullable TimeCycle timeCycle) {
        this.period = timeCycle;
    }

    @Nullable
    public final String getGroup() {
        return this.group;
    }

    public final void setGroup(@Nullable String string) {
        this.group = string;
    }

    @Override
    @NotNull
    public ControlTrigger getTrigger() {
        return this.type;
    }

    @Override
    @NotNull
    public CompletableFuture<ControlResult> check(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        StringBuilder stringBuilder = new StringBuilder().append("quest.repeat.").append(this.group != null ? '@' + this.group : template.getId()).append('.');
        String string = this.type.name().toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        String id2 = stringBuilder.append(string).toString();
        long time = profile.getPersistentDataContainer().get(id2 + ".time", 0L).toLong();
        if (this.period != null) {
            TimeCycle timeCycle = this.period;
            Intrinsics.checkNotNull((Object)timeCycle);
            if (timeCycle.start(time).isTimeout(time)) {
                CompletableFuture<ControlResult> completableFuture = CompletableFuture.completedFuture(new ControlResult(true, "repeat"));
                Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(ControlResult(true, \"repeat\"))");
                return completableFuture;
            }
        }
        CompletableFuture<ControlResult> completableFuture = CompletableFuture.completedFuture(this.toResult(profile.getPersistentDataContainer().get(id2 + ".amount", 0).toInt() < this.amount, "repeat"));
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture((profile\u2026ount).toResult(\"repeat\"))");
        return completableFuture;
    }

    /*
     * Enabled aggressive block sorting
     */
    @Override
    public void signature(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        StringBuilder stringBuilder = new StringBuilder().append("quest.repeat.").append(this.group != null ? '@' + this.group : template.getId()).append('.');
        String string = this.type.name().toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        String id2 = stringBuilder.append(string).toString();
        long time = profile.getPersistentDataContainer().get(id2 + ".time", 0L).toLong();
        if (this.period != null) {
            TimeCycle timeCycle = this.period;
            Intrinsics.checkNotNull((Object)timeCycle);
            if (timeCycle.start(time).isTimeout(time)) {
                profile.getPersistentDataContainer().set(id2 + ".amount", 1);
                profile.getPersistentDataContainer().set(id2 + ".time", System.currentTimeMillis());
                return;
            }
        }
        profile.getPersistentDataContainer().set(id2 + ".amount", profile.getPersistentDataContainer().get(id2 + ".amount", 0).toInt() + 1);
    }
}

