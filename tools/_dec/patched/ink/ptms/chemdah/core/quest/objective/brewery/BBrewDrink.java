/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.dre.brewery.api.events.brew.BrewDrinkEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.brewery;

import com.dre.brewery.api.events.brew.BrewDrinkEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Brewery")
@MetaInfo(source="Brewery", name="Brewery \u996e\u7528\u9152\u6c34\u76ee\u6807", description={"\u996e\u7528 Brewery \u9152\u6c34", "\u652f\u6301\u4f4d\u7f6e\u3001\u9152\u6c34\u7269\u54c1\u3001\u9152\u7cbe\u5ea6\u3001\u54c1\u8d28\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Brewery \u63d2\u4ef6\u652f\u6301"}, alias={"brewery\u996e\u7528", "\u559d\u9152", "\u996e\u7528\u9152\u6c34"}, params={@ParamInfo(name="position", type="section", description="\u996e\u7528\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="brew", type="section", description="\u9152\u6c34\u7269\u54c1\u6761\u4ef6"), @ParamInfo(name="alcohol", type="section", description="\u9152\u7cbe\u5ea6\u6570\u6761\u4ef6"), @ParamInfo(name="quality", type="section", description="\u9152\u6c34\u54c1\u8d28\u6761\u4ef6")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/brewery/BBrewDrink;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/dre/brewery/api/events/brew/BrewDrinkEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nBBrewDrink.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BBrewDrink.kt\nink/ptms/chemdah/core/quest/objective/brewery/BBrewDrink\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,52:1\n1#2:53\n*E\n"})
public final class BBrewDrink
extends ObjectiveCountableI<BrewDrinkEvent> {
    @NotNull
    public static final BBrewDrink INSTANCE = new BBrewDrink();
    @NotNull
    private static final String name = "brewery drink";

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
        return BrewDrinkEvent.class;
    }

    private static final Player _init_$lambda$0(BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, BrewDrinkEvent it) {
        ItemStack itemStack;
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ItemStack item2 = itemStack = new ItemStack(Material.POTION);
        boolean bl = false;
        item2.setItemMeta(it.getItemMeta());
        return itemStack;
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAddedAlcohol();
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, BrewDrinkEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getQuality();
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
        INSTANCE.addCondition("position", "Location", BBrewDrink::_init_$lambda$1);
        INSTANCE.addCondition("brew", "ItemStack", BBrewDrink::_init_$lambda$3);
        INSTANCE.addCondition("alcohol", "Number", BBrewDrink::_init_$lambda$4);
        INSTANCE.addCondition("quality", "Number", BBrewDrink::_init_$lambda$5);
        INSTANCE.addConditionVariable("alcohol", BBrewDrink::_init_$lambda$6);
        INSTANCE.addConditionVariable("quality", BBrewDrink::_init_$lambda$7);
    }
}

