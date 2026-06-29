/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/enchantment/EnchantItemEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIItemEnchant.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IItemEnchant.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,40:1\n1747#2,2:41\n1749#2:46\n187#3,3:43\n*S KotlinDebug\n*F\n+ 1 IItemEnchant.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IItemEnchant\n*L\n31#1:41,2\n31#1:46\n31#1:43,3\n*E\n"})
public final class IItemEnchant
extends ObjectiveCountableI<EnchantItemEvent> {
    @NotNull
    public static final IItemEnchant INSTANCE = new IItemEnchant();
    @NotNull
    private static final String name = "enchant item";
    @NotNull
    private static final Class<EnchantItemEvent> event = EnchantItemEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEnchanter();
    }

    private static final Boolean _init_$lambda$1(Data data2, EnchantItemEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEnchantBlock().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.enchantBlock.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EnchantItemEvent e) {
        InferItem inferItem = data2.toInferItem();
        ItemStack itemStack = e.getItem();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"e.item");
        return inferItem.isItem(itemStack);
    }

    private static final Boolean _init_$lambda$5(Data data2, EnchantItemEvent e) {
        boolean bl;
        block7: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    boolean bl2;
                    block6: {
                        Map $this$any$iv2;
                        String it = (String)element$iv;
                        boolean bl3 = false;
                        Intrinsics.checkNotNullExpressionValue((Object)e.getEnchantsToAdd(), (String)"e.enchantsToAdd");
                        boolean $i$f$any2 = false;
                        if ($this$any$iv2.isEmpty()) {
                            bl2 = false;
                        } else {
                            Iterator iterator = $this$any$iv2.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry element$iv2;
                                Map.Entry e2 = element$iv2 = iterator.next();
                                boolean bl4 = false;
                                if (!StringsKt.equals((String)((Enchantment)e2.getKey()).getName(), (String)it, (boolean)true)) continue;
                                bl2 = true;
                                break block6;
                            }
                            bl2 = false;
                        }
                    }
                    if (!bl2) continue;
                    bl = true;
                    break block7;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$6(Data data2, EnchantItemEvent e) {
        return data2.toConditionNumber().check(e.getExpLevelCost());
    }

    private static final Object _init_$lambda$7(EnchantItemEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExpLevelCost();
    }

    static {
        INSTANCE.handler(IItemEnchant::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemEnchant::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", IItemEnchant::_init_$lambda$2);
        INSTANCE.addSimpleCondition("type", IItemEnchant::_init_$lambda$5);
        INSTANCE.addSimpleCondition("cost", IItemEnchant::_init_$lambda$6);
        INSTANCE.addConditionVariable("cost", IItemEnchant::_init_$lambda$7);
    }
}

