/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.api.event.AdyeshachEntityDamageEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.adyeshach;

import ink.ptms.adyeshach.api.event.AdyeshachEntityDamageEvent;
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
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Adyeshach")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/adyeshach/ANPCDamage;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/adyeshach/api/event/AdyeshachEntityDamageEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nANPCDamage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ANPCDamage.kt\nink/ptms/chemdah/core/quest/objective/adyeshach/ANPCDamage\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,40:1\n1747#2,3:41\n1747#2,3:44\n*S KotlinDebug\n*F\n+ 1 ANPCDamage.kt\nink/ptms/chemdah/core/quest/objective/adyeshach/ANPCDamage\n*L\n28#1:41,3\n31#1:44,3\n*E\n"})
public final class ANPCDamage
extends ObjectiveCountableI<AdyeshachEntityDamageEvent> {
    @NotNull
    public static final ANPCDamage INSTANCE = new ANPCDamage();
    @NotNull
    private static final String name = "anpc damage";
    @NotNull
    private static final Class<AdyeshachEntityDamageEvent> event = AdyeshachEntityDamageEvent.class;

    private ANPCDamage() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<AdyeshachEntityDamageEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, AdyeshachEntityDamageEvent e) {
        return data2.toPosition().inside(e.getEntity().getLocation());
    }

    private static final Boolean _init_$lambda$3(Data data2, AdyeshachEntityDamageEvent e) {
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

    private static final Boolean _init_$lambda$5(Data data2, AdyeshachEntityDamageEvent e) {
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

    private static final Boolean _init_$lambda$6(Data data2, AdyeshachEntityDamageEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getPlayer().getInventory().getItemInMainHand();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.player.inventory.itemInMainHand");
        return inferItem.isItem(itemStack);
    }

    private static final Object _init_$lambda$7(AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getId();
    }

    static {
        INSTANCE.handler(ANPCDamage::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", ANPCDamage::_init_$lambda$1);
        INSTANCE.addSimpleCondition("id", ANPCDamage::_init_$lambda$3);
        INSTANCE.addSimpleCondition("type", ANPCDamage::_init_$lambda$5);
        INSTANCE.addSimpleCondition("item", ANPCDamage::_init_$lambda$6);
        INSTANCE.addConditionVariable("id", ANPCDamage::_init_$lambda$7);
    }
}

