/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customfishing.api.event.RodCastEvent
 *  net.momirealms.customfishing.api.mechanic.fishing.FishingGears
 *  net.momirealms.customfishing.api.mechanic.fishing.FishingGears$GearType
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
import ink.ptms.chemdah.core.quest.objective.xiaomomi.GearsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.momirealms.customfishing.api.event.RodCastEvent;
import net.momirealms.customfishing.api.mechanic.fishing.FishingGears;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomFishing")
@MetaInfo(source="CustomFishing", name="CustomFishing \u629b\u7aff\u76ee\u6807", description={"CustomFishing \u629b\u51fa\u9c7c\u7aff", "\u652f\u6301\u4f4d\u7f6e\u3001\u9c7c\u7aff\u3001\u9c7c\u9975\u3001\u9c7c\u94a9\u3001\u5de5\u5177\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CustomFishing \u63d2\u4ef6\u652f\u6301"}, alias={"customfishing\u629b\u7aff", "\u629b\u51fa\u9c7c\u7aff", "\u7529\u7aff\u9493\u9c7c"}, params={@ParamInfo(name="position", type="Location", description="\u629b\u7aff\u7684\u4f4d\u7f6e"), @ParamInfo(name="rod", type="ItemStack", description="\u9c7c\u7aff\u7269\u54c1"), @ParamInfo(name="bait", type="ItemStack", description="\u9c7c\u9975\u7269\u54c1"), @ParamInfo(name="hook", type="ItemStack", description="\u9c7c\u94a9\u7269\u54c1"), @ParamInfo(name="util", type="ItemStack", description="\u5de5\u5177\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CFRodCast;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customfishing/api/event/RodCastEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CFRodCast
extends ObjectiveCountableI<RodCastEvent> {
    @NotNull
    public static final CFRodCast INSTANCE = new CFRodCast();
    @NotNull
    private static final String name = "customfishing rod cast";

    private CFRodCast() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<RodCastEvent> getEvent() {
        return RodCastEvent.class;
    }

    private static final Player _init_$lambda$0(RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBukkitPlayerFishEvent().getHook().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        FishingGears fishingGears = it.getGears();
        Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"it.gears");
        FishingGears.GearType gearType = FishingGears.GearType.ROD;
        Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"ROD");
        return GearsKt.getItems(fishingGears, gearType);
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        FishingGears fishingGears = it.getGears();
        Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"it.gears");
        FishingGears.GearType gearType = FishingGears.GearType.BAIT;
        Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"BAIT");
        return GearsKt.getItems(fishingGears, gearType);
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        FishingGears fishingGears = it.getGears();
        Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"it.gears");
        FishingGears.GearType gearType = FishingGears.GearType.HOOK;
        Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"HOOK");
        return GearsKt.getItems(fishingGears, gearType);
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, RodCastEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        FishingGears fishingGears = it.getGears();
        Intrinsics.checkNotNullExpressionValue((Object)fishingGears, (String)"it.gears");
        FishingGears.GearType gearType = FishingGears.GearType.UTIL;
        Intrinsics.checkNotNullExpressionValue((Object)gearType, (String)"UTIL");
        return GearsKt.getItems(fishingGears, gearType);
    }

    static {
        INSTANCE.handler(CFRodCast::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CFRodCast::_init_$lambda$1);
        INSTANCE.addCondition("rod", "ItemStack", CFRodCast::_init_$lambda$2);
        INSTANCE.addCondition("bait", "ItemStack", CFRodCast::_init_$lambda$3);
        INSTANCE.addCondition("hook", "ItemStack", CFRodCast::_init_$lambda$4);
        INSTANCE.addCondition("util", "ItemStack", CFRodCast::_init_$lambda$5);
    }
}

