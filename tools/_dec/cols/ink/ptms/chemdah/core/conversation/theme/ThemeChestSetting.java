/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation.theme;

import ink.ptms.chemdah.core.conversation.theme.ThemeChestSetting;
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.xseries.XItemStackKt;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u001f\u001a\u00020\u0011R\u0013\u0010\u0005\u001a\u00020\u00068F\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0013\u0010\r\u001a\u00020\u00068F\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\bR\u001a\u0010\u000f\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00060\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0013\u0010\u0012\u001a\u00020\u00068F\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\bR\u0017\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\n0\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0018\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0016\u0010\u001b\u001a\u00070\u0011\u00a2\u0006\u0002\b\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001a\u00a8\u0006 "}, d2={"Link/ptms/chemdah/core/conversation/theme/ThemeChestSetting;", "Link/ptms/chemdah/core/conversation/theme/ThemeSettings;", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "npcItem", "Lorg/bukkit/inventory/ItemStack;", "getNpcItem", "()Lorg/bukkit/inventory/ItemStack;", "npcSlot", "", "getNpcSlot", "()I", "playerItem", "getPlayerItem", "playerItemCustom", "", "", "playerItemSelected", "getPlayerItemSelected", "playerSlot", "", "getPlayerSlot", "()Ljava/util/List;", "rows", "getRows", "()Ljava/lang/String;", "title", "Lorg/jetbrains/annotations/NotNull;", "getTitle", "getCustomPlayerItem", "name", "Chemdah"})
public final class ThemeChestSetting
extends ThemeSettings {
    @NotNull
    private final String title;
    @NotNull
    private final String rows;
    @NotNull
    private final ItemStack npcItem;
    @NotNull
    private final ItemStack playerItem;
    @NotNull
    private final ItemStack playerItemSelected;
    @NotNull
    private final Map<String, ItemStack> playerItemCustom;
    private final int npcSlot;
    @NotNull
    private final List<Integer> playerSlot;

    public ThemeChestSetting(@NotNull ConfigurationSection root2) {
        ItemStack itemStack;
        ItemStack itemStack2;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        super(root2);
        String string = root2.getString("ui.title", "");
        Intrinsics.checkNotNull((Object)string);
        this.title = UtilKt.colored((String)string);
        String string2 = root2.getString("ui.rows", "1");
        Intrinsics.checkNotNull((Object)string2);
        this.rows = string2;
        ItemStack itemStack3 = XItemStackKt.getItemStack((ConfigurationSection)root2, (String)"npc-side.item");
        if (itemStack3 == null) {
            itemStack3 = this.npcItem = new ItemStack(Material.STONE);
        }
        if ((itemStack2 = XItemStackKt.getItemStack((ConfigurationSection)root2, (String)"player-side.item")) == null) {
            itemStack2 = this.playerItem = new ItemStack(Material.STONE);
        }
        if ((itemStack = XItemStackKt.getItemStack((ConfigurationSection)root2, (String)"player-side.item-selected")) == null) {
            itemStack = new ItemStack(Material.STONE);
        }
        this.playerItemSelected = itemStack;
        this.playerItemCustom = SectionsKt.mapSection((ConfigurationSection)root2, (String)"player-side.item-custom", (Function1)playerItemCustom.1.INSTANCE);
        this.npcSlot = root2.getInt("npc-side.slot");
        this.playerSlot = root2.getIntegerList("player-side.slot");
    }

    @NotNull
    public final String getTitle() {
        return this.title;
    }

    @NotNull
    public final String getRows() {
        return this.rows;
    }

    @NotNull
    public final ItemStack getNpcItem() {
        ItemStack itemStack = this.npcItem.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"field.clone()");
        return itemStack;
    }

    @NotNull
    public final ItemStack getPlayerItem() {
        ItemStack itemStack = this.playerItem.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"field.clone()");
        return itemStack;
    }

    @NotNull
    public final ItemStack getPlayerItemSelected() {
        ItemStack itemStack = this.playerItemSelected.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"field.clone()");
        return itemStack;
    }

    public final int getNpcSlot() {
        return this.npcSlot;
    }

    @NotNull
    public final List<Integer> getPlayerSlot() {
        return this.playerSlot;
    }

    @Nullable
    public final ItemStack getCustomPlayerItem(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        ItemStack itemStack = this.playerItemCustom.get(name);
        return itemStack != null ? itemStack.clone() : null;
    }
}

