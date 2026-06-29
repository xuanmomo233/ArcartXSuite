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
package ink.ptms.chemdah.module.party;

import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001:\u0001\u0006J\u0012\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0007\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/module/party/Party;", "", "getParty", "Link/ptms/chemdah/module/party/Party$PartyInfo;", "player", "Lorg/bukkit/entity/Player;", "PartyInfo", "Chemdah"})
public interface Party {
    @Nullable
    public PartyInfo getParty(@NotNull Player var1);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\n\u0010\u0002\u001a\u0004\u0018\u00010\u0003H&J\u000e\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005H&J\u0010\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0003H\u0016\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\t\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/module/party/Party$PartyInfo;", "", "getLeader", "Lorg/bukkit/entity/Player;", "getMembers", "", "isLeader", "", "player", "Chemdah"})
    public static interface PartyInfo {
        default public boolean isLeader(@NotNull Player player2) {
            Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
            Player player3 = this.getLeader();
            return Intrinsics.areEqual((Object)(player3 != null ? player3.getUniqueId() : null), (Object)player2.getUniqueId());
        }

        @Nullable
        public Player getLeader();

        @NotNull
        public List<Player> getMembers();
    }
}

