/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.event;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.um.Mob;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\t\u0018\u00002\u00020\u0001B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\u000e\b\u0002\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\u0002\u0010\tJ\u0006\u0010\u0010\u001a\u00020\u0000R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/um/event/MobDeathEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "mob", "Link/ptms/chemdah/um/Mob;", "killer", "Lorg/bukkit/entity/LivingEntity;", "drop", "", "Lorg/bukkit/inventory/ItemStack;", "(Link/ptms/chemdah/um/Mob;Lorg/bukkit/entity/LivingEntity;Ljava/util/List;)V", "getDrop", "()Ljava/util/List;", "getKiller", "()Lorg/bukkit/entity/LivingEntity;", "getMob", "()Link/ptms/chemdah/um/Mob;", "fire", "common"})
public final class MobDeathEvent
extends BukkitProxyEvent {
    @NotNull
    private final Mob mob;
    @Nullable
    private final LivingEntity killer;
    @NotNull
    private final List<ItemStack> drop;

    public MobDeathEvent(@NotNull Mob mob, @Nullable LivingEntity killer, @NotNull List<ItemStack> drop) {
        Intrinsics.checkNotNullParameter((Object)mob, (String)"mob");
        Intrinsics.checkNotNullParameter(drop, (String)"drop");
        this.mob = mob;
        this.killer = killer;
        this.drop = drop;
    }

    public /* synthetic */ MobDeathEvent(Mob mob, LivingEntity livingEntity, List list2, int n, DefaultConstructorMarker defaultConstructorMarker) {
        if ((n & 4) != 0) {
            list2 = new ArrayList();
        }
        this(mob, livingEntity, list2);
    }

    @NotNull
    public final Mob getMob() {
        return this.mob;
    }

    @Nullable
    public final LivingEntity getKiller() {
        return this.killer;
    }

    @NotNull
    public final List<ItemStack> getDrop() {
        return this.drop;
    }

    @NotNull
    public final MobDeathEvent fire() {
        this.call();
        return this;
    }
}

