/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.api.event.AdyeshachEntityDamageEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.adyeshach;

import ink.ptms.adyeshach.api.event.AdyeshachEntityDamageEvent;
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

@Dependency(plugin="Adyeshach")
@MetaInfo(source="Adyeshach", name="Adyeshach NPC \u4f24\u5bb3\u76ee\u6807", description={"\u5bf9 Adyeshach NPC \u9020\u6210\u4f24\u5bb3", "\u652f\u6301 NPC ID\u3001\u7c7b\u578b\u3001\u4f4d\u7f6e\u3001\u4f7f\u7528\u7269\u54c1\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Adyeshach \u63d2\u4ef6\u652f\u6301"}, alias={"anpc\u4f24\u5bb3", "\u4f24\u5bb3npc", "\u653b\u51fbnpc"}, params={@ParamInfo(name="position", type="Location", description="NPC \u7684\u4f4d\u7f6e"), @ParamInfo(name="id", type="String", description="NPC \u7684\u552f\u4e00\u6807\u8bc6"), @ParamInfo(name="type", type="String", description="NPC \u7684\u5b9e\u4f53\u7c7b\u578b"), @ParamInfo(name="item", type="ItemStack", description="\u73a9\u5bb6\u624b\u4e2d\u7684\u7269\u54c1")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/adyeshach/ANPCDamage;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/adyeshach/api/event/AdyeshachEntityDamageEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class ANPCDamage
extends ObjectiveCountableI<AdyeshachEntityDamageEvent> {
    @NotNull
    public static final ANPCDamage INSTANCE = new ANPCDamage();
    @NotNull
    private static final String name = "anpc damage";

    private ANPCDamage() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<AdyeshachEntityDamageEvent> getEvent() {
        return AdyeshachEntityDamageEvent.class;
    }

    private static final Player _init_$lambda$0(AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getId();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getEntityType().name();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getInventory().getItemInMainHand();
    }

    private static final Object _init_$lambda$5(AdyeshachEntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getId();
    }

    static {
        INSTANCE.handler(ANPCDamage::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", ANPCDamage::_init_$lambda$1);
        INSTANCE.addCondition("id", "String", ANPCDamage::_init_$lambda$2);
        INSTANCE.addCondition("type", "String", ANPCDamage::_init_$lambda$3);
        INSTANCE.addCondition("item", "ItemStack", ANPCDamage::_init_$lambda$4);
        INSTANCE.addConditionVariable("id", ANPCDamage::_init_$lambda$5);
    }
}

