/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.IPlayerResurrect;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.platform.util.PlayerUtilKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u001b\u0010\f\u001a\u00020\r8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0010\u0010\u0011\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerResurrect;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityResurrectEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "totem", "Lorg/bukkit/Material;", "getTotem", "()Lorg/bukkit/Material;", "totem$delegate", "Lkotlin1822/Lazy;", "Chemdah"})
public final class IPlayerResurrect
extends ObjectiveCountableI<EntityResurrectEvent> {
    @NotNull
    public static final IPlayerResurrect INSTANCE = new IPlayerResurrect();
    @NotNull
    private static final String name = "player resurrect";
    @NotNull
    private static final Class<EntityResurrectEvent> event = EntityResurrectEvent.class;
    @NotNull
    private static final Lazy totem$delegate = LazyMakerKt.unsafeLazy((Function0)totem.2.INSTANCE);

    private IPlayerResurrect() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityResurrectEvent> getEvent() {
        return event;
    }

    @NotNull
    public final Material getTotem() {
        Lazy lazy = totem$delegate;
        return (Material)lazy.getValue();
    }

    private static final Player _init_$lambda$0(EntityResurrectEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getEntity();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityResurrectEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityResurrectEvent e) {
        InferItem inferItem = data2.toInferItem();
        LivingEntity livingEntity = e.getEntity();
        Intrinsics.checkNotNull((Object)livingEntity, (String)"null cannot be cast to non-null type org.bukkit.entity.Player");
        HumanEntity humanEntity = (HumanEntity)((Player)livingEntity);
        Material material = INSTANCE.getTotem();
        Intrinsics.checkNotNullExpressionValue((Object)material, (String)"totem");
        ItemStack itemStack = PlayerUtilKt.getUsingItem((HumanEntity)humanEntity, (Material)material);
        if (itemStack == null) {
            return false;
        }
        return inferItem.isItem(itemStack);
    }

    static {
        INSTANCE.handler(IPlayerResurrect::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerResurrect::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", IPlayerResurrect::_init_$lambda$2);
    }
}

