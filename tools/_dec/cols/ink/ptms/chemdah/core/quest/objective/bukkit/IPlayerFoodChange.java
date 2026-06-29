/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerFoodChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/FoodLevelChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerFoodChange
extends ObjectiveCountableI<FoodLevelChangeEvent> {
    @NotNull
    public static final IPlayerFoodChange INSTANCE = new IPlayerFoodChange();
    @NotNull
    private static final String name = "food change";
    @NotNull
    private static final Class<FoodLevelChangeEvent> event = FoodLevelChangeEvent.class;

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
        return event;
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

    private static final Boolean _init_$lambda$1(Data data2, FoodLevelChangeEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, FoodLevelChangeEvent e) {
        return data2.toConditionNumber().check(e.getFoodLevel());
    }

    private static final Boolean _init_$lambda$3(Data data2, FoodLevelChangeEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getItem();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.item ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    private static final Object _init_$lambda$4(FoodLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFoodLevel();
    }

    static {
        INSTANCE.handler(IPlayerFoodChange::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerFoodChange::_init_$lambda$1);
        INSTANCE.addSimpleCondition("amount", IPlayerFoodChange::_init_$lambda$2);
        INSTANCE.addSimpleCondition("item", IPlayerFoodChange::_init_$lambda$3);
        INSTANCE.addConditionVariable("amount", IPlayerFoodChange::_init_$lambda$4);
    }
}

