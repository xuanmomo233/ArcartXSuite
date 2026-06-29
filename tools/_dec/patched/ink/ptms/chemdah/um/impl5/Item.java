/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  io.lumine.mythic.api.adapters.AbstractItemStack
 *  io.lumine.mythic.api.config.MythicConfig
 *  io.lumine.mythic.core.items.MythicItem
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.impl5.MobConfiguration;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.core.items.MythicItem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0005\u001a\u00020\u0006H\u0016R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\fR\u0016\u0010\r\u001a\u0004\u0018\u00010\u000e8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\u000e8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/um/impl5/Item;", "Link/ptms/chemdah/um/Item;", "source", "Lio/lumine/mythic/core/items/MythicItem;", "(Lio/lumine/mythic/core/items/MythicItem;)V", "amount", "", "getAmount", "()I", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "internalName", "getInternalName", "getSource", "()Lio/lumine/mythic/core/items/MythicItem;", "generateItemStack", "Lorg/bukkit/inventory/ItemStack;", "implementation-v5"})
public final class Item
implements ink.ptms.chemdah.um.Item {
    @NotNull
    private final MythicItem source;

    public Item(@NotNull MythicItem source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
    }

    @NotNull
    public final MythicItem getSource() {
        return this.source;
    }

    @Override
    @NotNull
    public String getInternalName() {
        String string = this.source.getInternalName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getInternalName(...)");
        return string;
    }

    @Override
    public int getAmount() {
        return this.source.getAmount();
    }

    @Override
    @Nullable
    public String getDisplayName() {
        return this.source.getDisplayName();
    }

    @Override
    @NotNull
    public ConfigurationSection getConfig() {
        MythicConfig mythicConfig = this.source.getConfig();
        Intrinsics.checkNotNullExpressionValue((Object)mythicConfig, (String)"getConfig(...)");
        return new MobConfiguration(mythicConfig);
    }

    @Override
    @NotNull
    public ItemStack generateItemStack(int amount) {
        AbstractItemStack abstractItemStack = this.source.generateItemStack(amount);
        Intrinsics.checkNotNullExpressionValue((Object)abstractItemStack, (String)"generateItemStack(...)");
        return UtilsKt.toBukkit(abstractItemStack);
    }
}

