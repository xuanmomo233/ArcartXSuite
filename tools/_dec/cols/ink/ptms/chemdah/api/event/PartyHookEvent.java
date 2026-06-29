/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event;

import ink.ptms.chemdah.module.party.Party;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\t\u0018\u00002\u00020\u0001B\u000f\b\u0016\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007R\u0014\u0010\b\u001a\u00020\t8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u001c\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/api/event/PartyHookEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "plugin", "", "(Ljava/lang/String;)V", "party", "Link/ptms/chemdah/module/party/Party;", "(Ljava/lang/String;Link/ptms/chemdah/module/party/Party;)V", "allowCancelled", "", "getAllowCancelled", "()Z", "getParty", "()Link/ptms/chemdah/module/party/Party;", "setParty", "(Link/ptms/chemdah/module/party/Party;)V", "getPlugin", "()Ljava/lang/String;", "Chemdah"})
public final class PartyHookEvent
extends BukkitProxyEvent {
    @NotNull
    private final String plugin;
    @Nullable
    private Party party;

    public PartyHookEvent(@NotNull String plugin2, @Nullable Party party) {
        Intrinsics.checkNotNullParameter((Object)plugin2, (String)"plugin");
        this.plugin = plugin2;
        this.party = party;
    }

    @NotNull
    public final String getPlugin() {
        return this.plugin;
    }

    @Nullable
    public final Party getParty() {
        return this.party;
    }

    public final void setParty(@Nullable Party party) {
        this.party = party;
    }

    public PartyHookEvent(@NotNull String plugin2) {
        Intrinsics.checkNotNullParameter((Object)plugin2, (String)"plugin");
        this(plugin2, null);
    }

    public boolean getAllowCancelled() {
        return false;
    }
}

