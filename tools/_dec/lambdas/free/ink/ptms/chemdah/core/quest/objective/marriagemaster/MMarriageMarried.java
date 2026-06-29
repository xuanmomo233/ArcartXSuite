/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarryEvent
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.marriagemaster;

import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarryEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="MarriageMaster")
@MetaInfo(source="MarriageMaster", name="MarriageMaster \u7ed3\u5a5a\u76ee\u6807", description={"\u73a9\u5bb6\u7ed3\u5a5a", "\u652f\u6301\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 MarriageMaster \u63d2\u4ef6\u652f\u6301"}, alias={"\u7ed3\u5a5a", "\u5a5a\u793c", "marry"}, params={@ParamInfo(name="position", type="Location", description="\u7ed3\u5a5a\u65f6\u7684\u4f4d\u7f6e")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/marriagemaster/MMarriageMarried;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lat/pcgamingfreaks/MarriageMaster/Bukkit/API/Events/MarryEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class MMarriageMarried
extends ObjectiveCountableI<MarryEvent> {
    @NotNull
    public static final MMarriageMarried INSTANCE = new MMarriageMarried();
    @NotNull
    private static final String name = "marriage marry";

    private MMarriageMarried() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<MarryEvent> getEvent() {
        return MarryEvent.class;
    }

    private static final Player _init_$lambda$0(MarryEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer1().getPlayerOnline();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, MarryEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object[] objectArray = new Location[2];
        Player player2 = it.getPlayer1().getPlayerOnline();
        Intrinsics.checkNotNull((Object)player2);
        objectArray[0] = player2.getLocation();
        Player player3 = it.getPlayer2().getPlayerOnline();
        Intrinsics.checkNotNull((Object)player3);
        objectArray[1] = player3.getLocation();
        return CollectionsKt.listOf((Object[])objectArray);
    }

    static {
        INSTANCE.handler(MMarriageMarried::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", MMarriageMarried::_init_$lambda$1);
    }
}

