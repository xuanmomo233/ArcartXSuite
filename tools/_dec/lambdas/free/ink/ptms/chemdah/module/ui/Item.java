/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.xseries.XItemStack
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell$VariableMap
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XItemStack;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J*\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\r2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0004J \u0010\u0015\u001a\u00020\b2\u0006\u0010\u0016\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00070\b\u00a2\u0006\u0002\b\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/module/ui/Item;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "itemStackBase", "Lorg/bukkit/inventory/ItemStack;", "Lorg/jetbrains/annotations/NotNull;", "getItemStackBase", "()Lorg/bukkit/inventory/ItemStack;", "format", "", "str", "player", "Link/ptms/chemdah/core/PlayerProfile;", "ui", "Link/ptms/chemdah/module/ui/UI;", "template", "Link/ptms/chemdah/core/quest/Template;", "getItemStack", "profile", "Chemdah"})
public class Item {
    @NotNull
    private final ConfigurationSection config;
    @NotNull
    private final ItemStack itemStackBase;

    public Item(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.config = config;
        ItemStack itemStack = XItemStack.deserialize((ConfigurationSection)this.config, Item::itemStackBase$lambda$0);
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"deserialize(config) { it.colored() }");
        this.itemStackBase = itemStack;
    }

    @NotNull
    public final ConfigurationSection getConfig() {
        return this.config;
    }

    @NotNull
    public final ItemStack getItemStackBase() {
        return this.itemStackBase;
    }

    @NotNull
    public ItemStack getItemStack(@NotNull PlayerProfile profile, @NotNull UI ui2, @NotNull Template template) {
        ItemStack itemStack;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        ItemStack itemStack2 = this.itemStackBase.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack2, (String)"itemStackBase.clone()");
        ItemStack item2 = itemStack = itemStack2;
        boolean bl = false;
        ItemModifierKt.modifyMeta((ItemStack)item2, (Function1)((Function1)new Function1<ItemMeta, Unit>(this, profile, ui2, template){
            final /* synthetic */ Item this$0;
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ UI $ui;
            final /* synthetic */ Template $template;
            {
                this.this$0 = $receiver;
                this.$profile = $profile;
                this.$ui = $ui;
                this.$template = $template;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                List list2;
                Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                $this$modifyMeta.setDisplayName(this.this$0.format($this$modifyMeta.getDisplayName(), this.$profile, this.$ui, this.$template));
                ItemMeta itemMeta = $this$modifyMeta;
                List list3 = $this$modifyMeta.getLore();
                if (list3 != null) {
                    void $this$mapTo$iv$iv;
                    void $this$map$iv;
                    Iterable iterable = list3;
                    Item item2 = this.this$0;
                    PlayerProfile playerProfile2 = this.$profile;
                    UI uI = this.$ui;
                    Template template = this.$template;
                    ItemMeta itemMeta2 = itemMeta;
                    boolean $i$f$map = false;
                    void var8_9 = $this$map$iv;
                    Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                    boolean $i$f$mapTo = false;
                    for (T item$iv$iv : $this$mapTo$iv$iv) {
                        void lore;
                        String string = (String)item$iv$iv;
                        Collection collection = destination$iv$iv;
                        boolean bl = false;
                        collection.add(item2.format((String)lore, playerProfile2, uI, template));
                    }
                    list2 = (List)destination$iv$iv;
                    itemMeta = itemMeta2;
                } else {
                    list2 = null;
                }
                itemMeta.setLore(list2);
            }
        }));
        return itemStack;
    }

    @NotNull
    protected final String format(@Nullable String str, @NotNull PlayerProfile player2, @NotNull UI ui2, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        String string = str;
        if (string == null) {
            return "null";
        }
        List<String> list2 = UtilsForKetherKt.getNamespaceQuestUI();
        Player player3 = player2.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player3, (String)"player.player");
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)"@QuestUI", (Object)ui2), TuplesKt.to((Object)"@QuestSelected", (Object)template.getNode())};
        return UtilKt.colored((String)KetherFunction.parse$default((KetherFunction)KetherFunction.INSTANCE, (String)string, (boolean)false, list2, null, (ProxyCommandSender)AdapterKt.adaptCommandSender((Object)player3), (KetherShell.VariableMap)new KetherShell.VariableMap(pairArray), null, (int)74, null));
    }

    private static final String itemStackBase$lambda$0(String it) {
        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
        return UtilKt.colored((String)it);
    }
}

