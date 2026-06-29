/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerRespawnEvent
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u91cd\u751f\u76ee\u6807", description={"\u73a9\u5bb6\u91cd\u751f", "\u652f\u6301\u91cd\u751f\u4f4d\u7f6e\u3001\u91cd\u751f\u7c7b\u578b\uff08\u5e8a/\u91cd\u751f\u951a\uff09\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u91cd\u751f\u6b21\u6570"}, alias={"\u91cd\u751f", "\u590d\u6d3b", "\u590d\u751f"}, params={@ParamInfo(name="position", type="Location", description="\u91cd\u751f\u4f4d\u7f6e"), @ParamInfo(name="spawn:bed", type="Boolean", description="\u662f\u5426\u5728\u5e8a\u4e0a\u91cd\u751f"), @ParamInfo(name="spawn:anchor", type="Boolean", description="\u662f\u5426\u5728\u91cd\u751f\u951a\u4e0a\u91cd\u751f")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerRespawn;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerRespawnEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerRespawn
extends ObjectiveCountableI<PlayerRespawnEvent> {
    @NotNull
    public static final IPlayerRespawn INSTANCE = new IPlayerRespawn();
    @NotNull
    private static final String name = "player respawn";

    private IPlayerRespawn() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerRespawnEvent> getEvent() {
        return PlayerRespawnEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerRespawnEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerRespawnEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getRespawnLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerRespawnEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.isBedSpawn();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerRespawnEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.isAnchorSpawn();
    }

    static {
        INSTANCE.handler(IPlayerRespawn::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerRespawn::_init_$lambda$1);
        INSTANCE.addCondition("spawn:bed", "Boolean", IPlayerRespawn::_init_$lambda$2);
        INSTANCE.addCondition("spawn:anchor", "Boolean", IPlayerRespawn::_init_$lambda$3);
    }
}

