/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.plugin.Plugin
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.papi;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.module.papi.PlaceholderData;
import ink.ptms.chemdah.util.Function2;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\"\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\u001a2\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u001a\u0010\u0006\u001a\u0016\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\u0005\u0012\u0006\u0012\u0004\u0018\u00010\t0\u0007\u00a8\u0006\n"}, d2={"newPlaceholder", "", "plugin", "Lorg/bukkit/plugin/Plugin;", "identifier", "", "data", "Link/ptms/chemdah/util/Function2;", "Link/ptms/chemdah/core/PlayerProfile;", "", "Chemdah"})
public final class PlaceholderDataKt {
    public static final void newPlaceholder(@NotNull Plugin plugin2, @NotNull String identifier, @NotNull Function2<PlayerProfile, String, Object> data2) {
        Intrinsics.checkNotNullParameter((Object)plugin2, (String)"plugin");
        Intrinsics.checkNotNullParameter((Object)identifier, (String)"identifier");
        Intrinsics.checkNotNullParameter(data2, (String)"data");
        new PlaceholderData(plugin2, data2).register(identifier);
    }
}

