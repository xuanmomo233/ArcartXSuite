/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.Objective;
import kotlin.Metadata;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u6c38\u4e0d\u5b8c\u6210\u76ee\u6807", description={"\u6c38\u8fdc\u65e0\u6cd5\u5b8c\u6210\u7684\u76ee\u6807", "\u7528\u4e8e\u5360\u4f4d\u6216\u7279\u6b8a\u903b\u8f91", "\u76ee\u6807\u59cb\u7ec8\u8fd4\u56de false"}, alias={"never", "\u6c38\u4e0d", "\u5360\u4f4d\u76ee\u6807"}, params={@ParamInfo(name="null", type="string", description="\u5360\u4f4d\u53c2\u6570\uff0c\u65e0\u5b9e\u9645\u4f5c\u7528")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\nR\u0014\u0010\u000b\u001a\u00020\fX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/objective/other/INever;", "Link/ptms/chemdah/core/quest/objective/Objective;", "Lorg/bukkit/event/Event;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "isListener", "", "()Z", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class INever
extends Objective<Event> {
    @NotNull
    public static final INever INSTANCE = new INever();
    @NotNull
    private static final String name = "never";
    private static final boolean isListener;

    private INever() {
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

    private static final Boolean _init_$lambda$0(PlayerProfile playerProfile2, Task task) {
        return false;
    }

    static {
        INSTANCE.addGoal("null", INever::_init_$lambda$0);
    }
}

