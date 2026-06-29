/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerLevelChangeEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u7b49\u7ea7\u53d8\u5316\u76ee\u6807", description={"\u73a9\u5bb6\u7b49\u7ea7\u53d8\u5316", "\u652f\u6301\u7b49\u7ea7\u5dee\u3001\u65b0\u7b49\u7ea7\u3001\u65e7\u7b49\u7ea7\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7b49\u7ea7\u63d0\u5347\u91cf"}, alias={"\u7b49\u7ea7\u53d8\u5316", "\u5347\u7ea7", "\u7b49\u7ea7\u63d0\u5347"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="level", type="Number", description="\u7b49\u7ea7\u53d8\u5316\u91cf\uff08\u65b0\u7b49\u7ea7-\u65e7\u7b49\u7ea7\uff09"), @ParamInfo(name="level:new", type="Number", description="\u53d8\u5316\u540e\u7684\u7b49\u7ea7"), @ParamInfo(name="level:old", type="Number", description="\u53d8\u5316\u524d\u7684\u7b49\u7ea7")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerLevelChange;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerLevelChangeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerLevelChange
extends ObjectiveCountableI<PlayerLevelChangeEvent> {
    @NotNull
    public static final IPlayerLevelChange INSTANCE = new IPlayerLevelChange();
    @NotNull
    private static final String name = "level change";

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
        return PlayerLevelChangeEvent.class;
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

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewLevel() - it.getOldLevel();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewLevel();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerLevelChangeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getOldLevel();
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
        INSTANCE.addCondition("position", "Location", IPlayerLevelChange::_init_$lambda$1);
        INSTANCE.addCondition("level", "Number", IPlayerLevelChange::_init_$lambda$2);
        INSTANCE.addCondition("level:new", "Number", IPlayerLevelChange::_init_$lambda$3);
        INSTANCE.addCondition("level:old", "Number", IPlayerLevelChange::_init_$lambda$4);
        INSTANCE.addConditionVariable("level", IPlayerLevelChange::_init_$lambda$5);
        INSTANCE.addConditionVariable("level:new", IPlayerLevelChange::_init_$lambda$6);
        INSTANCE.addConditionVariable("level:old", IPlayerLevelChange::_init_$lambda$7);
    }
}

