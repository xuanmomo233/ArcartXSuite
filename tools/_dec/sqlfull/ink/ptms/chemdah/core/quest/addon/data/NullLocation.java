/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.addon.data;

import kotlin.Metadata;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0003"}, d2={"Link/ptms/chemdah/core/quest/addon/data/NullLocation;", "Lorg/bukkit/Location;", "()V", "Chemdah"})
public final class NullLocation
extends Location {
    @NotNull
    public static final NullLocation INSTANCE = new NullLocation();

    private NullLocation() {
        super(null, 0.0, 0.0, 0.0);
    }
}

