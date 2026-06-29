/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityBreedEvent
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u7e41\u6b96\u52a8\u7269\u76ee\u6807", description={"\u73a9\u5bb6\u7e41\u6b96\u52a8\u7269", "\u652f\u6301\u52a8\u7269\u7c7b\u578b\u3001\u7236\u6bcd\u5b9e\u4f53\u3001\u7e41\u6b96\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7e41\u6b96\u6b21\u6570"}, alias={"\u7e41\u6b96", "\u52a8\u7269\u7e41\u6b96", "\u914d\u79cd"}, params={@ParamInfo(name="position", type="Location", description="\u7e41\u6b96\u4f4d\u7f6e"), @ParamInfo(name="entity", type="Entity", description="\u7e41\u6b96\u51fa\u7684\u5e7c\u4f53"), @ParamInfo(name="entity:father", type="Entity", description="\u7236\u4ee3\u5b9e\u4f53"), @ParamInfo(name="entity:mother", type="Entity", description="\u6bcd\u4ee3\u5b9e\u4f53"), @ParamInfo(name="item", type="ItemStack", description="\u7528\u4e8e\u7e41\u6b96\u7684\u7269\u54c1"), @ParamInfo(name="exp", type="Number", description="\u83b7\u5f97\u7684\u7ecf\u9a8c\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBreed;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityBreedEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBreed
extends ObjectiveCountableI<EntityBreedEvent> {
    @NotNull
    public static final IPlayerBreed INSTANCE = new IPlayerBreed();
    @NotNull
    private static final String name = "entity breed";

    private IPlayerBreed() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityBreedEvent> getEvent() {
        return EntityBreedEvent.class;
    }

    private static final Player _init_$lambda$0(EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getBreeder();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFather();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getMother();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getBredWith();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperience();
    }

    private static final Object _init_$lambda$7(EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperience();
    }

    static {
        INSTANCE.handler(IPlayerBreed::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerBreed::_init_$lambda$1);
        INSTANCE.addCondition("entity", "Entity", IPlayerBreed::_init_$lambda$2);
        INSTANCE.addCondition("entity:father", "Entity", IPlayerBreed::_init_$lambda$3);
        INSTANCE.addCondition("entity:mother", "Entity", IPlayerBreed::_init_$lambda$4);
        INSTANCE.addCondition("item", "ItemStack", IPlayerBreed::_init_$lambda$5);
        INSTANCE.addCondition("exp", "Number", IPlayerBreed::_init_$lambda$6);
        INSTANCE.addConditionVariable("exp", IPlayerBreed::_init_$lambda$7);
    }
}

