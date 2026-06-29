/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerShootBow;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityShootBowEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIPlayerShootBow.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IPlayerShootBow.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerShootBow\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,54:1\n1747#2,3:55\n29#3:58\n*S KotlinDebug\n*F\n+ 1 IPlayerShootBow.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerShootBow\n*L\n39#1:55,3\n45#1:58\n*E\n"})
public final class IPlayerShootBow
extends ObjectiveCountableI<EntityShootBowEvent> {
    @NotNull
    public static final IPlayerShootBow INSTANCE = new IPlayerShootBow();
    @NotNull
    private static final String name = "shoot bow";
    @NotNull
    private static final Class<EntityShootBowEvent> event = EntityShootBowEvent.class;

    private IPlayerShootBow() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityShootBowEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityShootBowEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityShootBowEvent e) {
        return data2.toInferEntity().isEntity(e.getProjectile());
    }

    private static final Boolean _init_$lambda$3(Data data2, EntityShootBowEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getBow();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.bow ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$4(Data data2, EntityShootBowEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getConsumable();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.consumable ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$6(Data data2, EntityShootBowEvent e) {
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

    private static final Boolean _init_$lambda$7(Data data2, EntityShootBowEvent e) {
        return data2.toConditionNumber().check(Float.valueOf(e.getForce()));
    }

    private static final Boolean _init_$lambda$8(Data data2, EntityShootBowEvent e) {
        Data $this$cbool$iv = data2;
        boolean $i$f$getCbool = false;
        return Coerce.toBoolean((Object)$this$cbool$iv) == e.shouldConsumeItem();
    }

    private static final Object _init_$lambda$9(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return Float.valueOf(it.getForce());
    }

    private static final Object _init_$lambda$10(EntityShootBowEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.shouldConsumeItem();
    }

    static {
        INSTANCE.handler(IPlayerShootBow::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerShootBow::_init_$lambda$1);
        INSTANCE.addSimpleCondition("arrow", IPlayerShootBow::_init_$lambda$2);
        INSTANCE.addSimpleCondition("item", IPlayerShootBow::_init_$lambda$3);
        INSTANCE.addSimpleCondition("item:consumed", IPlayerShootBow::_init_$lambda$4);
        INSTANCE.addSimpleCondition("hand", IPlayerShootBow::_init_$lambda$6);
        INSTANCE.addSimpleCondition("force", IPlayerShootBow::_init_$lambda$7);
        INSTANCE.addSimpleCondition("consumable", IPlayerShootBow::_init_$lambda$8);
        INSTANCE.addConditionVariable("force", IPlayerShootBow::_init_$lambda$9);
        INSTANCE.addConditionVariable("consumable", IPlayerShootBow::_init_$lambda$10);
    }
}

