/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.ui;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007R\u0013\u0010\u0004\u001a\u00020\u00058F\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\u0006\u001a\u00020\u00058F\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/ui/Include;", "", "id", "", "activeItem", "Lorg/bukkit/inventory/ItemStack;", "normalItem", "(Ljava/lang/String;Lorg/bukkit/inventory/ItemStack;Lorg/bukkit/inventory/ItemStack;)V", "getActiveItem", "()Lorg/bukkit/inventory/ItemStack;", "getId", "()Ljava/lang/String;", "getNormalItem", "Chemdah"})
public final class Include {
    @NotNull
    private final String id;
    @NotNull
    private final ItemStack activeItem;
    @NotNull
    private final ItemStack normalItem;

    public Include(@NotNull String id2, @NotNull ItemStack activeItem, @NotNull ItemStack normalItem) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)activeItem, (String)"activeItem");
        Intrinsics.checkNotNullParameter((Object)normalItem, (String)"normalItem");
        this.id = id2;
        this.activeItem = activeItem;
        this.normalItem = normalItem;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final ItemStack getActiveItem() {
        ItemStack itemStack = this.activeItem.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"field.clone()");
        return itemStack;
    }

    @NotNull
    public final ItemStack getNormalItem() {
        ItemStack itemStack = this.normalItem.clone();
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"field.clone()");
        return itemStack;
    }
}

