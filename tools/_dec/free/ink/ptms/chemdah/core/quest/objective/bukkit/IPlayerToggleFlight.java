/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerToggleFlightEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u98de\u884c\u72b6\u6001\u5207\u6362\u76ee\u6807", description={"\u73a9\u5bb6\u5207\u6362\u98de\u884c\u72b6\u6001\u65f6\u89e6\u53d1", "\u652f\u6301\u98de\u884c\u72b6\u6001\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u5207\u6362\u6b21\u6570"}, alias={"\u98de\u884c", "\u5207\u6362\u98de\u884c", "\u8d77\u98de"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="flying", type="Boolean", description="\u662f\u5426\u5f00\u59cb\u98de\u884c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerToggleFlight;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerToggleFlightEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerToggleFlight
extends ObjectiveCountableI<PlayerToggleFlightEvent> {
    @NotNull
    public static final IPlayerToggleFlight INSTANCE = new IPlayerToggleFlight();
    @NotNull
    private static final String name = "player toggle flight";

    private IPlayerToggleFlight() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerToggleFlightEvent> getEvent() {
        return PlayerToggleFlightEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerToggleFlightEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerToggleFlightEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerToggleFlightEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.isFlying();
    }

    static {
        INSTANCE.handler(IPlayerToggleFlight::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerToggleFlight::_init_$lambda$1);
        INSTANCE.addCondition("flying", "Boolean", IPlayerToggleFlight::_init_$lambda$2);
    }
}

