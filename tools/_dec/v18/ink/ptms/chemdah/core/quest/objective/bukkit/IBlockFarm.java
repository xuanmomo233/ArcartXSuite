/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
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
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\f\u0010\f\u001a\u00020\r*\u00020\u000eH\u0002R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IBlockFarm;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "isFarmable", "", "Lorg/bukkit/Material;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIBlockFarm.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IBlockFarm.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockFarm\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,53:1\n1747#2,3:54\n1747#2,3:57\n1747#2,3:60\n*S KotlinDebug\n*F\n+ 1 IBlockFarm.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IBlockFarm\n*L\n37#1:54,3\n40#1:57,3\n43#1:60,3\n*E\n"})
public final class IBlockFarm
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IBlockFarm INSTANCE = new IBlockFarm();
    @NotNull
    private static final String name = "block farm";
    @NotNull
    private static final Class<PlayerInteractEvent> event = PlayerInteractEvent.class;

    private IBlockFarm() {
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

    private final boolean isFarmable(Material $this$isFarmable) {
        return Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"DIRT") || Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"GRASS_BLOCK") || Intrinsics.areEqual((Object)$this$isFarmable.name(), (Object)"GRASS_PATH");
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (it.getAction() != Action.RIGHT_CLICK_BLOCK) return null;
        Block block = it.getClickedBlock();
        Intrinsics.checkNotNull((Object)block);
        Material material = block.getType();
        Intrinsics.checkNotNullExpressionValue((Object)material, (String)"it.clickedBlock!!.type");
        if (!INSTANCE.isFarmable(material)) return null;
        ItemStack itemStack = it.getItem();
        Object object = itemStack;
        if (itemStack == null) return null;
        Material material2 = object.getType();
        object = material2;
        if (material2 == null) return null;
        String string = object.name();
        object = string;
        if (string == null) return null;
        if (!StringsKt.endsWith$default((String)object, (String)"_HOE", (boolean)false, (int)2, null)) return null;
        boolean bl = true;
        if (!bl) return null;
        Player player = it.getPlayer();
        return player;
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
        Intrinsics.checkNotNull((Object)itemStack);
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IBlockFarm::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IBlockFarm::_init_$lambda$1);
        INSTANCE.addSimpleCondition("material", IBlockFarm::_init_$lambda$2);
        INSTANCE.addSimpleCondition("action", IBlockFarm::_init_$lambda$4);
        INSTANCE.addSimpleCondition("face", IBlockFarm::_init_$lambda$6);
        INSTANCE.addSimpleCondition("hand", IBlockFarm::_init_$lambda$8);
        INSTANCE.addSimpleCondition("item", IBlockFarm::_init_$lambda$9);
    }
}

