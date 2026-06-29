/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0014\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\f0\u00102\u0006\u0010\u0011\u001a\u00020\u0012R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\nR\u0014\u0010\u000b\u001a\u00020\fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/other/ITrigger;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "isListener", "", "()Z", "name", "", "getName", "()Ljava/lang/String;", "getValues", "", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class ITrigger
extends ObjectiveCountableI<Event> {
    @NotNull
    public static final ITrigger INSTANCE = new ITrigger();
    @NotNull
    private static final String name = "trigger";
    @NotNull
    private static final Class<Event> event = Event.class;
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
        return event;
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

    private static final Boolean _init_$lambda$0(PlayerProfile profile, Task task, Event event) {
        Data data2 = task.getCondition().get("position");
        Intrinsics.checkNotNull((Object)data2);
        InferArea inferArea = data2.toPosition();
        Location location = profile.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"profile.player.location");
        return inferArea.inside(location);
    }

    static {
        INSTANCE.addFullCondition("position", ITrigger::_init_$lambda$0);
    }
}

