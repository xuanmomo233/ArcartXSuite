/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.api.event.PlayerCastSkillEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.skillapi;

import com.sucy.skill.api.event.PlayerCastSkillEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="SkillAPI")
@MetaInfo(source="SkillAPI", name="SkillAPI \u65bd\u653e\u6280\u80fd\u76ee\u6807", description={"\u65bd\u653e SkillAPI \u6280\u80fd", "\u652f\u6301\u4f4d\u7f6e\u3001\u6280\u80fd\u540d\u79f0\u3001\u9b54\u6cd5\u6d88\u8017\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 SkillAPI \u63d2\u4ef6\u652f\u6301"}, alias={"skillapi\u65bd\u653e\u6280\u80fd", "\u91ca\u653e\u6280\u80fd", "\u4f7f\u7528\u6280\u80fd"}, params={@ParamInfo(name="position", type="Location", description="\u68c0\u67e5\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="skill", type="String", description="\u65bd\u653e\u7684\u6280\u80fd\u540d\u79f0"), @ParamInfo(name="mana", type="Number", description="\u6280\u80fd\u7684\u9b54\u6cd5\u6d88\u8017\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/skillapi/SCastSkill;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/sucy/skill/api/event/PlayerCastSkillEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class SCastSkill
extends ObjectiveCountableI<PlayerCastSkillEvent> {
    @NotNull
    public static final SCastSkill INSTANCE = new SCastSkill();
    @NotNull
    private static final String name = "skillapi castskill";

    private SCastSkill() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerCastSkillEvent> getEvent() {
        return PlayerCastSkillEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerCastSkillEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerCastSkillEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerCastSkillEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getSkill().getData().getName();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerCastSkillEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getSkill().getManaCost();
    }

    private static final Object _init_$lambda$4(PlayerCastSkillEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getSkill().getManaCost();
    }

    static {
        INSTANCE.handler(SCastSkill::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", SCastSkill::_init_$lambda$1);
        INSTANCE.addCondition("skill", "String", SCastSkill::_init_$lambda$2);
        INSTANCE.addCondition("mana", "Number", SCastSkill::_init_$lambda$3);
        INSTANCE.addConditionVariable("mana", SCastSkill::_init_$lambda$4);
    }
}

