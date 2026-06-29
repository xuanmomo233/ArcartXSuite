/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelSystem;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(source="chemdah", name="\u81ea\u5b9a\u4e49\u7b49\u7ea7\u76ee\u6807", description={"\u68c0\u67e5\u73a9\u5bb6\u81ea\u5b9a\u4e49\u7b49\u7ea7\u662f\u5426\u8fbe\u5230\u8981\u6c42", "\u652f\u6301\u4f4d\u7f6e\u3001\u7b49\u7ea7ID\u3001\u7b49\u7ea7\u6570\u503c\u7b49\u6761\u4ef6\u5224\u65ad", "\u9002\u7528\u4e8e Chemdah \u7b49\u7ea7\u7cfb\u7edf"}, alias={"\u81ea\u5b9a\u4e49\u7b49\u7ea7", "\u7b49\u7ea7\u68c0\u67e5", "customlevel"}, params={@ParamInfo(name="position", type="location", description="\u68c0\u6d4b\u4f4d\u7f6e\u6761\u4ef6"), @ParamInfo(name="id", type="string", required=true, description="\u7b49\u7ea7\u7cfb\u7edfID"), @ParamInfo(name="level", type="number", required=true, description="\u76ee\u6807\u7b49\u7ea7\u6570\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/other/ICustomLevel;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/chemdah/api/event/collect/PlayerEvents$LevelChange;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class ICustomLevel
extends ObjectiveCountableI<PlayerEvents.LevelChange> {
    @NotNull
    public static final ICustomLevel INSTANCE = new ICustomLevel();
    @NotNull
    private static final String name = "custom level";

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
        return PlayerEvents.LevelChange.class;
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

    private static final Object _init_$lambda$1(PlayerProfile profile, Task task, PlayerEvents.LevelChange levelChange) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)levelChange), (String)"<anonymous parameter 2>");
        return profile.getPlayer().getLocation();
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
        INSTANCE.addCondition("position", "Location", ICustomLevel::_init_$lambda$1);
        INSTANCE.addGoal("id", "String", ICustomLevel::_init_$lambda$2);
        INSTANCE.addPlaceholderGoal("level", "Number");
        INSTANCE.addGoalVariable("level", ICustomLevel::_init_$lambda$3);
    }
}

