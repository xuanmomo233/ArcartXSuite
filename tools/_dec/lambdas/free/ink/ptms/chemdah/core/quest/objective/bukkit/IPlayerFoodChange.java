/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.FoodLevelChangeEvent
 *  org.bukkit.inventory.ItemStack
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
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u9965\u997f\u503c\u53d8\u5316\u76ee\u6807", description={"\u73a9\u5bb6\u9965\u997f\u503c\u53d8\u5316\u65f6\u89e6\u53d1", "\u652f\u6301\u9965\u997f\u503c\u3001\u98df\u7269\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7d2f\u8ba1\u9965\u997f\u503c\u53d8\u5316"}, alias={"\u9965\u997f\u503c", "\u8fdb\u98df", "\u98df\u7269"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="amount", type="Number", description="\u9965\u997f\u503c"), @ParamInfo(name="item", type="ItemStack", description="\u98df\u7269\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerFoodChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/FoodLevelChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerFoodChange
extends ObjectiveCountableI<FoodLevelChangeEvent> {
    @NotNull
    public static final IPlayerFoodChange INSTANCE = new IPlayerFoodChange();
    @NotNull
    private static final String name = "food change";

    private IPlayerFoodChange() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<FoodLevelChangeEvent> getEvent() {
        return FoodLevelChangeEvent.class;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull FoodLevelChangeEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getFoodLevel();
    }

    private static final Player _init_$lambda$0(FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        HumanEntity humanEntity = it.getEntity();
        Intrinsics.checkNotNull((Object)humanEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        return (Player)humanEntity;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFoodLevel();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getItem();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$4(FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFoodLevel();
    }

    static {
        INSTANCE.handler(IPlayerFoodChange::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerFoodChange::_init_$lambda$1);
        INSTANCE.addCondition("amount", "Number", IPlayerFoodChange::_init_$lambda$2);
        INSTANCE.addCondition("item", "ItemStack", IPlayerFoodChange::_init_$lambda$3);
        INSTANCE.addConditionVariable("amount", IPlayerFoodChange::_init_$lambda$4);
    }
}

