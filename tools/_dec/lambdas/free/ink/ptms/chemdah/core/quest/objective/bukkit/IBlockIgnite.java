/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.BlockIgniteEvent
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
import org.bukkit.event.block.BlockIgniteEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u65b9\u5757\u70b9\u71c3\u76ee\u6807", description={"\u73a9\u5bb6\u70b9\u71c3\u65b9\u5757", "\u652f\u6301\u6750\u8d28\u3001\u70b9\u71c3\u539f\u56e0\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u70b9\u71c3\u6b21\u6570"}, alias={"\u70b9\u706b", "\u70b9\u71c3", "\u5f15\u71c3\u65b9\u5757"}, params={@ParamInfo(name="position", type="Location", description="\u88ab\u70b9\u71c3\u65b9\u5757\u7684\u4f4d\u7f6e"), @ParamInfo(name="material", type="Block", description="\u88ab\u70b9\u71c3\u7684\u65b9\u5757\u6750\u8d28"), @ParamInfo(name="material:igniting", type="Block", description="\u5f15\u71c3\u7684\u65b9\u5757\u6750\u8d28"), @ParamInfo(name="cause", type="String", description="\u70b9\u71c3\u539f\u56e0\uff08\u5982FLINT_AND_STEEL\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockIgnite;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/block/BlockIgniteEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IBlockIgnite
extends ObjectiveCountableI<BlockIgniteEvent> {
    @NotNull
    public static final IBlockIgnite INSTANCE = new IBlockIgnite();
    @NotNull
    private static final String name = "block ignite";

    private IBlockIgnite() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BlockIgniteEvent> getEvent() {
        return BlockIgniteEvent.class;
    }

    private static final Player _init_$lambda$0(BlockIgniteEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BlockIgniteEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, BlockIgniteEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlock();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BlockIgniteEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getIgnitingBlock();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, BlockIgniteEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCause().name();
    }

    static {
        INSTANCE.handler(IBlockIgnite::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IBlockIgnite::_init_$lambda$1);
        INSTANCE.addCondition("material", "Block", IBlockIgnite::_init_$lambda$2);
        INSTANCE.addCondition("material:igniting", "Block", IBlockIgnite::_init_$lambda$3);
        INSTANCE.addCondition("cause", "String", IBlockIgnite::_init_$lambda$4);
    }
}

