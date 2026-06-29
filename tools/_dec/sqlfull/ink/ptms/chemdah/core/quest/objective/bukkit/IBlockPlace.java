/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockPlace;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/block/BlockPlaceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIBlockPlace.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IBlockPlace.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockPlace\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,37:1\n1747#2,3:38\n*S KotlinDebug\n*F\n+ 1 IBlockPlace.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockPlace\n*L\n34#1:38,3\n*E\n"})
public final class IBlockPlace
extends ObjectiveCountableI<BlockPlaceEvent> {
    @NotNull
    public static final IBlockPlace INSTANCE = new IBlockPlace();
    @NotNull
    private static final String name = "block place";
    @NotNull
    private static final Class<BlockPlaceEvent> event = BlockPlaceEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(BlockPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, BlockPlaceEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getBlock().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.block.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, BlockPlaceEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"e.block");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$3(Data data2, BlockPlaceEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getBlockAgainst();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"e.blockAgainst");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$5(Data data2, BlockPlaceEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)e.getHand().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    static {
        INSTANCE.handler(IBlockPlace::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IBlockPlace::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IBlockPlace::_init_$lambda$2);
        INSTANCE.addSimpleCondition("material:against", IBlockPlace::_init_$lambda$3);
        INSTANCE.addSimpleCondition("hand", IBlockPlace::_init_$lambda$5);
    }
}

