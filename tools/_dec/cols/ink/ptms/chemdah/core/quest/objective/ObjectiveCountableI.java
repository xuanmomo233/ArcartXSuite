/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Abstract
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004J%\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00028\u0000H\u0016\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J(\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u000b\u001a\u00020\u0002H\u0016\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "E", "", "Link/ptms/chemdah/core/quest/objective/Objective;", "()V", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)I", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "onContinue", "", "quest", "Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nObjectiveCountableI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ObjectiveCountableI.kt\nink/ptms/chemdah/core/quest/objective/ObjectiveCountableI\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,46:1\n8#2:47\n8#2:48\n8#2:49\n8#2:50\n8#2:51\n*S KotlinDebug\n*F\n+ 1 ObjectiveCountableI.kt\nink/ptms/chemdah/core/quest/objective/ObjectiveCountableI\n*L\n31#1:47\n35#1:48\n39#1:49\n22#1:50\n25#1:51\n*E\n"})
public abstract class ObjectiveCountableI<E>
extends Objective<E> {
    public ObjectiveCountableI() {
        this.addGoal("amount", ObjectiveCountableI::_init_$lambda$0);
        this.addGoalVariable("amount", ObjectiveCountableI::_init_$lambda$1);
    }

    @Override
    public void onContinue(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull Object event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        Data $this$cint$iv = task.getGoal().get("amount", 1);
        boolean $i$f$getCint = false;
        profile.dataOperator(task).add("amount", this.getCount(profile, task, event), Coerce.toInteger((Object)$this$cint$iv));
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Progress progress;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Data $this$cint$iv = task.getGoal().get("amount", 1);
        boolean $i$f$getCint = false;
        int target = Coerce.toInteger((Object)$this$cint$iv);
        if (this.hasCompletedSignature(profile, task)) {
            progress = Progress.Companion.toProgress(target, target, 1.0);
        } else {
            $this$cint$iv = profile.dataOperator(task).get("amount", 0);
            $i$f$getCint = false;
            progress = Progress.Companion.toProgress$default(Progress.Companion, Coerce.toInteger((Object)$this$cint$iv), target, 0.0, 2, null);
        }
        return progress;
    }

    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull E event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter(event, (String)"event");
        return 1;
    }

    private static final Boolean _init_$lambda$0(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cint$iv = task.getGoal().get("amount", 1);
        boolean $i$f$getCint = false;
        return profile.dataOperator(task).more("amount", Coerce.toInteger((Object)$this$cint$iv));
    }

    private static final Object _init_$lambda$1(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cint$iv = profile.dataOperator(task).get("amount", 0);
        boolean $i$f$getCint = false;
        return Coerce.toInteger((Object)$this$cint$iv);
    }
}

