/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerRecipeDiscoverEvent
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
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u914d\u65b9\u53d1\u73b0\u76ee\u6807", description={"\u73a9\u5bb6\u89e3\u9501\u65b0\u914d\u65b9\u65f6\u89e6\u53d1", "\u652f\u6301\u914d\u65b9\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u89e3\u9501\u6570\u91cf"}, alias={"\u89e3\u9501\u914d\u65b9", "\u53d1\u73b0\u914d\u65b9", "\u5b66\u4e60\u914d\u65b9"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="recipe", type="String", description="\u914d\u65b9ID")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerRecipeDiscover;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerRecipeDiscoverEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerRecipeDiscover
extends ObjectiveCountableI<PlayerRecipeDiscoverEvent> {
    @NotNull
    public static final IPlayerRecipeDiscover INSTANCE = new IPlayerRecipeDiscover();
    @NotNull
    private static final String name = "recipe discover";

    private IPlayerRecipeDiscover() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerRecipeDiscoverEvent> getEvent() {
        return PlayerRecipeDiscoverEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerRecipeDiscoverEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerRecipeDiscoverEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerRecipeDiscoverEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getRecipe().toString();
    }

    static {
        INSTANCE.handler(IPlayerRecipeDiscover::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerRecipeDiscover::_init_$lambda$1);
        INSTANCE.addCondition("recipe", "String", IPlayerRecipeDiscover::_init_$lambda$2);
    }
}

