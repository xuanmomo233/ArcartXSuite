/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt
 *  ink.ptms.chemdah.taboolib.library.xseries.XEnchantment
 *  ink.ptms.chemdah.taboolib.module.kether.action.transform.CheckType
 *  ink.ptms.chemdah.taboolib.module.nms.NMSItemTagKt
 *  ink.ptms.chemdah.taboolib.module.nms.NMSTranslateKt
 *  ink.ptms.chemdah.taboolib.platform.util.ItemMatcherKt
 *  kotlin.Metadata
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.PotionMeta
 *  org.bukkit.potion.PotionEffect
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.core.quest.selector.DataMatch;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.Flags;
import ink.ptms.chemdah.core.quest.selector.ItemEnchant;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import ink.ptms.chemdah.taboolib.library.xseries.XEnchantment;
import ink.ptms.chemdah.taboolib.module.kether.action.transform.CheckType;
import ink.ptms.chemdah.taboolib.module.nms.NMSItemTagKt;
import ink.ptms.chemdah.taboolib.module.nms.NMSTranslateKt;
import ink.ptms.chemdah.taboolib.platform.util.ItemMatcherKt;
import ink.ptms.chemdah.util.StringKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kotlin.Metadata;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u0000 \u00122\u00020\u0001:\u0002\u0012\u0013B\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u0016\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u000e\u001a\u00020\t2\u0006\u0010\u000f\u001a\u00020\u0010J\u0016\u0010\u0011\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItem;", "", "items", "", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "(Ljava/util/List;)V", "getItems", "()Ljava/util/List;", "check", "", "inventory", "Lorg/bukkit/inventory/Inventory;", "amount", "", "isItem", "item", "Lorg/bukkit/inventory/ItemStack;", "take", "Companion", "Item", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferItem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,137:1\n1747#2,3:138\n*S KotlinDebug\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem\n*L\n33#1:138,3\n*E\n"})
public final class InferItem {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final List<Item> items;

    public InferItem(@NotNull List<? extends Item> items) {
        Intrinsics.checkNotNullParameter(items, (String)"items");
        this.items = items;
    }

    @NotNull
    public final List<Item> getItems() {
        return this.items;
    }

    public final boolean isItem(@NotNull ItemStack item2) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Iterable $this$any$iv = this.items;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    Item it = (Item)element$iv;
                    boolean bl2 = false;
                    if (!it.match(item2)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    public final boolean check(@NotNull Inventory inventory, int amount) {
        Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
        return ItemMatcherKt.hasItem((Inventory)inventory, (int)amount, (Function1)((Function1)new Function1<ItemStack, Boolean>(this){
            final /* synthetic */ InferItem this$0;
            {
                this.this$0 = $receiver;
                super(1);
            }

            @NotNull
            public final Boolean invoke(@NotNull ItemStack it) {
                boolean bl;
                block3: {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    Iterable $this$any$iv = this.this$0.getItems();
                    boolean $i$f$any = false;
                    if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                        bl = false;
                    } else {
                        for (T element$iv : $this$any$iv) {
                            Item item2 = (Item)element$iv;
                            boolean bl2 = false;
                            if (!item2.match(it)) continue;
                            bl = true;
                            break block3;
                        }
                        bl = false;
                    }
                }
                return bl;
            }
        }));
    }

