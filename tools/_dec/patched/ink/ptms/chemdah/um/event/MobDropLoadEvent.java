/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.event;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.um.item.DropMeta;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u000f\u001a\u00020\u0000J\u001a\u0010\u0010\u001a\u00020\u00112\u0012\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R9\u0010\u0007\u001a*\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t0\bj\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t`\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/um/event/MobDropLoadEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "dropName", "", "(Ljava/lang/String;)V", "getDropName", "()Ljava/lang/String;", "itemDrops", "Ljava/util/ArrayList;", "Ljava/util/function/Function;", "Link/ptms/chemdah/um/item/DropMeta;", "Lorg/bukkit/inventory/ItemStack;", "Lkotlin1822/collections/ArrayList;", "getItemDrops", "()Ljava/util/ArrayList;", "fire", "registerItem", "", "func", "common"})
public final class MobDropLoadEvent
extends BukkitProxyEvent {
    @NotNull
    private final String dropName;
    @NotNull
    private final ArrayList<Function<DropMeta, ItemStack>> itemDrops;

    public MobDropLoadEvent(@NotNull String dropName) {
        Intrinsics.checkNotNullParameter((Object)dropName, (String)"dropName");
        this.dropName = dropName;
        this.itemDrops = new ArrayList();
    }

    @NotNull
    public final String getDropName() {
        return this.dropName;
    }

    @NotNull
    public final ArrayList<Function<DropMeta, ItemStack>> getItemDrops() {
        return this.itemDrops;
    }

    public final void registerItem(@NotNull Function<DropMeta, ItemStack> func) {
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Collection)this.itemDrops).add(func);
    }

    @NotNull
    public final MobDropLoadEvent fire() {
        this.call();
        return this;
    }
}

