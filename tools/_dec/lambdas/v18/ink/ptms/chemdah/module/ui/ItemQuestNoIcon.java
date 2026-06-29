/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonUI;
import ink.ptms.chemdah.core.quest.meta.MetaName;
import ink.ptms.chemdah.module.ui.Item;
import ink.ptms.chemdah.module.ui.UI;
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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/ui/ItemQuestNoIcon;", "Link/ptms/chemdah/module/ui/Item;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getItemStack", "Lorg/bukkit/inventory/ItemStack;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "ui", "Link/ptms/chemdah/module/ui/UI;", "template", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
public class ItemQuestNoIcon
extends Item {
    public ItemQuestNoIcon(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        super(config);
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
        AddonUI addonUI = AddonUI.Companion.ui(template);
        ItemModifierKt.modifyMeta((ItemStack)item2, (Function1)((Function1)new Function1<ItemMeta, Unit>(this, template, profile, ui2, addonUI){
            final /* synthetic */ ItemQuestNoIcon this$0;
            final /* synthetic */ Template $template;
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ UI $ui;
            final /* synthetic */ AddonUI $addonUI;
            {
                this.this$0 = $receiver;
                this.$template = $template;
                this.$profile = $profile;
                this.$ui = $ui;
                this.$addonUI = $addonUI;
                super(1);
            }

            /*
             * Unable to fully structure code
             */
            public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                block7: {
                    block4: {
                        Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                        v0 = $this$modifyMeta.getDisplayName();
                        Intrinsics.checkNotNullExpressionValue((Object)v0, (String)"displayName");
                        var2_2 = new Pair[]{TuplesKt.to((Object)"name", (Object)this.this$0.format(MetaName.Companion.displayName(this.$template, false), this.$profile, this.$ui, this.$template))};
                        $this$modifyMeta.setDisplayName(StringKt.replace(v0, var2_2));
                        v1 = $this$modifyMeta;
                        v2 = $this$modifyMeta.getLore();
                        if (v2 == null) break block4;
                        var3_3 = v2;
                        var4_4 = this.$addonUI;
                        var5_5 = this.$profile;
                        var6_6 = this.this$0;
                        var7_7 = this.$ui;
                        var8_8 = this.$template;
                        var29_9 = v1;
                        $i$f$flatMap = false;
                        var10_11 = $this$flatMap$iv;
                        destination$iv$iv = new ArrayList<E>();
                        $i$f$flatMapTo = false;
                        for (T element$iv$iv : $this$flatMapTo$iv$iv) {
                            block6: {
                                block5: {
                                    lore = (String)element$iv$iv;
                                    $i$a$-flatMap-ItemQuestNoIcon$getItemStack$1$1$1 = false;
                                    Intrinsics.checkNotNullExpressionValue((Object)lore, (String)"lore");
                                    if (!StringsKt.contains$default((CharSequence)lore, (CharSequence)"description", (boolean)false, (int)2, null)) break block5;
                                    v3 = var4_4;
                                    if (v3 == null || (v3 = v3.getDescription()) == null) ** GOTO lbl-1000
                                    v4 = var5_5.getPlayer().getLocale();
                                    Intrinsics.checkNotNullExpressionValue((Object)v4, (String)"profile.player.locale");
                                    if ((v3 = v3.get(v4)) != null) {
                                        $this$map$iv = (Iterable)v3;
                                        $i$f$map = false;
                                        var19_20 = $this$map$iv;
                                        destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                                        $i$f$mapTo = false;
                                        for (T item$iv$iv : $this$mapTo$iv$iv) {
                                            var24_25 = (String)item$iv$iv;
                                            var25_26 = destination$iv$iv;
                                            $i$a$-map-ItemQuestNoIcon$getItemStack$1$1$1$1 = false;
                                            var27_28 = new Pair[]{TuplesKt.to((Object)"description", (Object)var6_6.format((String)it, var5_5, var7_7, var8_8))};
                                            var25_26.add(StringKt.replace(lore, var27_28));
                                        }
                                        v5 = (List)destination$iv$iv;
                                    } else lbl-1000:
                                    // 2 sources

                                    {
                                        v5 = CollectionsKt.emptyList();
                                    }
                                    break block6;
                                }
                                v5 = CollectionsKt.listOf((Object)lore);
                            }
                            list$iv$iv = v5;
                            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
                        }
                        v6 = (List)destination$iv$iv;
                        v1 = var29_9;
                        break block7;
                    }
                    v6 = null;
                }
                v1.setLore(v6);
                var2_2 = ItemFlag.values();
                $this$modifyMeta.addItemFlags((ItemFlag[])Arrays.copyOf(var2_2, var2_2.length));
            }
        }));
        return itemStack;
    }
}

