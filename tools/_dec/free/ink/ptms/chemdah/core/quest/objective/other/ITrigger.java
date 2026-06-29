/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u89e6\u53d1\u5668\u76ee\u6807", description={"\u901a\u8fc7\u5916\u90e8\u547d\u4ee4\u6216\u811a\u672c\u624b\u52a8\u89e6\u53d1", "\u652f\u6301\u81ea\u5b9a\u4e49\u89e6\u53d1\u503c\u5339\u914d", "\u7528\u4e8e\u4e0e\u5176\u4ed6\u7cfb\u7edf\u8054\u52a8"}, alias={"trigger", "\u89e6\u53d1", "\u624b\u52a8\u89e6\u53d1"}, params={@ParamInfo(name="position", type="location", description="\u68c0\u6d4b\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="value", type="string", description="\u89e6\u53d1\u503c\uff0c\u652f\u6301\u591a\u4e2a\u503c\u7528\u9017\u53f7\u5206\u9694")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0014\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\f0\u00102\u0006\u0010\u0011\u001a\u00020\u0012R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\nR\u0014\u0010\u000b\u001a\u00020\fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/other/ITrigger;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "isListener", "", "()Z", "name", "", "getName", "()Ljava/lang/String;", "getValues", "", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class ITrigger
extends ObjectiveCountableI<Event> {
    @NotNull
    public static final ITrigger INSTANCE = new ITrigger();
    @NotNull
    private static final String name = "trigger";
    private static final boolean isListener;

    private ITrigger() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<Event> getEvent() {
        return Event.class;
    }

    @Override
    public boolean isListener() {
        return isListener;
    }

    @NotNull
    public final List<String> getValues(@NotNull Task task) {
        Object object;
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Object object2 = task.getCondition().get("value");
        if (object2 == null) {
            object2 = object = task.getCondition().get("values");
        }
        if (object2 == null || (object = ((Data)object).asList()) == null) {
            object = CollectionsKt.emptyList();
        }
        return object;
    }

    private static final Object _init_$lambda$0(PlayerProfile profile, Task task, Event event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)event, (String)"<anonymous parameter 2>");
        return profile.getPlayer().getLocation();
    }

    static {
        INSTANCE.addCondition("position", "Location", ITrigger::_init_$lambda$0);
    }
}

