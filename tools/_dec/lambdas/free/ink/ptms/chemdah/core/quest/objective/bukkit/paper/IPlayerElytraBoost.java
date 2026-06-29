/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.player.PlayerElytraBoostEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit.paper;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
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
@MetaInfo(name="\u9798\u7fc5\u52a0\u901f\u76ee\u6807", description={"\u73a9\u5bb6\u4f7f\u7528\u70df\u82b1\u706b\u7bad\u52a0\u901f\u9798\u7fc5\uff08\u9700\u8981 Paper \u670d\u52a1\u7aef\uff09", "\u652f\u6301\u7269\u54c1\u3001\u6d88\u8017\u72b6\u6001\u3001\u70df\u82b1\u5b9e\u4f53\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u52a0\u901f\u6b21\u6570"}, alias={"\u70df\u82b1\u52a0\u901f", "\u9798\u7fc5\u98de\u884c", "\u706b\u7bad\u63a8\u8fdb"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u4f7f\u7528\u7684\u70df\u82b1\u706b\u7bad\u7269\u54c1"), @ParamInfo(name="consume", type="Boolean", description="\u662f\u5426\u6d88\u8017\u4e86\u70df\u82b1\uff08\u4f7f\u7528\u4e86\u4e00\u4e2a\uff09"), @ParamInfo(name="firework", type="Entity", description="\u89e6\u53d1\u52a0\u901f\u7684\u70df\u82b1\u5b9e\u4f53")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/paper/IPlayerElytraBoost;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerElytraBoostEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerElytraBoost
extends ObjectiveCountableI<PlayerElytraBoostEvent> {
    @NotNull
    public static final IPlayerElytraBoost INSTANCE = new IPlayerElytraBoost();
    @NotNull
    private static final String name = "elytra boost";

    private IPlayerElytraBoost() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerElytraBoostEvent> getEvent() {
        return PlayerElytraBoostEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerElytraBoostEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerElytraBoostEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerElytraBoostEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItemStack();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerElytraBoostEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.shouldConsume();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerElytraBoostEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFirework();
    }

    static {
        INSTANCE.handler(IPlayerElytraBoost::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerElytraBoost::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IPlayerElytraBoost::_init_$lambda$2);
        INSTANCE.addCondition("consume", "Boolean", IPlayerElytraBoost::_init_$lambda$3);
        INSTANCE.addCondition("firework", "Entity", IPlayerElytraBoost::_init_$lambda$4);
    }
}

