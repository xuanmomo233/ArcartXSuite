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
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBreed;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityBreedEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBreed
extends ObjectiveCountableI<EntityBreedEvent> {
    @NotNull
    public static final IPlayerBreed INSTANCE = new IPlayerBreed();
    @NotNull
    private static final String name = "entity breed";
    @NotNull
    private static final Class<EntityBreedEvent> event = EntityBreedEvent.class;

    private IPlayerBreed() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityBreedEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getBreeder();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityBreedEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityBreedEvent it) {
        return data2.toInferEntity().isEntity((Entity)it.getEntity());
    }

    private static final Boolean _init_$lambda$3(Data data2, EntityBreedEvent it) {
        return data2.toInferEntity().isEntity((Entity)it.getFather());
    }

    private static final Boolean _init_$lambda$4(Data data2, EntityBreedEvent it) {
        return data2.toInferEntity().isEntity((Entity)it.getMother());
    }

    private static final Boolean _init_$lambda$5(Data data2, EntityBreedEvent it) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = it.getBredWith();
        if (itemStack == null) {
            itemStack = UnitsKt.getEMPTY_ITEM();
        }
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"it.bredWith ?: EMPTY_ITEM");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$6(Data data2, EntityBreedEvent it) {
        return data2.toConditionNumber().check(it.getExperience());
    }

    private static final Object _init_$lambda$7(EntityBreedEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperience();
    }

    static {
        INSTANCE.handler(IPlayerBreed::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerBreed::_init_$lambda$1);
        INSTANCE.addSimpleCondition("entity", IPlayerBreed::_init_$lambda$2);
        INSTANCE.addSimpleCondition("entity:father", IPlayerBreed::_init_$lambda$3);
        INSTANCE.addSimpleCondition("entity:mother", IPlayerBreed::_init_$lambda$4);
        INSTANCE.addSimpleCondition("item", IPlayerBreed::_init_$lambda$5);
        INSTANCE.addSimpleCondition("exp", IPlayerBreed::_init_$lambda$6);
        INSTANCE.addConditionVariable("exp", IPlayerBreed::_init_$lambda$7);
    }
}

