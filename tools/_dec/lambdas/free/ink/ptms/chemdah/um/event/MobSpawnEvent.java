/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.event;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.MobType;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u000b\u0018\u00002\u00020\u0001B\u001f\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0006\u0010\u0011\u001a\u00020\u0000R\u001a\u0010\u0006\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/um/event/MobSpawnEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "mob", "Link/ptms/chemdah/um/Mob;", "mobType", "Link/ptms/chemdah/um/MobType;", "level", "", "(Link/ptms/chemdah/um/Mob;Link/ptms/chemdah/um/MobType;D)V", "getLevel", "()D", "setLevel", "(D)V", "getMob", "()Link/ptms/chemdah/um/Mob;", "getMobType", "()Link/ptms/chemdah/um/MobType;", "fire", "common"})
public final class MobSpawnEvent
extends BukkitProxyEvent {
    @Nullable
    private final Mob mob;
    @NotNull
    private final MobType mobType;
    private double level;

    public MobSpawnEvent(@Nullable Mob mob, @NotNull MobType mobType, double level) {
        Intrinsics.checkNotNullParameter((Object)mobType, (String)"mobType");
        this.mob = mob;
        this.mobType = mobType;
        this.level = level;
    }

    @Nullable
    public final Mob getMob() {
        return this.mob;
    }

    @NotNull
    public final MobType getMobType() {
        return this.mobType;
    }

    public final double getLevel() {
        return this.level;
    }

    public final void setLevel(double d) {
        this.level = d;
    }

    @NotNull
    public final MobSpawnEvent fire() {
        this.call();
        return this;
    }
}

