/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerBedEnterEvent
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
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u8fdb\u5165\u5e8a\u76ee\u6807", description={"\u73a9\u5bb6\u8fdb\u5165\u5e8a", "\u652f\u6301\u5e8a\u4f4d\u7f6e\u3001\u6750\u8d28\u3001\u8fdb\u5165\u539f\u56e0\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u8fdb\u5165\u6b21\u6570"}, alias={"\u4e0a\u5e8a", "\u7761\u89c9", "\u4f11\u606f"}, params={@ParamInfo(name="position", type="Location", description="\u5e8a\u7684\u4f4d\u7f6e"), @ParamInfo(name="bed", type="Block", description="\u5e8a\u7684\u65b9\u5757"), @ParamInfo(name="reason", type="String", description="\u8fdb\u5165\u5e8a\u7684\u539f\u56e0\uff08OK/NOT_POSSIBLE_HERE/NOT_POSSIBLE_NOW/TOO_FAR_AWAY\u7b49\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBedEnter;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerBedEnterEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBedEnter
extends ObjectiveCountableI<PlayerBedEnterEvent> {
    @NotNull
    public static final IPlayerBedEnter INSTANCE = new IPlayerBedEnter();
    @NotNull
    private static final String name = "bed enter";

    private IPlayerBedEnter() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerBedEnterEvent> getEvent() {
        return PlayerBedEnterEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerBedEnterEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerBedEnterEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBed().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerBedEnterEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBed();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerBedEnterEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBedEnterResult().name();
    }

    static {
        INSTANCE.handler(IPlayerBedEnter::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerBedEnter::_init_$lambda$1);
        INSTANCE.addCondition("bed", "Block", IPlayerBedEnter::_init_$lambda$2);
        INSTANCE.addCondition("reason", "String", IPlayerBedEnter::_init_$lambda$3);
    }
}

