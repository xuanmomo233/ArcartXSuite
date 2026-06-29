/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  net.momirealms.customfishing.api.mechanic.fishing.FishingGears
 *  net.momirealms.customfishing.api.mechanic.fishing.FishingGears$GearType
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import net.momirealms.customfishing.api.mechanic.fishing.FishingGears;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0016\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u001a\u0018\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"getItems", "", "Lorg/bukkit/inventory/ItemStack;", "Lnet/momirealms/customfishing/api/mechanic/fishing/FishingGears;", "type", "Lnet/momirealms/customfishing/api/mechanic/fishing/FishingGears$GearType;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nGears.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Gears.kt\nink/ptms/chemdah/core/quest/objective/xiaomomi/GearsKt\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,12:1\n125#2:13\n152#2,3:14\n*S KotlinDebug\n*F\n+ 1 Gears.kt\nink/ptms/chemdah/core/quest/objective/xiaomomi/GearsKt\n*L\n11#1:13\n11#1:14,3\n*E\n"})
public final class GearsKt {
    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<ItemStack> getItems(@NotNull FishingGears $this$getItems, @NotNull FishingGears.GearType type) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)$this$getItems, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)type, (String)"type");
        Object object = Reflex.Companion.getLocalProperty((Object)$this$getItems, "gears");
        Intrinsics.checkNotNull((Object)object);
        Map $this$map$iv = (Map)object;
        boolean $i$f$map = false;
        Map map = $this$map$iv;
        Collection destination$iv$iv = new ArrayList($this$map$iv.size());
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void it;
            Map.Entry item$iv$iv;
            Map.Entry entry = item$iv$iv = iterator.next();
            Collection collection = destination$iv$iv;
            boolean bl = false;
            Object object2 = Reflex.Companion.getLocalProperty((Object)it, "right");
            Intrinsics.checkNotNull((Object)object2);
            collection.add((ItemStack)object2);
        }
        return (List)destination$iv$iv;
    }
}

