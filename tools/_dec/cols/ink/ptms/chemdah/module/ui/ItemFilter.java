/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.ui.Item;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.util.StringKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0016R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/ui/ItemFilter;", "Link/ptms/chemdah/module/ui/Item;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "allKey", "", "getAllKey", "()Ljava/lang/String;", "getItemStack", "Lorg/bukkit/inventory/ItemStack;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "ui", "Link/ptms/chemdah/module/ui/UI;", "template", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
public class ItemFilter
extends Item {
    @NotNull
    private final String allKey;

    public ItemFilter(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        super(config);
        String string = config.getString("all-key");
        Intrinsics.checkNotNull((Object)string);
        this.allKey = string;
    }

    @NotNull
    public final String getAllKey() {
        return this.allKey;
    }

    @Override
    @NotNull
    public ItemStack getItemStack(@NotNull PlayerProfile profile, @NotNull UI ui2, @NotNull Template template) {
        ItemStack itemStack;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        ItemStack item2 = itemStack = super.getItemStack(profile, ui2, template);
        boolean bl = false;
        ItemModifierKt.modifyMeta((ItemStack)item2, (Function1)((Function1)new Function1<ItemMeta, Unit>(ui2, profile, this){
            final /* synthetic */ UI $ui;
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ ItemFilter this$0;
            {
                this.$ui = $ui;
                this.$profile = $profile;
                this.this$0 = $receiver;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                List list2;
                Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                ItemMeta itemMeta = $this$modifyMeta;
                List list3 = $this$modifyMeta.getLore();
                if (list3 != null) {
                    void $this$flatMapTo$iv$iv;
                    void $this$flatMap$iv;
                    Iterable iterable = list3;
                    UI uI = this.$ui;
                    PlayerProfile playerProfile = this.$profile;
                    ItemFilter itemFilter = this.this$0;
                    ItemMeta itemMeta2 = itemMeta;
                    boolean $i$f$flatMap = false;
                    void var8_8 = $this$flatMap$iv;
                    Collection destination$iv$iv = new ArrayList<E>();
                    boolean $i$f$flatMapTo = false;
                    for (T element$iv$iv : $this$flatMapTo$iv$iv) {
                        List list4;
                        String lore = (String)element$iv$iv;
                        boolean bl = false;
                        Intrinsics.checkNotNullExpressionValue((Object)lore, (String)"lore");
                        if (StringsKt.contains$default((CharSequence)lore, (CharSequence)"filter", (boolean)false, (int)2, null)) {
                            void $this$mapTo$iv$iv;
                            Object[] objectArray = new String[]{itemFilter.getAllKey()};
                            List<String> list5 = uI.getPlayerFilters().getOrDefault(playerProfile.getUniqueId(), CollectionsKt.arrayListOf((Object[])objectArray));
                            Intrinsics.checkNotNullExpressionValue(list5, (String)"ui.playerFilters.getOrDe\u2026eId, arrayListOf(allKey))");
                            List filters = CollectionsKt.toMutableList((Collection)list5);
                            if (filters.isEmpty()) {
                                filters.add(itemFilter.getAllKey());
                            }
                            Iterable $this$map$iv = filters;
                            boolean $i$f$map = false;
                            Iterable iterable2 = $this$map$iv;
                            Collection destination$iv$iv2 = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                            boolean $i$f$mapTo = false;
                            for (T item$iv$iv : $this$mapTo$iv$iv) {
                                void it;
                                String string = (String)item$iv$iv;
                                Collection collection = destination$iv$iv2;
                                boolean bl2 = false;
                                Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"filter", (Object)it)};
                                collection.add(StringKt.replace(lore, pairArray));
                            }
                            list4 = (List)destination$iv$iv2;
                        } else {
                            list4 = CollectionKt.asList((Object)lore);
                        }
                        Iterable list$iv$iv = list4;
                        CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                    }
                    list2 = (List)destination$iv$iv;
                    itemMeta = itemMeta2;
                } else {
                    list2 = null;
                }
                itemMeta.setLore(list2);
                ItemFlag[] itemFlagArray = ItemFlag.values();
                $this$modifyMeta.addItemFlags(Arrays.copyOf(itemFlagArray, itemFlagArray.length));
            }
        }));
        return itemStack;
    }
}

