/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.sandalphon.module.impl.blockmine.event.BlockBreakEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.sandalphon;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.sandalphon.module.impl.blockmine.event.BlockBreakEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Sandalphon")
@MetaInfo(source="Sandalphon", name="Sandalphon \u65b9\u5757\u7834\u574f\u76ee\u6807", description={"\u7834\u574f Sandalphon \u81ea\u5b9a\u4e49\u65b9\u5757", "\u652f\u6301\u4f4d\u7f6e\u3001\u65b9\u5757\u6750\u8d28\u3001\u65b9\u5757ID\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Sandalphon \u63d2\u4ef6\u652f\u6301"}, alias={"sandalphon\u6316\u77ff", "\u6316\u6398\u65b9\u5757", "\u7834\u574f\u65b9\u5757"}, params={@ParamInfo(name="position", type="string", description="\u65b9\u5757\u7834\u574f\u4f4d\u7f6e"), @ParamInfo(name="material", type="string", description="\u65b9\u5757\u6750\u8d28\u7c7b\u578b"), @ParamInfo(name="id", type="string", description="Sandalphon\u81ea\u5b9a\u4e49\u65b9\u5757ID")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/sandalphon/SBlockBreak;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/sandalphon/module/impl/blockmine/event/BlockBreakEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class SBlockBreak
extends ObjectiveCountableI<BlockBreakEvent> {
    @NotNull
    public static final SBlockBreak INSTANCE = new SBlockBreak();
    @NotNull
    private static final String name = "sandalphon block break";

    private SBlockBreak() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BlockBreakEvent> getEvent() {
        return BlockBreakEvent.class;
    }

    private static final Player _init_$lambda$0(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBukkitEvent().getBlock().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBukkitEvent().getBlock();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockData().getId();
    }

    private static final Object _init_$lambda$4(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockData().getId();
    }

    static {
        INSTANCE.handler(SBlockBreak::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", SBlockBreak::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", SBlockBreak::_init_$lambda$2);
        INSTANCE.addCondition("id", "String", SBlockBreak::_init_$lambda$3);
        INSTANCE.addConditionVariable("id", SBlockBreak::_init_$lambda$4);
    }
}

