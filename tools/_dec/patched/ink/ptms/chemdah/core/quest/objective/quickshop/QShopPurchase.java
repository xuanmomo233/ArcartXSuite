/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.maxgamer.quickshop.event.ShopPurchaseEvent
 */
package ink.ptms.chemdah.core.quest.objective.quickshop;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.maxgamer.quickshop.event.ShopPurchaseEvent;

@Dependency(plugin="QuickShop")
@MetaInfo(source="QuickShop", name="QuickShop \u5546\u5e97\u8d2d\u4e70\u76ee\u6807", description={"\u73a9\u5bb6\u5728 QuickShop \u5546\u5e97\u8d2d\u4e70\u7269\u54c1", "\u652f\u6301\u4f4d\u7f6e\u3001\u7269\u54c1\u3001\u6570\u91cf\u3001\u603b\u4ef7\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 QuickShop \u63d2\u4ef6\u652f\u6301"}, alias={"qs\u8d2d\u4e70", "\u5546\u5e97\u8d2d\u4e70", "\u8d2d\u4e70\u7269\u54c1"}, params={@ParamInfo(name="position", type="Location", description="\u5546\u5e97\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u8d2d\u4e70\u7684\u7269\u54c1"), @ParamInfo(name="amount", type="Number", description="\u8d2d\u4e70\u7684\u7269\u54c1\u6570\u91cf"), @ParamInfo(name="total", type="Number", description="\u8d2d\u4e70\u7684\u603b\u4ef7\u683c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/quickshop/QShopPurchase;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/maxgamer/quickshop/event/ShopPurchaseEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class QShopPurchase
extends ObjectiveCountableI<ShopPurchaseEvent> {
    @NotNull
    public static final QShopPurchase INSTANCE = new QShopPurchase();
    @NotNull
    private static final String name = "quickshop purchase";

    private QShopPurchase() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ShopPurchaseEvent> getEvent() {
        return ShopPurchaseEvent.class;
    }

    private static final Player _init_$lambda$0(ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Player player2 = it.getPlayer();
        Intrinsics.checkNotNull((Object)player2);
        return player2.getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getShop().getItem();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAmount();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getTotal();
    }

    private static final Object _init_$lambda$5(ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAmount();
    }

    private static final Object _init_$lambda$6(ShopPurchaseEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getTotal();
    }

    static {
        INSTANCE.handler(QShopPurchase::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", QShopPurchase::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", QShopPurchase::_init_$lambda$2);
        INSTANCE.addCondition("amount", "Number", QShopPurchase::_init_$lambda$3);
        INSTANCE.addCondition("total", "Number", QShopPurchase::_init_$lambda$4);
        INSTANCE.addConditionVariable("amount", QShopPurchase::_init_$lambda$5);
        INSTANCE.addConditionVariable("total", QShopPurchase::_init_$lambda$6);
    }
}

