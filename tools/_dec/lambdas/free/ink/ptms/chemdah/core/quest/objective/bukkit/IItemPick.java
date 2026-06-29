/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityPickupItemEvent
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u7269\u54c1\u62fe\u53d6\u76ee\u6807", description={"\u73a9\u5bb6\u62fe\u53d6\u6389\u843d\u7269\u65f6\u89e6\u53d1", "\u81ea\u52a8\u8ba1\u7b97\u62fe\u53d6\u7269\u54c1\u7684\u5806\u53e0\u6570\u91cf", "\u652f\u6301\u7269\u54c1\u3001\u6570\u91cf\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad"}, alias={"\u62fe\u53d6", "\u6361\u8d77\u7269\u54c1", "\u6536\u96c6\u7269\u54c1"}, params={@ParamInfo(name="position", type="Location", description="\u62fe\u53d6\u65f6\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u62fe\u53d6\u7684\u7269\u54c1"), @ParamInfo(name="amount", type="Number", description="\u62fe\u53d6\u7269\u54c1\u7684\u6570\u91cf")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemPick;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityPickupItemEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IItemPick
extends ObjectiveCountableI<EntityPickupItemEvent> {
    @NotNull
    public static final IItemPick INSTANCE = new IItemPick();
    @NotNull
    private static final String name = "pickup item";

    private IItemPick() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityPickupItemEvent> getEvent() {
        return EntityPickupItemEvent.class;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull EntityPickupItemEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getItem().getItemStack().getAmount();
    }

    private static final Player _init_$lambda$0(EntityPickupItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityPickupItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityPickupItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityPickupItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack().getAmount();
    }

    private static final Object _init_$lambda$4(EntityPickupItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem().getItemStack().getAmount();
    }

    static {
        INSTANCE.handler(IItemPick::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemPick::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IItemPick::_init_$lambda$2);
        INSTANCE.addCondition("amount", "Number", IItemPick::_init_$lambda$3);
        INSTANCE.addConditionVariable("amount", IItemPick::_init_$lambda$4);
    }
}

