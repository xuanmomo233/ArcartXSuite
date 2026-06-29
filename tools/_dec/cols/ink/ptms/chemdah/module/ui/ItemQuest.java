/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonUI;
import ink.ptms.chemdah.module.ui.ItemQuestNoIcon;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.util.UtilsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0016\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/ui/ItemQuest;", "Link/ptms/chemdah/module/ui/ItemQuestNoIcon;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "getItemStack", "Lorg/bukkit/inventory/ItemStack;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "ui", "Link/ptms/chemdah/module/ui/UI;", "template", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
public class ItemQuest
extends ItemQuestNoIcon {
    public ItemQuest(@NotNull ConfigurationSection config) {
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        super(config);
    }

    @Override
    @NotNull
    public ItemStack getItemStack(@NotNull PlayerProfile profile, @NotNull UI ui2, @NotNull Template template) {
        AddonUI addonUI;
        ItemStack itemStack;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)ui2, (String)"ui");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        ItemStack item2 = itemStack = super.getItemStack(profile, ui2, template);
        boolean bl = false;
        AddonUI addonUI2 = addonUI = AddonUI.Companion.ui(template);
        if ((addonUI2 != null ? addonUI2.getIcon() : null) != null) {
            String string = addonUI.getIcon();
            Intrinsics.checkNotNull((Object)string);
            UtilsKt.setIcon(item2, string);
        }
        return itemStack;
    }
}

