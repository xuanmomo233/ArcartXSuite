/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customcrops.api.event.WateringCanWaterPotEvent
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.momirealms.customcrops.api.event.WateringCanWaterPotEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@MetaInfo(source="CustomCrops", name="CustomCrops \u6d12\u6c34\u58f6\u6d47\u704c\u82b1\u76c6\u76ee\u6807", description={"\u4f7f\u7528\u6d12\u6c34\u58f6\u6d47\u704c CustomCrops \u82b1\u76c6", "\u652f\u6301\u4f4d\u7f6e\u3001\u624b\u6301\u7269\u54c1\u3001\u82b1\u76c6\u7c7b\u578b\u3001\u6d12\u6c34\u58f6\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CustomCrops \u63d2\u4ef6\u652f\u6301"}, alias={"customcrops\u6d47\u6c34", "\u6d47\u704c\u82b1\u76c6", "\u6d12\u6c34\u58f6\u6d47\u6c34"}, params={@ParamInfo(name="position", type="Location", description="\u6d12\u6c34\u6d47\u704c\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u73a9\u5bb6\u624b\u6301\u7684\u7269\u54c1"), @ParamInfo(name="pot", type="String", description="\u82b1\u76c6\u7684 ID"), @ParamInfo(name="can", type="String", description="\u6d12\u6c34\u58f6\u7684 ID"), @ParamInfo(name="can:item", type="String", description="\u6d12\u6c34\u58f6\u7684\u7269\u54c1 ID")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCWateringCanWaterPot;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/WateringCanWaterPotEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCWateringCanWaterPot
extends ObjectiveCountableI<WateringCanWaterPotEvent> {
    @NotNull
    public static final CCWateringCanWaterPot INSTANCE = new CCWateringCanWaterPot();
    @NotNull
    private static final String name = "customcrops water pot";

    private CCWateringCanWaterPot() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<WateringCanWaterPotEvent> getEvent() {
        return WateringCanWaterPotEvent.class;
    }

    private static final Player _init_$lambda$0(WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.itemInHand();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.potConfig().id();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.wateringCanConfig().id();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.wateringCanConfig().itemID();
    }

    static {
        INSTANCE.handler(CCWateringCanWaterPot::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CCWateringCanWaterPot::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", CCWateringCanWaterPot::_init_$lambda$2);
        INSTANCE.addCondition("pot", "String", CCWateringCanWaterPot::_init_$lambda$3);
        INSTANCE.addCondition("can", "String", CCWateringCanWaterPot::_init_$lambda$4);
        INSTANCE.addCondition("can:item", "String", CCWateringCanWaterPot::_init_$lambda$5);
    }
}

