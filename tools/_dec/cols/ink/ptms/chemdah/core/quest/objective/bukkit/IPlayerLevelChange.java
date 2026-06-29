/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerLevelChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerLevelChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerLevelChange
extends ObjectiveCountableI<PlayerLevelChangeEvent> {
    @NotNull
    public static final IPlayerLevelChange INSTANCE = new IPlayerLevelChange();
    @NotNull
    private static final String name = "level change";
    @NotNull
    private static final Class<PlayerLevelChangeEvent> event = PlayerLevelChangeEvent.class;

    private IPlayerLevelChange() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerLevelChangeEvent> getEvent() {
        return event;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull PlayerLevelChangeEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getNewLevel() - event.getOldLevel();
    }

    private static final Player _init_$lambda$0(PlayerLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerLevelChangeEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerLevelChangeEvent e) {
        return data2.toConditionNumber().check(e.getNewLevel() - e.getOldLevel());
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerLevelChangeEvent e) {
        return data2.toConditionNumber().check(e.getNewLevel());
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerLevelChangeEvent e) {
        return data2.toConditionNumber().check(e.getOldLevel());
    }

    private static final Object _init_$lambda$5(PlayerLevelChangeEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return e.getNewLevel() - e.getOldLevel();
    }

    private static final Object _init_$lambda$6(PlayerLevelChangeEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return e.getNewLevel();
    }

    private static final Object _init_$lambda$7(PlayerLevelChangeEvent e) {
        Intrinsics.checkNotNullParameter((Object)e, (String)"e");
        return e.getOldLevel();
    }

    static {
        INSTANCE.handler(IPlayerLevelChange::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerLevelChange::_init_$lambda$1);
        INSTANCE.addSimpleCondition("level", IPlayerLevelChange::_init_$lambda$2);
        INSTANCE.addSimpleCondition("level:new", IPlayerLevelChange::_init_$lambda$3);
        INSTANCE.addSimpleCondition("level:old", IPlayerLevelChange::_init_$lambda$4);
        INSTANCE.addConditionVariable("level", IPlayerLevelChange::_init_$lambda$5);
        INSTANCE.addConditionVariable("level:new", IPlayerLevelChange::_init_$lambda$6);
        INSTANCE.addConditionVariable("level:old", IPlayerLevelChange::_init_$lambda$7);
    }
}

