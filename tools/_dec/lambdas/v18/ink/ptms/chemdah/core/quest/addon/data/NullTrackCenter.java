/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0001\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\b\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/quest/addon/data/NullTrackCenter;", "Link/ptms/chemdah/core/quest/addon/data/TrackCenter;", "()V", "getLocation", "", "player", "Lorg/bukkit/entity/Player;", "identifier", "", "Chemdah"})
public final class NullTrackCenter
implements TrackCenter {
    @NotNull
    public static final NullTrackCenter INSTANCE = new NullTrackCenter();

    private NullTrackCenter() {
    }

    @Override
    @NotNull
    public String identifier() {
        return "null";
    }

    @Nullable
    public Void getLocation(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        return null;
    }
}

