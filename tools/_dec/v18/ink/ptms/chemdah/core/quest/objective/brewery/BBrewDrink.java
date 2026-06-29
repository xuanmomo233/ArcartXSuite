/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dre.brewery.api.events.brew.BrewDrinkEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.brewery;

import com.dre.brewery.api.events.brew.BrewDrinkEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Brewery")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/brewery/BBrewDrink;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/dre/brewery/api/events/brew/BrewDrinkEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nBBrewDrink.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BBrewDrink.kt\nink/ptms/chemdah/core/quest/objective/brewery/BBrewDrink\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,45:1\n1#2:46\n*E\n"})
public final class BBrewDrink
extends ObjectiveCountableI<BrewDrinkEvent> {
    @NotNull
    public static final BBrewDrink INSTANCE = new BBrewDrink();
    @NotNull
    private static final String name = "brewery drink";
    @NotNull
    private static final Class<BrewDrinkEvent> event = BrewDrinkEvent.class;

    private BBrewDrink() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<BrewDrinkEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, BrewDrinkEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.player.location");
        return inferArea.inside(location);
    }

    /*
     * WARNING - void declaration
     */
    private static final Boolean _init_$lambda$3(Data data2, BrewDrinkEvent it) {
        void item2;
        ItemStack itemStack;
        ItemStack itemStack2 = itemStack = new ItemStack(Material.POTION);
        InferItem inferItem = data2.toInferItem();
        boolean bl = false;
        item2.setItemMeta(it.getItemMeta());
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$4(Data data2, BrewDrinkEvent it) {
        return data2.toConditionNumber().check(it.getAddedAlcohol());
    }

    private static final Boolean _init_$lambda$5(Data data2, BrewDrinkEvent it) {
        return data2.toConditionNumber().check(it.getQuality());
    }

    private static final Object _init_$lambda$6(BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAddedAlcohol();
    }

    private static final Object _init_$lambda$7(BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getQuality();
    }

    static {
        INSTANCE.handler(BBrewDrink::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", BBrewDrink::_init_$lambda$1);
        INSTANCE.addSimpleCondition("brew", BBrewDrink::_init_$lambda$3);
        INSTANCE.addSimpleCondition("alcohol", BBrewDrink::_init_$lambda$4);
        INSTANCE.addSimpleCondition("quality", BBrewDrink::_init_$lambda$5);
        INSTANCE.addConditionVariable("alcohol", BBrewDrink::_init_$lambda$6);
        INSTANCE.addConditionVariable("quality", BBrewDrink::_init_$lambda$7);
    }
}

