/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelSystem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/other/ICustomLevel;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/chemdah/api/event/collect/PlayerEvents$LevelChange;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class ICustomLevel
extends ObjectiveCountableI<PlayerEvents.LevelChange> {
    @NotNull
    public static final ICustomLevel INSTANCE = new ICustomLevel();
    @NotNull
    private static final String name = "custom level";
    @NotNull
    private static final Class<PlayerEvents.LevelChange> event = PlayerEvents.LevelChange.class;

    private ICustomLevel() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerEvents.LevelChange> getEvent() {
        return event;
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        LevelOption levelOption = LevelSystem.INSTANCE.getLevelOption(String.valueOf(task.getGoal().get("id")));
        if (levelOption == null) {
            return Progress.Companion.getZERO();
        }
        LevelOption option = levelOption;
        int target = task.getGoal().get("level", 1).toInt();
        return this.hasCompletedSignature(profile, task) ? Progress.Companion.toProgress(target, target, 1.0) : Progress.Companion.toProgress$default(Progress.Companion, LevelSystem.INSTANCE.getLevel(profile, option).getLevel(), target, 0.0, 2, null);
    }

    private static final Player _init_$lambda$0(PlayerEvents.LevelChange it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(PlayerProfile profile, Task task, PlayerEvents.LevelChange levelChange) {
        Data data2 = task.getCondition().get("position");
        Intrinsics.checkNotNull((Object)data2);
        InferArea inferArea = data2.toPosition();
        Location location = profile.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"profile.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(PlayerProfile profile, Task task) {
        LevelOption levelOption = LevelSystem.INSTANCE.getLevelOption(String.valueOf(task.getGoal().get("id")));
        if (levelOption == null) {
            return false;
        }
        LevelOption option = levelOption;
        Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
        return LevelSystem.INSTANCE.getLevel(profile, option).getLevel() >= task.getGoal().get("level", 1).toInt();
    }

    private static final Object _init_$lambda$3(PlayerProfile profile, Task task) {
        LevelOption levelOption = LevelSystem.INSTANCE.getLevelOption(String.valueOf(task.getGoal().get("id")));
        if (levelOption == null) {
            return -1;
        }
        LevelOption option = levelOption;
        Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
        return LevelSystem.INSTANCE.getLevel(profile, option).getLevel();
    }

    static {
        INSTANCE.handler(ICustomLevel::_init_$lambda$0);
        INSTANCE.addFullCondition("position", ICustomLevel::_init_$lambda$1);
        INSTANCE.addGoal("id, level", ICustomLevel::_init_$lambda$2);
        INSTANCE.addGoalVariable("level", ICustomLevel::_init_$lambda$3);
    }
}

