/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerHarvestBlockEvent
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockHarvest;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerHarvestBlockEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIBlockHarvest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IBlockHarvest.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockHarvest\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,34:1\n1747#2,3:35\n*S KotlinDebug\n*F\n+ 1 IBlockHarvest.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockHarvest\n*L\n31#1:35,3\n*E\n"})
public final class IBlockHarvest
extends ObjectiveCountableI<PlayerHarvestBlockEvent> {
    @NotNull
    public static final IBlockHarvest INSTANCE = new IBlockHarvest();
    @NotNull
    private static final String name = "harvest block";
    @NotNull
    private static final Class<PlayerHarvestBlockEvent> event = PlayerHarvestBlockEvent.class;

    private IBlockHarvest() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerHarvestBlockEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerHarvestBlockEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerHarvestBlockEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getHarvestedBlock().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.harvestedBlock.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerHarvestBlockEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getHarvestedBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"e.harvestedBlock");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerHarvestBlockEvent e) {
        boolean bl;
        block3: {
            List list2 = e.getItemsHarvested();
            Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"e.itemsHarvested");
            Iterable $this$any$iv = list2;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    ItemStack it = (ItemStack)element$iv;
                    boolean bl2 = false;
                    InferItem inferItem = data2.toInferItem();
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    if (!inferItem.isItem(it)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    static {
        INSTANCE.handler(IBlockHarvest::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IBlockHarvest::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IBlockHarvest::_init_$lambda$2);
        INSTANCE.addSimpleCondition("item", IBlockHarvest::_init_$lambda$4);
    }
}

