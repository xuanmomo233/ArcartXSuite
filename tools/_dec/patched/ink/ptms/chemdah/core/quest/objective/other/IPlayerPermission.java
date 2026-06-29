/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u6743\u9650\u68c0\u6d4b\u76ee\u6807", description={"\u68c0\u6d4b\u73a9\u5bb6\u662f\u5426\u62e5\u6709\u6307\u5b9a\u6743\u9650", "\u6301\u7eed\u5b9a\u65f6\u68c0\u6d4b\u6743\u9650\u72b6\u6001", "\u652f\u6301\u6743\u9650\u52a8\u6001\u53d8\u5316\u7684\u573a\u666f"}, alias={"player permission", "\u6743\u9650", "\u6743\u9650\u68c0\u6d4b"}, params={@ParamInfo(name="position", type="location", description="\u68c0\u6d4b\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="permission", type="string", required=true, description="\u68c0\u6d4b\u6743\u9650\u8282\u70b9\uff0c\u5982 'chemdah.admin'")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\nR\u0014\u0010\u000b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0014\u0010\f\u001a\u00020\rX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/objective/other/IPlayerPermission;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "isListener", "", "()Z", "isTickable", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerPermission
extends ObjectiveCountableI<Event> {
    @NotNull
    public static final IPlayerPermission INSTANCE = new IPlayerPermission();
    @NotNull
    private static final String name = "player permission";
    private static final boolean isListener;
    private static final boolean isTickable;

    private IPlayerPermission() {
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

    @Override
    public boolean isTickable() {
        return isTickable;
    }

    private static final Object _init_$lambda$0(PlayerProfile profile, Task task, Event event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)event, (String)"<anonymous parameter 2>");
        return profile.getPlayer().getLocation();
    }

    private static final Boolean _init_$lambda$1(PlayerProfile profile, Task task) {
        return profile.getPlayer().hasPermission(String.valueOf(task.getCondition().get("permission")));
    }

    static {
        isTickable = true;
        INSTANCE.addCondition("position", "Location", IPlayerPermission::_init_$lambda$0);
        INSTANCE.addGoal("permission", "String", IPlayerPermission::_init_$lambda$1);
    }
}

