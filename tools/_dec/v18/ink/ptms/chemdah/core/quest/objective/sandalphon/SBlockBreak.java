/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.sandalphon.module.impl.blockmine.event.BlockBreakEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.sandalphon;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.sandalphon.module.impl.blockmine.event.BlockBreakEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Sandalphon")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/sandalphon/SBlockBreak;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/sandalphon/module/impl/blockmine/event/BlockBreakEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class SBlockBreak
extends ObjectiveCountableI<BlockBreakEvent> {
    @NotNull
    public static final SBlockBreak INSTANCE = new SBlockBreak();
    @NotNull
    private static final String name = "sandalphon block break";
    @NotNull
    private static final Class<BlockBreakEvent> event = BlockBreakEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, BlockBreakEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getBukkitEvent().getBlock().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.bukkitEvent.block.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, BlockBreakEvent it) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = it.getBukkitEvent().getBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.bukkitEvent.block");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$3(Data data2, BlockBreakEvent it) {
        return StringsKt.equals((String)data2.toString(), (String)it.getBlockData().getId(), (boolean)true);
    }

    private static final Object _init_$lambda$4(BlockBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getBlockData().getId();
    }

    static {
        INSTANCE.handler(SBlockBreak::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", SBlockBreak::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", SBlockBreak::_init_$lambda$2);
        INSTANCE.addSimpleCondition("id", SBlockBreak::_init_$lambda$3);
        INSTANCE.addConditionVariable("id", SBlockBreak::_init_$lambda$4);
    }
}

