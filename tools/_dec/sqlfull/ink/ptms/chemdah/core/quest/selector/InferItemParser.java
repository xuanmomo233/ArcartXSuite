/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.api.event.InferItemHookEvent;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.InferItem;
import ink.ptms.chemdah.core.quest.selector.ItemEnchant;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0006\u001a\u00020\u0007H\u0016J\u0010\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0016\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItemParser;", "", "()V", "getEnchantments", "", "Link/ptms/chemdah/core/quest/selector/ItemEnchant;", "item", "Lorg/bukkit/inventory/ItemStack;", "parse", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "source", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferItemParser.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferItemParser.kt\nink/ptms/chemdah/core/quest/selector/InferItemParser\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,27:1\n1#2:28\n1549#3:29\n1620#3,3:30\n*S KotlinDebug\n*F\n+ 1 InferItemParser.kt\nink/ptms/chemdah/core/quest/selector/InferItemParser\n*L\n25#1:29\n25#1:30,3\n*E\n"})
public class InferItemParser {
    @NotNull
    public InferItem.Item parse(@NotNull String source) {
        Class<InferItem.Item> clazz;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        DataMatchHandler.Matcher matcher2 = DataMatchHandler.parseMatcher$default(DataMatchHandler.INSTANCE, source, false, 2, null);
        Object[] objectArray = matcher2.getNamespace();
        if (Intrinsics.areEqual((Object)objectArray, (Object)"mc") ? true : Intrinsics.areEqual((Object)objectArray, (Object)"minecraft")) {
            clazz = InferItem.Item.class;
        } else {
            InferItemHookEvent inferItemHookEvent;
            InferItemHookEvent $this$parse_u24lambda_u240 = inferItemHookEvent = new InferItemHookEvent(matcher2.getNamespace(), InferItem.Item.class);
            boolean bl = false;
            $this$parse_u24lambda_u240.call();
            clazz = inferItemHookEvent.getItemClass();
        }
        Class<InferItem.Item> item2 = clazz;
        objectArray = new Object[]{matcher2.getKey(), matcher2.getFlags(), matcher2.getDataMatch()};
        return (InferItem.Item)Reflex.Companion.invokeConstructor(item2, objectArray);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<ItemEnchant> getEnchantments(@NotNull ItemStack item2) {
        List list2;
        Object object;
        Set entries;
        Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
        ItemMeta meta = item2.getItemMeta();
        Set set2 = entries = meta instanceof EnchantmentStorageMeta ? ((EnchantmentStorageMeta)meta).getStoredEnchants().entrySet() : ((object = meta) != null && (object = object.getEnchants()) != null ? object.entrySet() : null);
        if (set2 != null) {
            void $this$mapTo$iv$iv;
            Iterable $this$map$iv = set2;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                Map.Entry entry = (Map.Entry)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                String string = ((Enchantment)it.getKey()).getName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"it.key.name");
                String string2 = string.toLowerCase(Locale.ROOT);
                Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
                Object v = it.getValue();
                Intrinsics.checkNotNullExpressionValue(v, (String)"it.value");
                collection.add(new ItemEnchant(string2, ((Number)v).intValue()));
            }
            list2 = (List)destination$iv$iv;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        return list2;
    }
}

