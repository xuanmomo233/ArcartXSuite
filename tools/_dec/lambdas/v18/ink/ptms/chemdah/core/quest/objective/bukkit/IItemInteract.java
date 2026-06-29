/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
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
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\f\u001a\u00020\r8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "getPriority", "()Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIItemInteract.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IItemInteract.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemInteract\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,43:1\n1747#2,3:44\n1747#2,3:47\n*S KotlinDebug\n*F\n+ 1 IItemInteract.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemInteract\n*L\n34#1:44,3\n37#1:47,3\n*E\n"})
public final class IItemInteract
extends ObjectiveCountableI<PlayerInteractEvent> {
    @NotNull
    public static final IItemInteract INSTANCE = new IItemInteract();
    @NotNull
    private static final String name = "item interact";
    @NotNull
    private static final Class<PlayerInteractEvent> event = PlayerInteractEvent.class;

    private IItemInteract() {
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

    @Override
    @NotNull
    public EventPriority getPriority() {
        return EventPriority.LOWEST;
    }

    private static final Player _init_$lambda$0(PlayerInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAction() != Action.PHYSICAL && ItemModifierKt.isNotAir((ItemStack)it.getItem()) ? it.getPlayer() : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerInteractEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerInteractEvent e) {
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

    private static final Boolean _init_$lambda$5(Data data2, PlayerInteractEvent e) {
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

    private static final Boolean _init_$lambda$6(Data data2, PlayerInteractEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getItem();
        Intrinsics.checkNotNull((Object)itemStack);
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IItemInteract::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemInteract::_init_$lambda$1);
        INSTANCE.addSimpleCondition("action", IItemInteract::_init_$lambda$3);
        INSTANCE.addSimpleCondition("hand", IItemInteract::_init_$lambda$5);
        INSTANCE.addSimpleCondition("item", IItemInteract::_init_$lambda$6);
    }
}

