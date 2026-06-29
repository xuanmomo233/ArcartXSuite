/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0002\b\r\u0018\u00002\u00020\u0001B1\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\u0002\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u001c\u0010\t\u001a\u0004\u0018\u00010\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/api/event/PlaceholderHookEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "player", "Lorg/bukkit/entity/Player;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "identifier", "", "parameter", "result", "", "(Lorg/bukkit/entity/Player;Link/ptms/chemdah/core/PlayerProfile;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V", "getIdentifier", "()Ljava/lang/String;", "getParameter", "getPlayer", "()Lorg/bukkit/entity/Player;", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "getResult", "()Ljava/lang/Object;", "setResult", "(Ljava/lang/Object;)V", "Chemdah"})
public final class PlaceholderHookEvent
extends BukkitProxyEvent {
    @NotNull
    private final Player player;
    @NotNull
    private final PlayerProfile profile;
    @NotNull
    private final String identifier;
    @NotNull
    private final String parameter;
    @Nullable
    private Object result;

    public PlaceholderHookEvent(@NotNull Player player, @NotNull PlayerProfile profile, @NotNull String identifier, @NotNull String parameter, @Nullable Object result) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)identifier, (String)"identifier");
        Intrinsics.checkNotNullParameter((Object)parameter, (String)"parameter");
        this.player = player;
        this.profile = profile;
        this.identifier = identifier;
        this.parameter = parameter;
        this.result = result;
    }

    public /* synthetic */ PlaceholderHookEvent(Player player, PlayerProfile playerProfile, String string, String string2, Object object, int n, DefaultConstructorMarker defaultConstructorMarker) {
        if ((n & 0x10) != 0) {
            object = null;
        }
        this(player, playerProfile, string, string2, object);
    }

    @NotNull
    public final Player getPlayer() {
        return this.player;
    }

    @NotNull
    public final PlayerProfile getProfile() {
        return this.profile;
    }

    @NotNull
    public final String getIdentifier() {
        return this.identifier;
    }

    @NotNull
    public final String getParameter() {
        return this.parameter;
    }

    @Nullable
    public final Object getResult() {
        return this.result;
    }

    public final void setResult(@Nullable Object object) {
        this.result = object;
    }
}

