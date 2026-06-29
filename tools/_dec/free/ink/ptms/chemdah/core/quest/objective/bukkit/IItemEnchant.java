/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.enchantment.EnchantItemEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u7269\u54c1\u9644\u9b54\u76ee\u6807", description={"\u73a9\u5bb6\u5728\u9644\u9b54\u53f0\u9644\u9b54\u7269\u54c1\u65f6\u89e6\u53d1", "\u652f\u6301\u7269\u54c1\u3001\u9644\u9b54\u7c7b\u578b\u3001\u7ecf\u9a8c\u6d88\u8017\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u9644\u9b54\u6b21\u6570"}, alias={"\u9644\u9b54", "\u9644\u9b54\u53f0", "\u7ed9\u7269\u54c1\u9644\u9b54"}, params={@ParamInfo(name="position", type="Location", description="\u9644\u9b54\u53f0\u7684\u4f4d\u7f6e"), @ParamInfo(name="item", type="ItemStack", description="\u88ab\u9644\u9b54\u7684\u7269\u54c1"), @ParamInfo(name="type", type="String", description="\u9644\u9b54\u7c7b\u578b"), @ParamInfo(name="cost", type="Number", description="\u6d88\u8017\u7684\u7ecf\u9a8c\u7b49\u7ea7")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/enchantment/EnchantItemEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIItemEnchant.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IItemEnchant.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,61:1\n125#2:62\n152#2,3:63\n*S KotlinDebug\n*F\n+ 1 IItemEnchant.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant\n*L\n55#1:62\n55#1:63,3\n*E\n"})
public final class IItemEnchant
extends ObjectiveCountableI<EnchantItemEvent> {
    @NotNull
    public static final IItemEnchant INSTANCE = new IItemEnchant();
    @NotNull
    private static final String name = "enchant item";

    private IItemEnchant() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EnchantItemEvent> getEvent() {
        return EnchantItemEvent.class;
    }

    private static final Player _init_$lambda$0(EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEnchanter();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEnchantBlock().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getItem();
    }

    /*
     * WARNING - void declaration
     */
    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EnchantItemEvent it) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Map map = it.getEnchantsToAdd();
        Intrinsics.checkNotNullExpressionValue((Object)map, (String)"it.enchantsToAdd");
        Map $this$map$iv = map;
        boolean $i$f$map = false;
        Map map2 = $this$map$iv;
        Collection destination$iv$iv = new ArrayList($this$map$iv.size());
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void e;
            Map.Entry item$iv$iv;
            Map.Entry entry = item$iv$iv = iterator.next();
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(((Enchantment)e.getKey()).getName());
        }
        return (List)destination$iv$iv;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpLevelCost();
    }

    private static final Object _init_$lambda$6(EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpLevelCost();
    }

    static {
        INSTANCE.handler(IItemEnchant::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemEnchant::_init_$lambda$1);
        INSTANCE.addCondition("item", "ItemStack", IItemEnchant::_init_$lambda$2);
        INSTANCE.addCondition("type", "String", IItemEnchant::_init_$lambda$4);
        INSTANCE.addCondition("cost", "Number", IItemEnchant::_init_$lambda$5);
        INSTANCE.addConditionVariable("cost", IItemEnchant::_init_$lambda$6);
    }
}

