/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u4f20\u9001\u76ee\u6807", description={"\u73a9\u5bb6\u4f20\u9001", "\u652f\u6301\u8d77\u59cb\u4f4d\u7f6e\u3001\u76ee\u6807\u4f4d\u7f6e\u3001\u4f20\u9001\u539f\u56e0\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u4f20\u9001\u6b21\u6570"}, alias={"\u4f20\u9001", "\u77ac\u79fb", "\u7a7f\u8d8a"}, params={@ParamInfo(name="position", type="Location", description="\u4f20\u9001\u540e\u7684\u4f4d\u7f6e"), @ParamInfo(name="position:to", type="Location", description="\u4f20\u9001\u7684\u76ee\u6807\u4f4d\u7f6e"), @ParamInfo(name="position:from", type="Location", description="\u4f20\u9001\u524d\u7684\u4f4d\u7f6e"), @ParamInfo(name="cause", type="String", description="\u4f20\u9001\u539f\u56e0\uff08\u5982NETHER_PORTAL\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerTeleport;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerTeleportEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerTeleport
extends ObjectiveCountableI<PlayerTeleportEvent> {
    @NotNull
    public static final IPlayerTeleport INSTANCE = new IPlayerTeleport();
    @NotNull
    private static final String name = "player teleport";

    private IPlayerTeleport() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerTeleportEvent> getEvent() {
        return PlayerTeleportEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerTeleportEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerTeleportEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        Intrinsics.checkNotNull((Object)location);
        return location;
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerTeleportEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        if (location == null) {
            location = UnitsKt.getEMPTY_LOCATION();
        }
        return location;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerTeleportEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFrom();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerTeleportEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCause().name();
    }

    static {
        INSTANCE.handler(IPlayerTeleport::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerTeleport::_init_$lambda$1);
        INSTANCE.addCondition("position:to", "Location", IPlayerTeleport::_init_$lambda$2);
        INSTANCE.addCondition("position:from", "Location", IPlayerTeleport::_init_$lambda$3);
        INSTANCE.addCondition("cause", "String", IPlayerTeleport::_init_$lambda$4);
    }
}

