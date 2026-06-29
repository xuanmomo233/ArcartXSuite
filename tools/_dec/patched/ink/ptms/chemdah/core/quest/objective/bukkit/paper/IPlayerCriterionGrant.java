/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit.paper;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
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

@Dependency(plugin="minecraft")
@MetaInfo(name="\u8fdb\u5ea6\u6807\u51c6\u8fbe\u6210\u76ee\u6807", description={"\u73a9\u5bb6\u8fbe\u6210\u8fdb\u5ea6\u6807\u51c6\u65f6\u89e6\u53d1\uff08\u9700\u8981 Paper \u670d\u52a1\u7aef\uff09", "\u652f\u6301\u8fdb\u5ea6ID\u3001\u6807\u51c6\u540d\u79f0\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u8fbe\u6210\u6b21\u6570"}, alias={"\u8fdb\u5ea6\u8fbe\u6210", "\u6210\u5c31\u6807\u51c6", "\u8fdb\u5ea6\u6761\u4ef6"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="advancement", type="String", description="\u8fdb\u5ea6\u7684\u547d\u540d\u7a7a\u95f4ID\uff08\u5982minecraft:story/mine_stone\uff09"), @ParamInfo(name="criterion", type="String", description="\u8fdb\u5ea6\u6807\u51c6\u7684\u540d\u79f0")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerCriterionGrant;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerAdvancementCriterionGrantEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerCriterionGrant
extends ObjectiveCountableI<PlayerAdvancementCriterionGrantEvent> {
    @NotNull
    public static final IPlayerCriterionGrant INSTANCE = new IPlayerCriterionGrant();
    @NotNull
    private static final String name = "criterion grant";

    private IPlayerCriterionGrant() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerAdvancementCriterionGrantEvent> getEvent() {
        return PlayerAdvancementCriterionGrantEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerAdvancementCriterionGrantEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerAdvancementCriterionGrantEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerAdvancementCriterionGrantEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAdvancement().getKey().toString();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerAdvancementCriterionGrantEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCriterion();
    }

    static {
        INSTANCE.handler(IPlayerCriterionGrant::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerCriterionGrant::_init_$lambda$1);
        INSTANCE.addCondition("advancement", "String", IPlayerCriterionGrant::_init_$lambda$2);
        INSTANCE.addCondition("criterion", "String", IPlayerCriterionGrant::_init_$lambda$3);
    }
}

