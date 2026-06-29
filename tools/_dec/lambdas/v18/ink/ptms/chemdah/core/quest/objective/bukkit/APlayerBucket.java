/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerBucketEvent
 *  org.bukkit.inventory.ItemStack
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.util.CollectionKt;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.inventory.ItemStack;

@Abstract
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/APlayerBucket;", "T", "Lorg/bukkit/event/player/PlayerBucketEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAPlayerBucket.kt\nKotlin\n*S Kotlin\n*F\n+ 1 APlayerBucket.kt\nink/ptms/chemdah/core/quest/objective/bukkit/APlayerBucket\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,46:1\n1747#2,3:47\n*S KotlinDebug\n*F\n+ 1 APlayerBucket.kt\nink/ptms/chemdah/core/quest/objective/bukkit/APlayerBucket\n*L\n40#1:47,3\n*E\n"})
public abstract class APlayerBucket<T extends PlayerBucketEvent>
extends ObjectiveCountableI<T> {
    public APlayerBucket() {
        this.handler(APlayerBucket::_init_$lambda$0);
        this.addSimpleCondition("position", APlayerBucket::_init_$lambda$1);
        this.addSimpleCondition("material", APlayerBucket::_init_$lambda$2);
        this.addSimpleCondition("material:clicked", APlayerBucket::_init_$lambda$3);
        this.addSimpleCondition("item", APlayerBucket::_init_$lambda$4);
        this.addSimpleCondition("item:bucket", APlayerBucket::_init_$lambda$5);
        this.addSimpleCondition("face", APlayerBucket::_init_$lambda$7);
        this.addSimpleCondition("hand", APlayerBucket::_init_$lambda$8);
    }

    private static final Player _init_$lambda$0(PlayerBucketEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerBucketEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getBlockClicked().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.blockClicked.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerBucketEvent it) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = it.getBlock();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.block");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerBucketEvent it) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = it.getBlockClicked();
        Intrinsics.checkNotNullExpressionValue((Object)block, (String)"it.blockClicked");
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerBucketEvent it) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = it.getItemStack();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"it.itemStack ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$5(Data data2, PlayerBucketEvent it) {
        return data2.toInferItem().isItem(new ItemStack(it.getBucket()));
    }

    private static final Boolean _init_$lambda$7(Data data2, PlayerBucketEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getBlockFace().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$8(Data data2, PlayerBucketEvent e) {
        List<String> list2 = data2.asList();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        return CollectionKt.has(list2, String.valueOf(Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"getHand", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null)));
    }
}

