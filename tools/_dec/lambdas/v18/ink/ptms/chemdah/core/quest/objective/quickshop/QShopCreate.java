/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.maxgamer.quickshop.event.ShopCreateEvent
 */
package ink.ptms.chemdah.core.quest.objective.quickshop;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.maxgamer.quickshop.event.ShopCreateEvent;

@Dependency(plugin="QuickShop")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/quickshop/QShopCreate;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/maxgamer/quickshop/event/ShopCreateEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class QShopCreate
extends ObjectiveCountableI<ShopCreateEvent> {
    @NotNull
    public static final QShopCreate INSTANCE = new QShopCreate();
    @NotNull
    private static final String name = "quickshop create";
    @NotNull
    private static final Class<ShopCreateEvent> event = ShopCreateEvent.class;

    private QShopCreate() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ShopCreateEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, ShopCreateEvent it) {
        InferArea inferArea = data2.toPosition();
        Player player = it.getPlayer();
        Intrinsics.checkNotNull((Object)player);
        Location location = player.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.player!!.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, ShopCreateEvent it) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = it.getShop().getItem();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"it.shop.item");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$3(Data data2, ShopCreateEvent it) {
        return data2.toBoolean() == it.getShop().isBuying();
    }

    private static final Boolean _init_$lambda$4(Data data2, ShopCreateEvent it) {
        return data2.toBoolean() == it.getShop().isSelling();
    }

    static {
        INSTANCE.handler(QShopCreate::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", QShopCreate::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", QShopCreate::_init_$lambda$2);
        INSTANCE.addSimpleCondition("type:buy", QShopCreate::_init_$lambda$3);
        INSTANCE.addSimpleCondition("type:sell", QShopCreate::_init_$lambda$4);
    }
}

