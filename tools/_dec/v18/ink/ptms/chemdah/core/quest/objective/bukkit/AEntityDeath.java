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
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.inventory.ItemStack
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.ConditionNumber;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

@Abstract
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath;", "T", "Lorg/bukkit/event/entity/EntityDeathEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAEntityDeath.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AEntityDeath.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,53:1\n1747#2,3:54\n1747#2,3:57\n*S KotlinDebug\n*F\n+ 1 AEntityDeath.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDeath\n*L\n29#1:54,3\n32#1:57,3\n*E\n"})
public abstract class AEntityDeath<T extends EntityDeathEvent>
extends ObjectiveCountableI<T> {
    public AEntityDeath() {
        this.addSimpleCondition("position", AEntityDeath::_init_$lambda$0);
        this.addSimpleCondition("damage", AEntityDeath::_init_$lambda$1);
        this.addSimpleCondition("damage:final", AEntityDeath::_init_$lambda$2);
        this.addSimpleCondition("cause", AEntityDeath::_init_$lambda$4);
        this.addSimpleCondition("drops", AEntityDeath::_init_$lambda$6);
        this.addSimpleCondition("exp", AEntityDeath::_init_$lambda$7);
        this.addSimpleCondition("revive-health", AEntityDeath::_init_$lambda$8);
        this.addConditionVariable("damage", AEntityDeath::_init_$lambda$9);
        this.addConditionVariable("damage:final", AEntityDeath::_init_$lambda$10);
        this.addConditionVariable("exp", AEntityDeath::_init_$lambda$11);
        this.addConditionVariable("revive-health", AEntityDeath::_init_$lambda$12);
    }

    private static final Boolean _init_$lambda$0(Data data2, EntityDeathEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDeathEvent e) {
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return data2.toConditionNumber().check(entityDamageEvent != null ? entityDamageEvent.getDamage() : 0.0);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityDeathEvent e) {
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return data2.toConditionNumber().check(entityDamageEvent != null ? entityDamageEvent.getFinalDamage() : 0.0);
    }

    private static final Boolean _init_$lambda$4(Data data2, EntityDeathEvent e) {
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
                    EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
                    if (!StringsKt.equals((String)it, (String)String.valueOf(entityDamageEvent != null && (entityDamageEvent = entityDamageEvent.getCause()) != null ? entityDamageEvent.name() : null), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$6(Data data2, EntityDeathEvent e) {
        boolean bl;
        block3: {
            List list2 = e.getDrops();
            Intrinsics.checkNotNullExpressionValue((Object)list2, (String)"e.drops");
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

    private static final Boolean _init_$lambda$7(Data data2, EntityDeathEvent e) {
        return data2.toConditionNumber().check(e.getDroppedExp());
    }

    private static final Boolean _init_$lambda$8(Data data2, EntityDeathEvent e) {
        ConditionNumber conditionNumber = data2.toConditionNumber();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"getReviveHealth", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        Intrinsics.checkNotNull((Object)object);
        return conditionNumber.check((Number)object);
    }

    private static final Object _init_$lambda$9(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$10(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        EntityDamageEvent entityDamageEvent = e.getEntity().getLastDamageCause();
        return entityDamageEvent != null ? Double.valueOf(entityDamageEvent.getFinalDamage()) : Double.valueOf(0.0);
    }

    private static final Object _init_$lambda$11(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return e.getDroppedExp();
    }

    private static final Object _init_$lambda$12(EntityDeathEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"getReviveHealth", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        Intrinsics.checkNotNull((Object)object);
        return object;
    }
}

