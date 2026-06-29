/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  me.badbones69.crazycrates.api.events.PlayerPrizeEvent
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.crazycrates;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import me.badbones69.crazycrates.api.events.PlayerPrizeEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CrayzCrates")
@MetaInfo(source="CrazyCrates", name="CrazyCrates \u5f00\u542f\u7bb1\u5b50\u76ee\u6807", description={"\u5f00\u542f CrazyCrates \u7bb1\u5b50\u5e76\u83b7\u5f97\u5956\u52b1", "\u652f\u6301\u4f4d\u7f6e\u3001\u7bb1\u5b50\u540d\u79f0\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CrazyCrates \u63d2\u4ef6\u652f\u6301"}, alias={"crazycrates\u5f00\u7bb1", "cc\u5f00\u7bb1", "\u5f00\u542f\u5b9d\u7bb1"}, params={@ParamInfo(name="position", type="string", description="\u7bb1\u5b50\u5f00\u542f\u4f4d\u7f6e"), @ParamInfo(name="name", type="string", description="CrazyCrates\u7bb1\u5b50\u540d\u79f0")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/crazycrates/CCratesOpen;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lme/badbones69/crazycrates/api/events/PlayerPrizeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCratesOpen
extends ObjectiveCountableI<PlayerPrizeEvent> {
    @NotNull
    public static final CCratesOpen INSTANCE = new CCratesOpen();
    @NotNull
    private static final String name = "cc open";

    private CCratesOpen() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerPrizeEvent> getEvent() {
        return PlayerPrizeEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerPrizeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerPrizeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerPrizeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCrate().getName();
    }

    private static final Object _init_$lambda$3(PlayerPrizeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCrate().getName();
    }

    static {
        INSTANCE.handler(CCratesOpen::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CCratesOpen::_init_$lambda$1);
        INSTANCE.addCondition("name", "String", CCratesOpen::_init_$lambda$2);
        INSTANCE.addConditionVariable("name", CCratesOpen::_init_$lambda$3);
    }
}

