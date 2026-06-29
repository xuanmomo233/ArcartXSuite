/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dre.brewery.api.events.PlayerPukeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.brewery;

import com.dre.brewery.api.events.PlayerPukeEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Brewery")
@MetaInfo(source="Brewery", name="Brewery \u5455\u5410\u76ee\u6807", description={"\u56e0\u9189\u9152\u800c\u5455\u5410", "\u652f\u6301\u4f4d\u7f6e\u3001\u5455\u5410\u6b21\u6570\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Brewery \u63d2\u4ef6\u652f\u6301"}, alias={"brewery\u5455\u5410", "\u559d\u9189\u5455\u5410", "\u9189\u9152"}, params={@ParamInfo(name="position", type="section", description="\u5455\u5410\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="count", type="section", description="\u5455\u5410\u6b21\u6570\u6761\u4ef6")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/brewery/BPuke;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/dre/brewery/api/events/PlayerPukeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class BPuke
extends ObjectiveCountableI<PlayerPukeEvent> {
    @NotNull
    public static final BPuke INSTANCE = new BPuke();
    @NotNull
    private static final String name = "brewery puke";

    private BPuke() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerPukeEvent> getEvent() {
        return PlayerPukeEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerPukeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerPukeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerPukeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCount();
    }

    private static final Object _init_$lambda$3(PlayerPukeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCount();
    }

    static {
        INSTANCE.handler(BPuke::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", BPuke::_init_$lambda$1);
        INSTANCE.addCondition("count", "Number", BPuke::_init_$lambda$2);
        INSTANCE.addConditionVariable("count", BPuke::_init_$lambda$3);
    }
}

