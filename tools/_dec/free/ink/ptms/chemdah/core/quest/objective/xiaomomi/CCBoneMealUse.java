/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customcrops.api.event.BoneMealUseEvent
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
import net.momirealms.customcrops.api.event.BoneMealUseEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@MetaInfo(source="CustomCrops", name="CustomCrops \u9aa8\u7c89\u4f7f\u7528\u76ee\u6807", description={"\u4f7f\u7528\u9aa8\u7c89\u50ac\u719f\u4f5c\u7269", "\u652f\u6301\u4f4d\u7f6e\u3001\u624b\u6301\u7269\u54c1\u3001\u9aa8\u7c89\u7c7b\u578b\u3001\u4f5c\u7269\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 CustomCrops \u63d2\u4ef6\u652f\u6301"}, alias={"customcrops\u9aa8\u7c89", "\u4f7f\u7528\u9aa8\u7c89", "\u50ac\u719f\u4f5c\u7269"}, params={@ParamInfo(name="position", type="Location", description="\u9aa8\u7c89\u4f7f\u7528\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u73a9\u5bb6\u624b\u6301\u7684\u7269\u54c1"), @ParamInfo(name="bonemeal", type="String", description="\u9aa8\u7c89\u7684 ID"), @ParamInfo(name="bonemeal:required", type="String", description="\u9aa8\u7c89\u9700\u8981\u7684\u7269\u54c1 ID"), @ParamInfo(name="bonemeal:returned", type="String", description="\u9aa8\u7c89\u8fd4\u56de\u7684\u7269\u54c1 ID"), @ParamInfo(name="crop", type="String", description="\u4f5c\u7269\u7684 ID"), @ParamInfo(name="crop:seed", type="String", description="\u4f5c\u7269\u7684\u79cd\u5b50 ID"), @ParamInfo(name="block", type="String", description="\u65b9\u5757\u7684\u5b8c\u6574\u4fe1\u606f"), @ParamInfo(name="block:type", type="String", description="\u65b9\u5757\u7684\u7c7b\u578b")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCBoneMealUse;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/BoneMealUseEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCBoneMealUse
extends ObjectiveCountableI<BoneMealUseEvent> {
    @NotNull
    public static final CCBoneMealUse INSTANCE = new CCBoneMealUse();
    @NotNull
    private static final String name = "customcrops bonemeal use";

    private CCBoneMealUse() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BoneMealUseEvent> getEvent() {
        return BoneMealUseEvent.class;
    }

    private static final Player _init_$lambda$0(BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.location();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.itemInHand();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.boneMeal().requiredItem();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.boneMeal().requiredItem();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.boneMeal().returnedItem();
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.cropConfig().id();
    }

    private static final Object _init_$lambda$7(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.cropConfig().seed();
    }

    private static final Object _init_$lambda$8(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.blockState().asString();
    }

    private static final Object _init_$lambda$9(PlayerProfile playerProfile2, Task task, BoneMealUseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.blockState().type().type().asString();
    }

    static {
        INSTANCE.handler(CCBoneMealUse::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CCBoneMealUse::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", CCBoneMealUse::_init_$lambda$2);
        INSTANCE.addCondition("bonemeal", "String", CCBoneMealUse::_init_$lambda$3);
        INSTANCE.addCondition("bonemeal:required", "String", CCBoneMealUse::_init_$lambda$4);
        INSTANCE.addCondition("bonemeal:returned", "String", CCBoneMealUse::_init_$lambda$5);
        INSTANCE.addCondition("crop", "String", CCBoneMealUse::_init_$lambda$6);
        INSTANCE.addCondition("crop:seed", "String", CCBoneMealUse::_init_$lambda$7);
        INSTANCE.addCondition("block", "String", CCBoneMealUse::_init_$lambda$8);
        INSTANCE.addCondition("block:type", "String", CCBoneMealUse::_init_$lambda$9);
    }
}

