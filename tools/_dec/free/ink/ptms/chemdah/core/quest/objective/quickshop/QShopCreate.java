/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.maxgamer.quickshop.event.ShopCreateEvent
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
import org.maxgamer.quickshop.event.ShopCreateEvent;

@Dependency(plugin="QuickShop")
@MetaInfo(source="QuickShop", name="QuickShop \u5546\u5e97\u521b\u5efa\u76ee\u6807", description={"\u73a9\u5bb6\u521b\u5efa QuickShop \u5546\u5e97", "\u652f\u6301\u4f4d\u7f6e\u3001\u7269\u54c1\u3001\u5546\u5e97\u7c7b\u578b\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 QuickShop \u63d2\u4ef6\u652f\u6301"}, alias={"qs\u521b\u5efa", "\u5546\u5e97\u521b\u5efa", "\u521b\u5efa\u5546\u5e97"}, params={@ParamInfo(name="position", type="Location", description="\u5546\u5e97\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u5546\u5e97\u4ea4\u6613\u7684\u7269\u54c1"), @ParamInfo(name="type:buy", type="Boolean", description="\u5546\u5e97\u662f\u5426\u4e3a\u6536\u8d2d\u7c7b\u578b"), @ParamInfo(name="type:sell", type="Boolean", description="\u5546\u5e97\u662f\u5426\u4e3a\u51fa\u552e\u7c7b\u578b")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/quickshop/QShopCreate;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/maxgamer/quickshop/event/ShopCreateEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class QShopCreate
extends ObjectiveCountableI<ShopCreateEvent> {
    @NotNull
    public static final QShopCreate INSTANCE = new QShopCreate();
    @NotNull
    private static final String name = "quickshop create";

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
        return ShopCreateEvent.class;
    }

    private static final Player _init_$lambda$0(ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Player player2 = it.getPlayer();
        Intrinsics.checkNotNull((Object)player2);
        return player2.getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getShop().getItem();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getShop().isBuying();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, ShopCreateEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getShop().isSelling();
    }

    static {
        INSTANCE.handler(QShopCreate::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", QShopCreate::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", QShopCreate::_init_$lambda$2);
        INSTANCE.addCondition("type:buy", "Boolean", QShopCreate::_init_$lambda$3);
        INSTANCE.addCondition("type:sell", "Boolean", QShopCreate::_init_$lambda$4);
    }
}

