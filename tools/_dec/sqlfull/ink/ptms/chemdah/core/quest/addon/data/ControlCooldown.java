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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\bJ\u001e\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00170\u00162\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0016J\u0018\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0016R\u001c\u0010\u0006\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\u00038VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0013\u00a8\u0006\u001e"}, d2={"Link/ptms/chemdah/core/quest/addon/data/ControlCooldown;", "Link/ptms/chemdah/core/quest/addon/data/Control;", "type", "Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "time", "Link/ptms/chemdah/taboolib/common5/TimeCycle;", "group", "", "(Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;Link/ptms/chemdah/taboolib/common5/TimeCycle;Ljava/lang/String;)V", "getGroup", "()Ljava/lang/String;", "setGroup", "(Ljava/lang/String;)V", "getTime", "()Link/ptms/chemdah/taboolib/common5/TimeCycle;", "setTime", "(Link/ptms/chemdah/taboolib/common5/TimeCycle;)V", "trigger", "getTrigger", "()Link/ptms/chemdah/core/quest/addon/data/ControlTrigger;", "getType", "check", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/addon/data/ControlResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "template", "Link/ptms/chemdah/core/quest/Template;", "signature", "", "Chemdah"})
public class ControlCooldown
extends Control {
    @NotNull
    private final ControlTrigger type;
    @NotNull
    private TimeCycle time;
    @Nullable
    private String group;

    public ControlCooldown(@NotNull ControlTrigger type, @NotNull TimeCycle time, @Nullable String group2) {
        Intrinsics.checkNotNullParameter((Object)((Object)type), (String)"type");
        Intrinsics.checkNotNullParameter((Object)time, (String)"time");
        this.type = type;
        this.time = time;
        this.group = group2;
    }

    @NotNull
    public final ControlTrigger getType() {
        return this.type;
    }

    @NotNull
    public final TimeCycle getTime() {
        return this.time;
    }

    public final void setTime(@NotNull TimeCycle timeCycle) {
        Intrinsics.checkNotNullParameter((Object)timeCycle, (String)"<set-?>");
        this.time = timeCycle;
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
        StringBuilder stringBuilder = new StringBuilder().append("quest.cooldown.").append(this.group != null ? '@' + this.group : template.getId()).append('.');
        String string = this.type.name().toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        String id2 = stringBuilder.append(string).toString();
        long start = profile.getPersistentDataContainer().get(id2, 0L).toLong();
        CompletableFuture<ControlResult> completableFuture = CompletableFuture.completedFuture(this.toResult(this.time.start(start).isTimeout(start), "cooldown"));
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(time.sta\u2026rt).toResult(\"cooldown\"))");
        return completableFuture;
    }

    @Override
    public void signature(@NotNull PlayerProfile profile, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        StringBuilder stringBuilder = new StringBuilder().append("quest.cooldown.").append(this.group != null ? '@' + this.group : template.getId()).append('.');
        String string = this.type.name().toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
        String id2 = stringBuilder.append(string).toString();
        profile.getPersistentDataContainer().set(id2, System.currentTimeMillis());
    }
}

