/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.api.event.PlayerComboFinishEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.skillapi;

import com.sucy.skill.api.event.PlayerComboFinishEvent;
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
@MetaInfo(source="SkillAPI", name="SkillAPI \u8fde\u62db\u5b8c\u6210\u76ee\u6807", description={"\u5b8c\u6210 SkillAPI \u6280\u80fd\u8fde\u62db", "\u652f\u6301\u4f4d\u7f6e\u3001\u6280\u80fd\u540d\u79f0\u3001\u8fde\u62db\u6570\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 SkillAPI \u63d2\u4ef6\u652f\u6301"}, alias={"skillapi\u8fde\u62db", "\u6280\u80fd\u8fde\u51fb", "combo\u8fde\u62db"}, params={@ParamInfo(name="position", type="Location", description="\u68c0\u67e5\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="skill", type="String", description="\u8fde\u62db\u7684\u6280\u80fd\u540d\u79f0"), @ParamInfo(name="combo", type="Number", description="\u5b8c\u6210\u7684\u8fde\u62db\u6570")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/skillapi/SCombo;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/sucy/skill/api/event/PlayerComboFinishEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class SCombo
extends ObjectiveCountableI<PlayerComboFinishEvent> {
    @NotNull
    public static final SCombo INSTANCE = new SCombo();
    @NotNull
    private static final String name = "skillapi combo";

    private SCombo() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerComboFinishEvent> getEvent() {
        return PlayerComboFinishEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getSkill().getName();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCombo();
    }

    private static final Object _init_$lambda$4(PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCombo();
    }

    static {
        INSTANCE.handler(SCombo::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", SCombo::_init_$lambda$1);
        INSTANCE.addCondition("skill", "String", SCombo::_init_$lambda$2);
        INSTANCE.addCondition("combo", "Number", SCombo::_init_$lambda$3);
        INSTANCE.addConditionVariable("combo", SCombo::_init_$lambda$4);
    }
}

