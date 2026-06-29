/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIBlockInteract.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IBlockInteract.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockInteract\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,43:1\n1747#2,3:44\n1747#2,3:47\n1747#2,3:50\n*S KotlinDebug\n*F\n+ 1 IBlockInteract.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockInteract\n*L\n31#1:44,3\n34#1:47,3\n37#1:50,3\n*E\n"})
public final class IBlockInteract
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IBlockInteract INSTANCE = new IBlockInteract();
    @NotNull
    private static final String name = "block interact";
    @NotNull
    private static final Class<PlayerInteractEvent> event = PlayerInteractEvent.class;

    private IBlockInteract() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerInteractEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getClickedBlock() != null ? it.getPlayer() : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerInteractEvent e) {
        InferArea inferArea = data2.toPosition();
        Block block = e.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        Location location = block.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.clickedBlock!!.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerInteractEvent e) {
        InferBlock inferBlock = data2.toInferBlock();
        Block block = e.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        return inferBlock.isBlock(block);
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerInteractEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getAction().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$6(Data data2, PlayerInteractEvent e) {
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

    private static final Boolean _init_$lambda$8(Data data2, PlayerInteractEvent e) {
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
                    EquipmentSlot equipmentSlot = e.getHand();
                    if (!StringsKt.equals((String)it, (String)(equipmentSlot != null ? equipmentSlot.name() : null), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$9(Data data2, PlayerInteractEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getItem();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.item ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IBlockInteract::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IBlockInteract::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IBlockInteract::_init_$lambda$2);
        INSTANCE.addSimpleCondition("action", IBlockInteract::_init_$lambda$4);
        INSTANCE.addSimpleCondition("face", IBlockInteract::_init_$lambda$6);
        INSTANCE.addSimpleCondition("hand", IBlockInteract::_init_$lambda$8);
        INSTANCE.addSimpleCondition("item", IBlockInteract::_init_$lambda$9);
    }
}

