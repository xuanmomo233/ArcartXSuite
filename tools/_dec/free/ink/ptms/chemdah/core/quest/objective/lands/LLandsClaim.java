/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  me.angeschossen.lands.api.events.ChunkPostClaimEvent
 *  me.angeschossen.lands.api.player.LandPlayer
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.lands;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import me.angeschossen.lands.api.events.ChunkPostClaimEvent;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Lands")
@MetaInfo(source="Lands", name="Lands \u9886\u5730\u58f0\u660e\u76ee\u6807", description={"\u58f0\u660e Lands \u9886\u5730\u533a\u5757", "\u652f\u6301\u4f4d\u7f6e\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Lands \u63d2\u4ef6\u652f\u6301"}, alias={"lands\u58f0\u660e", "\u58f0\u660e\u9886\u5730", "\u5708\u5730"}, params={@ParamInfo(name="position", type="string", required=false, description="\u73a9\u5bb6\u58f0\u660e\u533a\u5757\u65f6\u7684\u4f4d\u7f6e\u6761\u4ef6")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/lands/LLandsClaim;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lme/angeschossen/lands/api/events/ChunkPostClaimEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class LLandsClaim
extends ObjectiveCountableI<ChunkPostClaimEvent> {
    @NotNull
    public static final LLandsClaim INSTANCE = new LLandsClaim();
    @NotNull
    private static final String name = "lands claim";

    private LLandsClaim() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ChunkPostClaimEvent> getEvent() {
        return ChunkPostClaimEvent.class;
    }

    private static final Player _init_$lambda$0(ChunkPostClaimEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LandPlayer landPlayer = it.getLandPlayer();
        return landPlayer != null ? landPlayer.getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ChunkPostClaimEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LandPlayer landPlayer = it.getLandPlayer();
        Intrinsics.checkNotNull((Object)landPlayer);
        return landPlayer.getPlayer().getLocation();
    }

    static {
        INSTANCE.handler(LLandsClaim::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", LLandsClaim::_init_$lambda$1);
    }
}

