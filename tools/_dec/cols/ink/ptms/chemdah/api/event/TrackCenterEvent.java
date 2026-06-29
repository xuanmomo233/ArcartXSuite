/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event;

import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\r\u001a\u00020\u0000R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u001c\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\f\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/api/event/TrackCenterEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "center", "", "(Ljava/lang/String;)V", "getCenter", "()Ljava/lang/String;", "trackCenter", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "getTrackCenter", "()Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "setTrackCenter", "(Link/ptms/chemdah/core/quest/addon/data/TrackCenter;)V", "fire", "Chemdah"})
public final class TrackCenterEvent
extends BukkitProxyEvent {
    @NotNull
    private final String center;
    @Nullable
    private TrackCenter trackCenter;

    public TrackCenterEvent(@NotNull String center2) {
        Intrinsics.checkNotNullParameter((Object)center2, (String)"center");
        this.center = center2;
    }

    @NotNull
    public final String getCenter() {
        return this.center;
    }

    @Nullable
    public final TrackCenter getTrackCenter() {
        return this.trackCenter;
    }

    public final void setTrackCenter(@Nullable TrackCenter trackCenter) {
        this.trackCenter = trackCenter;
    }

    @NotNull
    public final TrackCenterEvent fire() {
        this.call();
        return this;
    }
}

