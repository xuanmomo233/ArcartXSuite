/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerToggleFlight;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerToggleFlightEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerToggleFlight
extends ObjectiveCountableI<PlayerToggleFlightEvent> {
    @NotNull
    public static final IPlayerToggleFlight INSTANCE = new IPlayerToggleFlight();
    @NotNull
    private static final String name = "player toggle flight";
    @NotNull
    private static final Class<PlayerToggleFlightEvent> event = PlayerToggleFlightEvent.class;

    private IPlayerToggleFlight() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerToggleFlightEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerToggleFlightEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerToggleFlightEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerToggleFlightEvent e) {
        return data2.toBoolean() == e.isFlying();
    }

    static {
        INSTANCE.handler(IPlayerToggleFlight::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerToggleFlight::_init_$lambda$1);
        INSTANCE.addSimpleCondition("flying", IPlayerToggleFlight::_init_$lambda$2);
    }
}

