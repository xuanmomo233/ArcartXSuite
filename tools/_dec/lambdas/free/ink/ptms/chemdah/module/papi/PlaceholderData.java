/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.plugin.Plugin
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.papi;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.module.papi.PlaceholderForLiteral;
import ink.ptms.chemdah.util.Function2;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u001a\u0010\u0004\u001a\u0016\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u0007\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0005\u00a2\u0006\u0002\u0010\bJ\u0018\u0010\r\u001a\u0004\u0018\u00010\u00012\u0006\u0010\u000e\u001a\u00020\u00062\u0006\u0010\u000f\u001a\u00020\u0007J\u000e\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0007R%\u0010\u0004\u001a\u0016\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u0007\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/module/papi/PlaceholderData;", "", "plugin", "Lorg/bukkit/plugin/Plugin;", "data", "Link/ptms/chemdah/util/Function2;", "Link/ptms/chemdah/core/PlayerProfile;", "", "(Lorg/bukkit/plugin/Plugin;Link/ptms/chemdah/util/Function2;)V", "getData", "()Link/ptms/chemdah/util/Function2;", "getPlugin", "()Lorg/bukkit/plugin/Plugin;", "getResult", "player", "placeholder", "register", "", "identifier", "Chemdah"})
public final class PlaceholderData {
    @NotNull
    private final Plugin plugin;
    @NotNull
    private final Function2<PlayerProfile, String, Object> data;

    public PlaceholderData(@NotNull Plugin plugin2, @NotNull Function2<PlayerProfile, String, Object> data2) {
        Intrinsics.checkNotNullParameter((Object)plugin2, (String)"plugin");
        Intrinsics.checkNotNullParameter(data2, (String)"data");
        this.plugin = plugin2;
        this.data = data2;
    }

    @NotNull
    public final Plugin getPlugin() {
        return this.plugin;
    }

    @NotNull
    public final Function2<PlayerProfile, String, Object> getData() {
        return this.data;
    }

    @Nullable
    public final Object getResult(@NotNull PlayerProfile player2, @NotNull String placeholder) {
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)placeholder, (String)"placeholder");
        return this.data.invoke(player2, placeholder);
    }

    public final void register(@NotNull String identifier) {
        Intrinsics.checkNotNullParameter((Object)identifier, (String)"identifier");
        PlaceholderForLiteral.INSTANCE.registerPlaceholder(identifier, this);
    }
}

