/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  me.angeschossen.lands.api.events.LandInvitePlayerEvent
 *  me.angeschossen.lands.api.player.LandPlayer
 *  org.bukkit.Bukkit
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
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import me.angeschossen.lands.api.events.LandInvitePlayerEvent;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="Lands")
@MetaInfo(source="Lands", name="Lands \u9886\u5730\u9080\u8bf7\u76ee\u6807", description={"\u9080\u8bf7\u73a9\u5bb6\u52a0\u5165 Lands \u9886\u5730", "\u652f\u6301\u4f4d\u7f6e\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 Lands \u63d2\u4ef6\u652f\u6301"}, alias={"lands\u9080\u8bf7", "\u9080\u8bf7\u73a9\u5bb6", "\u9886\u5730\u9080\u8bf7"}, params={@ParamInfo(name="position", type="string", required=false, description="\u9080\u8bf7\u73a9\u5bb6\u65f6\u7684\u4f4d\u7f6e\u6761\u4ef6")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/lands/LLandsInvite;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lme/angeschossen/lands/api/events/LandInvitePlayerEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class LLandsInvite
extends ObjectiveCountableI<LandInvitePlayerEvent> {
    @NotNull
    public static final LLandsInvite INSTANCE = new LLandsInvite();
    @NotNull
    private static final String name = "lands invite";

    private LLandsInvite() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<LandInvitePlayerEvent> getEvent() {
        return LandInvitePlayerEvent.class;
    }

    private static final Player _init_$lambda$0(LandInvitePlayerEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LandPlayer landPlayer = it.getLandPlayer();
        return landPlayer != null ? landPlayer.getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, LandInvitePlayerEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Player player2 = Bukkit.getPlayer((UUID)it.getTargetUUID());
        Intrinsics.checkNotNull((Object)player2);
        return player2.getLocation();
    }

    static {
        INSTANCE.handler(LLandsInvite::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", LLandsInvite::_init_$lambda$1);
    }
}

