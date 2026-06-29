/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerPickupArrowEvent
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
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u62fe\u53d6\u7bad\u77e2\u76ee\u6807", description={"\u73a9\u5bb6\u62fe\u53d6\u7bad\u77e2\u65f6\u89e6\u53d1", "\u652f\u6301\u7bad\u77e2\u7c7b\u578b\u3001\u6570\u91cf\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7d2f\u8ba1\u62fe\u53d6\u6570\u91cf"}, alias={"\u62fe\u53d6\u7bad", "\u6361\u7bad", "\u6536\u96c6\u7bad\u77e2"}, params={@ParamInfo(name="position", type="location", description="\u62fe\u53d6\u7bad\u77e2\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="itemstack", description="\u62fe\u53d6\u7684\u7bad\u77e2"), @ParamInfo(name="amount", type="number", description="\u62fe\u53d6\u7bad\u77e2\u7684\u6570\u91cf")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemPickArrow;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerPickupArrowEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IItemPickArrow
extends ObjectiveCountableI<PlayerPickupArrowEvent> {
    @NotNull
    public static final IItemPickArrow INSTANCE = new IItemPickArrow();
    @NotNull
    private static final String name = "pickup arrow";

    private IItemPickArrow() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerPickupArrowEvent> getEvent() {
        return PlayerPickupArrowEvent.class;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull PlayerPickupArrowEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getItem().getItemStack().getAmount();
    }

    private static final Player _init_$lambda$0(PlayerPickupArrowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerPickupArrowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerPickupArrowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerPickupArrowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack().getAmount();
    }

    private static final Object _init_$lambda$4(PlayerPickupArrowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack().getAmount();
    }

    static {
        INSTANCE.handler(IItemPickArrow::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemPickArrow::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IItemPickArrow::_init_$lambda$2);
        INSTANCE.addCondition("amount", "Number", IItemPickArrow::_init_$lambda$3);
        INSTANCE.addConditionVariable("amount", IItemPickArrow::_init_$lambda$4);
    }
}