    public final boolean take(@NotNull Inventory inventory, int amount) {
        Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
        return ItemMatcherKt.takeItem$default((Inventory)inventory, (int)amount, null, (Function1)((Function1)new Function1<ItemStack, Boolean>(this){
            final /* synthetic */ InferItem this$0;
            {
                this.this$0 = $receiver;
                super(1);
            }

            @NotNull
            public final Boolean invoke(@NotNull ItemStack it) {
                boolean bl;
                block3: {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    Iterable $this$any$iv = this.this$0.getItems();
                    boolean $i$f$any = false;
                    if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                        bl = false;
                    } else {
                        for (T element$iv : $this$any$iv) {
                            Item item2 = (Item)element$iv;
                            boolean bl2 = false;
                            if (!item2.match(it)) continue;
                            bl = true;
                            break block3;
                        }
                        bl = false;
                    }
                }
                return bl;
            }
        }), (int)2, null);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005J\u0010\u0010\u0003\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u00050\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItem$Companion;", "", "()V", "toInferItem", "Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "", "Link/ptms/chemdah/core/quest/selector/InferItem;", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferItem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,137:1\n1549#2:138\n1620#2,3:139\n*S KotlinDebug\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem$Companion\n*L\n133#1:138\n133#1:139,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Item toInferItem(@NotNull String $this$toInferItem) {
            Intrinsics.checkNotNullParameter((Object)$this$toInferItem, (String)"<this>");
            return DataMatchHandler.INSTANCE.getItemParser().parse($this$toInferItem);
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final InferItem toInferItem(@NotNull List<String> $this$toInferItem) {
            void $this$mapTo$iv$iv;
            Intrinsics.checkNotNullParameter($this$toInferItem, (String)"<this>");
            Iterable $this$map$iv = $this$toInferItem;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String string = (String)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                collection.add(Companion.toInferItem((String)it));
            }
            List list2 = (List)destination$iv$iv;
            return new InferItem(list2);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0016\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u0018\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019H\u0016J\u0010\u0010\u001a\u001a\u00020\u00152\u0006\u0010\u001b\u001a\u00020\u001cH\u0016J\u0010\u0010\u001d\u001a\u00020\u00152\u0006\u0010\u001b\u001a\u00020\u001cH\u0016J\"\u0010\u001d\u001a\u00020\u00152\u0006\u0010\u001b\u001a\u00020\u001c2\b\u0010\u001e\u001a\u0004\u0018\u00010\u001f2\u0006\u0010 \u001a\u00020\bH\u0016J\u0010\u0010!\u001a\u00020\u00152\u0006\u0010\"\u001a\u00020\u0003H\u0016J\u0018\u0010#\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0019H\u0016R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000b\"\u0004\b\u000f\u0010\rR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013\u00a8\u0006$"}, d2={"Link/ptms/chemdah/core/quest/selector/InferItem$Item;", "", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getData", "()Ljava/util/List;", "setData", "(Ljava/util/List;)V", "getFlags", "setFlags", "getMaterial", "()Ljava/lang/String;", "setMaterial", "(Ljava/lang/String;)V", "check", "", "inventory", "Lorg/bukkit/inventory/Inventory;", "amount", "", "match", "item", "Lorg/bukkit/inventory/ItemStack;", "matchMetaData", "itemMeta", "Lorg/bukkit/inventory/meta/ItemMeta;", "dataMatch", "matchType", "type", "take", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferItem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem$Item\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,137:1\n1747#2,3:138\n1726#2,2:141\n1747#2,3:144\n1747#2,3:147\n1747#2,3:150\n1728#2:153\n1#3:143\n*S KotlinDebug\n*F\n+ 1 InferItem.kt\nink/ptms/chemdah/core/quest/selector/InferItem$Item\n*L\n47#1:138,3\n52#1:141,2\n62#1:144,3\n75#1:147,3\n84#1:150,3\n52#1:153\n*E\n"})
    public static class Item {
        @NotNull
        private String material;
        @NotNull
        private List<? extends Flags> flags;
        @NotNull
        private List<DataMatch> data;

        public Item(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            this.material = material;
            this.flags = flags;
            this.data = data2;
        }

        @NotNull
        public final String getMaterial() {
            return this.material;
        }

        public final void setMaterial(@NotNull String string) {
            Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
            this.material = string;
        }

        @NotNull
        public final List<Flags> getFlags() {
            return this.flags;
        }

        public final void setFlags(@NotNull List<? extends Flags> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.flags = list2;
        }

        @NotNull
        public final List<DataMatch> getData() {
            return this.data;
        }

        public final void setData(@NotNull List<DataMatch> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.data = list2;
        }

        public boolean match(@NotNull ItemStack item2) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            String string = item2.getType().name().toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            return this.matchType(string) && this.matchMetaData(item2);
        }

        public boolean matchType(@NotNull String type) {
            boolean bl;
            block3: {
                Intrinsics.checkNotNullParameter((Object)type, (String)"type");
                Iterable $this$any$iv = this.flags;
                boolean $i$f$any = false;
                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                    bl = false;
                } else {
                    for (Object element$iv : $this$any$iv) {
                        Flags it = (Flags)((Object)element$iv);
                        boolean bl2 = false;
                        if (!((Boolean)it.getMatch().invoke((Object)type, (Object)this.material)).booleanValue()) continue;
                        bl = true;
                        break block3;
                    }
                    bl = false;
                }
            }
            return bl;
        }

        /*
         * Unable to fully structure code
         * Could not resolve type clashes
         */
        public boolean matchMetaData(@NotNull ItemStack item) {
            block58: {
                block59: {
                    Intrinsics.checkNotNullParameter((Object)item, (String)"item");
                    meta = item.getItemMeta();
                    $this$all$iv = this.data;
                    $i$f$all = false;
                    if (!($this$all$iv instanceof Collection) || !((Collection)$this$all$iv).isEmpty()) break block59;
                    v0 = true;
                    break block58;
                }
                for (T element$iv : $this$all$iv) {
                    it = (DataMatch)element$iv;
                    $i$a$-all-InferItem$Item$matchMetaData$1 = false;
                    var9_9 = it.getKey();
                    tmp = -1;
                    switch (var9_9.hashCode()) {
                        case -1339126929: {
                            if (var9_9.equals("damage")) {
                                tmp = 1;
                            }
                            break;
                        }
                        case 3117774: {
                            if (var9_9.equals("ench")) {
                                tmp = 2;
                            }
                            break;
                        }
                        case 1704673082: {
                            if (var9_9.equals("enchants")) {
                                tmp = 2;
                            }
                            break;
                        }
                        case -982431341: {
                            if (var9_9.equals("potion")) {
                                tmp = 3;
                            }
                            break;
                        }
                        case 3327734: {
                            if (var9_9.equals("lore")) {
                                tmp = 4;
                            }
                            break;
                        }
                        case 222399799: {
                            if (var9_9.equals("enchantment")) {
                                tmp = 2;
                            }
                            break;
                        }
                        case 716086281: {
                            if (var9_9.equals("durability")) {
                                tmp = 1;
                            }
                            break;
                        }
                        case -1607578535: {
                            if (var9_9.equals("enchant")) {
                                tmp = 2;
                            }
                            break;
                        }
                        case -390600384: {
                            if (var9_9.equals("potions")) {
                                tmp = 3;
                            }
                            break;
                        }
                        case 3373707: {
                            if (var9_9.equals("name")) {
                                tmp = 5;
                            }
                            break;
                        }
                        case 1472374698: {
                            if (var9_9.equals("custom-model-data")) {
                                tmp = 6;
                            }
                            break;
                        }
                        case 104069929: {
                            if (var9_9.equals("model")) {
                                tmp = 6;
                            }
                            break;
                        }
                    }
                    block15 : switch (tmp) {
                        case 5: {
                            var10_10 = this;
                            var11_11 = it;
                            try {
                                $this$matchMetaData_u24lambda_u247_u24lambda_u241 = var10_10;
                                $i$a$-runCatching-InferItem$Item$matchMetaData$1$1 = false;
                                $this$matchMetaData_u24lambda_u247_u24lambda_u241 = Result.constructor-impl((Object)NMSTranslateKt.getName$default((ItemStack)item, null, (int)1, null));
                            }
                            catch (Throwable $i$a$-runCatching-InferItem$Item$matchMetaData$1$1) {
                                $this$matchMetaData_u24lambda_u247_u24lambda_u241 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)$i$a$-runCatching-InferItem$Item$matchMetaData$1$1));
                            }
                            v1 = var11_11;
                            var10_10 = $this$matchMetaData_u24lambda_u247_u24lambda_u241;
                            v2 = Result.exceptionOrNull-impl((Object)var10_10);
                            if (v2 == null) {
                                v3 = var10_10;
                            } else {
                                $this$matchMetaData_u24lambda_u247_u24lambda_u241 = v2;
                                var11_11 = v1;
                                $i$a$-getOrElse-InferItem$Item$matchMetaData$1$2 = false;
                                v4 /* !! */  = item.getItemMeta();
                                if (v4 /* !! */  == null || (v4 /* !! */  = v4 /* !! */ .getDisplayName()) == null) {
                                    v4 /* !! */  = item.getType().name();
                                }
                                Intrinsics.checkNotNullExpressionValue((Object)v4 /* !! */ , (String)"item.itemMeta?.displayName ?: item.type.name");
                                v3 = v4 /* !! */ ;
                                v1 = var11_11;
                            }
                            v5 = v1.check(v3, CheckType.IN);
                            break;
                        }
                        case 4: {
                            if (it.isInt()) {
                                v6 = meta;
                                v5 = DataMatch.check$default(it, v6 != null && (v6 = v6.getLore()) != null ? v6.size() : 0, null, 2, null);
                                break;
                            }
                            v7 /* !! */  = meta;
                            if (v7 /* !! */  != null && (v7 /* !! */  = v7 /* !! */ .getLore()) != null) {
                                $this$any$iv = (Iterable)v7 /* !! */ ;
                                $i$f$any = false;
                                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                                    v5 = false;
                                    break;
                                }
                                for (T element$iv : $this$any$iv) {
                                    e = (String)element$iv;
                                    $i$a$-any-InferItem$Item$matchMetaData$1$3 = false;
                                    Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
                                    if (!DataMatch.check$default(it, e, null, 2, null)) continue;
                                    v5 = true;
                                    break block15;
                                }
                                v5 = false;
                                break;
                            }
                            v5 = false;
                            break;
                        }
                        case 1: {
                            v5 = DataMatch.check$default(it, item.getDurability(), null, 2, null);
                            break;
                        }
                        case 6: {
                            v8 = meta;
                            v5 = DataMatch.check$default(it, v8 != null ? v8.getCustomModelData() : 0, null, 2, null);
                            break;
                        }
                        case 2: {
                            if (it.isInt()) {
                                v9 = meta;
                                v5 = DataMatch.check$default(it, v9 != null && (v9 = v9.getEnchants()) != null ? v9.size() : 0, null, 2, null);
                                break;
                            }
                            $this$any$iv = DataMatchHandler.INSTANCE.getItemParser().getEnchantments(item);
                            $i$f$any = false;
                            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                                v5 = false;
                                break;
                            }
                            $this$any$iv = $this$any$iv.iterator();
                            while ($this$any$iv.hasNext()) {
                                element$iv = $this$any$iv.next();
                                ench = (ItemEnchant)element$iv;
                                $i$a$-any-InferItem$Item$matchMetaData$1$4 = false;
                                if (!DataMatch.check$default(it, ench.getName(), null, 2, null)) continue;
                                v5 = true;
                                break block15;
                            }
                            v5 = false;
                            break;
                        }
                        case 3: {
                            if (!(meta instanceof PotionMeta)) ** GOTO lbl188
                            if (it.isInt()) {
                                v5 = DataMatch.check$default(it, ((PotionMeta)meta).getCustomEffects().size(), null, 2, null);
                                break;
                            }
                            if (DataMatch.check$default(it, ((PotionMeta)meta).getBasePotionData().getType().name(), null, 2, null)) ** GOTO lbl184
                            v10 = ((PotionMeta)meta).getCustomEffects();
                            Intrinsics.checkNotNullExpressionValue((Object)v10, (String)"meta.customEffects");
                            $this$any$iv = v10;
                            $i$f$any = false;
                            if (!($this$any$iv instanceof Collection) || !((Collection)$this$any$iv).isEmpty()) ** GOTO lbl172
                            v11 = false;
                            ** GOTO lbl183
lbl172:
                            // 1 sources

                            $this$any$iv = $this$any$iv.iterator();
                            while ($this$any$iv.hasNext()) {
                                element$iv = $this$any$iv.next();
                                e = (PotionEffect)element$iv;
                                $i$a$-any-InferItem$Item$matchMetaData$1$5 = false;
                                v12 = e.getType().getName();
                                Intrinsics.checkNotNullExpressionValue((Object)v12, (String)"e.type.name");
                                if (!DataMatch.check$default(it, v12, null, 2, null)) continue;
                                v11 = true;
                                ** GOTO lbl183
                            }
                            v11 = false;
lbl183:
                            // 3 sources

                            if (!v11) ** GOTO lbl186
lbl184:
                            // 2 sources

                            v5 = true;
                            break;
lbl186:
                            // 1 sources

                            v5 = false;
                            break;
lbl188:
                            // 1 sources

                            v5 = false;
                            break;
                        }
                        default: {
                            $this$any$iv = new String[]{"ench.", "enchant.", "enchantment."};
                            if (!StringKt.startsWithAny(it.getKey(), (String[])$this$any$iv)) ** GOTO lbl215
                            $i$f$any = new String[]{"ench.", "enchant.", "enchantment."};
                            args /* !! */  = StringKt.substringAfterAny(it.getKey(), $i$f$any);
                            v13 = XEnchantment.matchXEnchantment((String)args /* !! */ );
                            Intrinsics.checkNotNullExpressionValue((Object)v13, (String)"matchXEnchantment(args)");
                            v14 /* !! */  = (String[])OptionalKt.orNull((Optional)v13);
                            if (v14 /* !! */  == null || (v14 /* !! */  = v14 /* !! */ .name()) == null) {
                                v14 /* !! */  = args /* !! */ ;
                            }
                            name /* !! */  = v14 /* !! */ ;
                            var15_26 = DataMatchHandler.INSTANCE.getItemParser().getEnchantments(item);
                            var16_27 = var15_26.iterator();
                            while (var16_27.hasNext()) {
                                var17_30 = var16_27.next();
                                ench = (ItemEnchant)var17_30;
                                $i$a$-find-InferItem$Item$matchMetaData$1$ench$1 = false;
                                if (!CoerceExtensionsKt.eqic((String)ench.getName(), (String)name /* !! */ )) continue;
                                v15 = var17_30;
                                ** GOTO lbl212
                            }
                            v15 = null;
lbl212:
                            // 2 sources

                            v16 = ench = (ItemEnchant)v15;
                            v5 = DataMatch.check$default(it, v16 != null ? v16.getLevel() : 0, null, 2, null);
                            break;
lbl215:
                            // 1 sources

                            args /* !! */  = new String[]{"nbt.", "tag."};
                            if (StringKt.startsWithAny(it.getKey(), args /* !! */ )) {
                                var13_22 = new String[]{"nbt.", "tag."};
                                v17 = NMSItemTagKt.getItemTag$default((ItemStack)item, (boolean)false, (int)1, null).getDeep(StringKt.substringAfterAny(it.getKey(), var13_22));
                                v18 = data = v17 != null ? v17.unsafeData() : null;
                                if (v18 != null) {
                                    v5 = DataMatch.check$default(it, v18, null, 2, null);
                                    break;
                                }
                                v5 = false;
                                break;
                            }
                            v5 = this.matchMetaData(item, meta, it);
                        }
                    }
                    if (v5) continue;
                    v0 = false;
                    break block58;
                }
                v0 = true;
            }
            return v0;
        }

        public boolean matchMetaData(@NotNull ItemStack item2, @Nullable ItemMeta itemMeta, @NotNull DataMatch dataMatch) {
            Intrinsics.checkNotNullParameter((Object)item2, (String)"item");
            Intrinsics.checkNotNullParameter((Object)dataMatch, (String)"dataMatch");
            Object[] objectArray = new Object[]{this.material + '[' + dataMatch.getKey() + ' ' + dataMatch.getType() + ' ' + dataMatch.getValue() + "] not supported."};
            IOKt.warning((Object[])objectArray);
            return false;
        }

        public boolean check(@NotNull Inventory inventory, int amount) {
            Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
            return ItemMatcherKt.hasItem((Inventory)inventory, (int)amount, (Function1)((Function1)new Function1<ItemStack, Boolean>(this){
                final /* synthetic */ Item this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(@NotNull ItemStack it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    return this.this$0.match(it);
                }
            }));
        }

        public boolean take(@NotNull Inventory inventory, int amount) {
            Intrinsics.checkNotNullParameter((Object)inventory, (String)"inventory");
            return ItemMatcherKt.takeItem$default((Inventory)inventory, (int)amount, null, (Function1)((Function1)new Function1<ItemStack, Boolean>(this){
                final /* synthetic */ Item this$0;
                {
                    this.this$0 = $receiver;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(@NotNull ItemStack it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    return this.this$0.match(it);
                }
            }), (int)2, null);
        }
    }
}

