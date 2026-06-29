/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.BlockPlaceEvent
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u65b9\u5757\u653e\u7f6e\u76ee\u6807", description={"\u73a9\u5bb6\u653e\u7f6e\u65b9\u5757", "\u652f\u6301\u6750\u8d28\u3001\u4f4d\u7f6e\u3001\u624b\u6301\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u653e\u7f6e\u6570\u91cf"}, alias={"\u653e\u7f6e", "\u6446\u653e\u65b9\u5757", "\u5efa\u9020"}, params={@ParamInfo(name="position", type="Location", description="\u65b9\u5757\u7684\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u653e\u7f6e\u7684\u65b9\u5757\u6750\u8d28\u7c7b\u578b"), @ParamInfo(name="material:against", type="Block", description="\u653e\u7f6e\u65f6\u76f8\u90bb\u7684\u65b9\u5757\u6750\u8d28"), @ParamInfo(name="hand", type="String", description="\u4f7f\u7528\u7684\u624b\uff08HAND/OFF_HAND\uff09"), @ParamInfo(name="item", type="ItemStack", description="\u624b\u6301\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockPlace;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/block/BlockPlaceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IBlockPlace
extends ObjectiveCountableI<BlockPlaceEvent> {
    @NotNull
    public static final IBlockPlace INSTANCE = new IBlockPlace();
    @NotNull
    private static final String name = "block place";

    private IBlockPlace() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BlockPlaceEvent> getEvent() {
        return BlockPlaceEvent.class;
    }

    private static final Player _init_$lambda$0(BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockAgainst();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getHand().name();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItemInHand();
    }

    static {
        INSTANCE.handler(IBlockPlace::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IBlockPlace::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IBlockPlace::_init_$lambda$2);
        INSTANCE.addCondition("material:against", "Block", IBlockPlace::_init_$lambda$3);
        INSTANCE.addCondition("hand", "String", IBlockPlace::_init_$lambda$4);
        INSTANCE.addCondition("item", "ItemStack", IBlockPlace::_init_$lambda$5);
    }
}

