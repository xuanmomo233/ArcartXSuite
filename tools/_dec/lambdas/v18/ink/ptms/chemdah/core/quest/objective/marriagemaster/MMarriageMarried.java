/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarryEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.marriagemaster;

import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarryEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="MarriageMaster")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/marriagemaster/MMarriageMarried;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lat/pcgamingfreaks/MarriageMaster/Bukkit/API/Events/MarryEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class MMarriageMarried
extends ObjectiveCountableI<MarryEvent> {
    @NotNull
    public static final MMarriageMarried INSTANCE = new MMarriageMarried();
    @NotNull
    private static final String name = "marriage marry";
    @NotNull
    private static final Class<MarryEvent> event = MarryEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(MarryEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer1().getPlayerOnline();
    }

    /*
     * Enabled aggressive block sorting
     */
    private static final Boolean _init_$lambda$1(Data data2, MarryEvent it) {
        boolean bl;
        InferArea inferArea = data2.toPosition();
        Player player = it.getPlayer1().getPlayerOnline();
        Intrinsics.checkNotNull((Object)player);
        Location location = player.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.player1.playerOnline!!.location");
        if (inferArea.inside(location)) {
            InferArea inferArea2 = data2.toPosition();
            Player player2 = it.getPlayer2().getPlayerOnline();
            Intrinsics.checkNotNull((Object)player2);
            Location location2 = player2.getLocation();
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"it.player2.playerOnline!!.location");
            if (inferArea2.inside(location2)) {
                bl = true;
                return bl;
            }
        }
        bl = false;
        return bl;
    }

    static {
        INSTANCE.handler(MMarriageMarried::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", MMarriageMarried::_init_$lambda$1);
    }
}

