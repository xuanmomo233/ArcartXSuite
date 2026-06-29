/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityShootBowEvent
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
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u5c04\u7bad\u76ee\u6807", description={"\u73a9\u5bb6\u5c04\u7bad", "\u652f\u6301\u5f13\u7c7b\u578b\u3001\u62c9\u5f13\u529b\u5ea6\u3001\u6d88\u8017\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u5c04\u7bad\u6b21\u6570"}, alias={"\u5c04\u7bad", "\u5c04\u51fb", "\u53d1\u5c04\u5f13\u7bad"}, params={@ParamInfo(name="position", type="Location", description="\u5c04\u7bad\u4f4d\u7f6e"), @ParamInfo(name="arrow", type="Entity", description="\u53d1\u5c04\u7684\u7bad\u77e2\u5b9e\u4f53"), @ParamInfo(name="item", type="ItemStack", description="\u4f7f\u7528\u7684\u5f13"), @ParamInfo(name="item:consumed", type="ItemStack", description="\u6d88\u8017\u7684\u7269\u54c1\uff08\u5982\u7bad\u77e2\uff09"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b\uff08HAND/OFF_HAND\uff09"), @ParamInfo(name="force", type="Number", description="\u62c9\u5f13\u529b\u5ea6\uff080.0-1.0\uff09"), @ParamInfo(name="consumable", type="Boolean", description="\u662f\u5426\u6d88\u8017\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerShootBow;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityShootBowEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerShootBow
extends ObjectiveCountableI<EntityShootBowEvent> {
    @NotNull
    public static final IPlayerShootBow INSTANCE = new IPlayerShootBow();
    @NotNull
    private static final String name = "shoot bow";

    private IPlayerShootBow() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityShootBowEvent> getEvent() {
        return EntityShootBowEvent.class;
    }

    private static final Player _init_$lambda$0(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getProjectile();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getBow();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack itemStack = it.getConsumable();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        return itemStack;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getHand().name();
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return Float.valueOf(it.getForce());
    }

    private static final Object _init_$lambda$7(PlayerProfile playerProfile2, Task task, EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.shouldConsumeItem();
    }

    private static final Object _init_$lambda$8(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return Float.valueOf(it.getForce());
    }

    private static final Object _init_$lambda$9(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.shouldConsumeItem();
    }

    static {
        INSTANCE.handler(IPlayerShootBow::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerShootBow::_init_$lambda$1);
        INSTANCE.addCondition("arrow", "Entity", IPlayerShootBow::_init_$lambda$2);
        INSTANCE.addCondition("item", "ItemStack", IPlayerShootBow::_init_$lambda$3);
        INSTANCE.addCondition("item:consumed", "ItemStack", IPlayerShootBow::_init_$lambda$4);
        INSTANCE.addCondition("hand", "String", IPlayerShootBow::_init_$lambda$5);
        INSTANCE.addCondition("force", "Number", IPlayerShootBow::_init_$lambda$6);
        INSTANCE.addCondition("consumable", "Boolean", IPlayerShootBow::_init_$lambda$7);
        INSTANCE.addConditionVariable("force", IPlayerShootBow::_init_$lambda$8);
        INSTANCE.addConditionVariable("consumable", IPlayerShootBow::_init_$lambda$9);
    }
}

