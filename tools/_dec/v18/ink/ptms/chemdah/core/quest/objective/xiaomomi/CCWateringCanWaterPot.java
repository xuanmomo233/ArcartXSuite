/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customcrops.api.event.WateringCanWaterPotEvent
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.util.CollectionKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.momirealms.customcrops.api.event.WateringCanWaterPotEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCWateringCanWaterPot;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/WateringCanWaterPotEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCWateringCanWaterPot
extends ObjectiveCountableI<WateringCanWaterPotEvent> {
    @NotNull
    public static final CCWateringCanWaterPot INSTANCE = new CCWateringCanWaterPot();
    @NotNull
    private static final String name = "customcrops water pot";
    @NotNull
    private static final Class<WateringCanWaterPotEvent> event = WateringCanWaterPotEvent.class;

    private CCWateringCanWaterPot() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<WateringCanWaterPotEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(WateringCanWaterPotEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, WateringCanWaterPotEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, WateringCanWaterPotEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.itemInHand();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.itemInHand()");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$3(Data data2, WateringCanWaterPotEvent e) {
        List<String> list2 = data2.asList();
        String string = e.potConfig().id();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.potConfig().id()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$4(Data data2, WateringCanWaterPotEvent e) {
        List<String> list2 = data2.asList();
        String string = e.wateringCanConfig().id();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.wateringCanConfig().id()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$5(Data data2, WateringCanWaterPotEvent e) {
        List<String> list2 = data2.asList();
        String string = e.wateringCanConfig().itemID();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.wateringCanConfig().itemID()");
        return CollectionKt.has(list2, string);
    }

    static {
        INSTANCE.handler(CCWateringCanWaterPot::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CCWateringCanWaterPot::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", CCWateringCanWaterPot::_init_$lambda$2);
        INSTANCE.addSimpleCondition("pot", CCWateringCanWaterPot::_init_$lambda$3);
        INSTANCE.addSimpleCondition("can", CCWateringCanWaterPot::_init_$lambda$4);
        INSTANCE.addSimpleCondition("can:item", CCWateringCanWaterPot::_init_$lambda$5);
    }
}

