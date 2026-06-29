/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.adyeshach;

import ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Adyeshach")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/adyeshach/ANPCInteract;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/adyeshach/core/event/AdyeshachEntityInteractEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nANPCInteract.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ANPCInteract.kt\nink/ptms/chemdah/core/quest/objective/adyeshach/ANPCInteract\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,47:1\n1747#2,3:48\n1747#2,3:51\n*S KotlinDebug\n*F\n+ 1 ANPCInteract.kt\nink/ptms/chemdah/core/quest/objective/adyeshach/ANPCInteract\n*L\n32#1:48,3\n35#1:51,3\n*E\n"})
public final class ANPCInteract
extends ObjectiveCountableI<AdyeshachEntityInteractEvent> {
    @NotNull
    public static final ANPCInteract INSTANCE = new ANPCInteract();
    @NotNull
    private static final String name = "anpc interact";
    @NotNull
    private static final Class<AdyeshachEntityInteractEvent> event = AdyeshachEntityInteractEvent.class;

    private ANPCInteract() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<AdyeshachEntityInteractEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(AdyeshachEntityInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, AdyeshachEntityInteractEvent e) {
        return data2.toPosition().inside(e.getEntity().getLocation());
    }

    private static final Boolean _init_$lambda$2(Data data2, AdyeshachEntityInteractEvent e) {
        return data2.toVector().inside(new Vector(e.getVector().getX(), e.getVector().getY(), e.getVector().getZ()));
    }

    private static final Boolean _init_$lambda$4(Data data2, AdyeshachEntityInteractEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getEntity().getId(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$6(Data data2, AdyeshachEntityInteractEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getEntity().getEntityType().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$7(Data data2, AdyeshachEntityInteractEvent e) {
        return data2.toBoolean() == e.isMainHand();
    }

    private static final Boolean _init_$lambda$8(Data data2, AdyeshachEntityInteractEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.isMainHand() ? e.getPlayer().getInventory().getItemInMainHand() : e.getPlayer().getInventory().getItemInOffHand();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"if (e.isMainHand) e.play\u2026r.inventory.itemInOffHand");
        return inferItem.isItem(itemStack);
    }

    private static final Object _init_$lambda$9(AdyeshachEntityInteractEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getId();
    }

    static {
        INSTANCE.handler(ANPCInteract::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", ANPCInteract::_init_$lambda$1);
        INSTANCE.addSimpleCondition("position:clicked", ANPCInteract::_init_$lambda$2);
        INSTANCE.addSimpleCondition("id", ANPCInteract::_init_$lambda$4);
        INSTANCE.addSimpleCondition("type", ANPCInteract::_init_$lambda$6);
        INSTANCE.addSimpleCondition("hand", ANPCInteract::_init_$lambda$7);
        INSTANCE.addSimpleCondition("item", ANPCInteract::_init_$lambda$8);
        INSTANCE.addConditionVariable("id", ANPCInteract::_init_$lambda$9);
    }
}

