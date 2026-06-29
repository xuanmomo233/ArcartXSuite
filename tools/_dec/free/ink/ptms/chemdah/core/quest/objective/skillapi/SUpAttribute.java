/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.api.event.PlayerUpAttributeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.skillapi;

import com.sucy.skill.api.event.PlayerUpAttributeEvent;
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
@MetaInfo(source="SkillAPI", name="SkillAPI \u5c5e\u6027\u5347\u7ea7\u76ee\u6807", description={"\u5347\u7ea7 SkillAPI \u5c5e\u6027", "\u652f\u6301\u4f4d\u7f6e\u3001\u5c5e\u6027\u540d\u79f0\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 SkillAPI \u63d2\u4ef6\u652f\u6301"}, alias={"skillapi\u5c5e\u6027\u5347\u7ea7", "\u52a0\u5c5e\u6027\u70b9", "\u5347\u7ea7\u5c5e\u6027"}, params={@ParamInfo(name="position", type="Location", description="\u68c0\u67e5\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="attribute", type="String", description="\u5347\u7ea7\u7684\u5c5e\u6027\u540d\u79f0")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/skillapi/SUpAttribute;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/sucy/skill/api/event/PlayerUpAttributeEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class SUpAttribute
extends ObjectiveCountableI<PlayerUpAttributeEvent> {
    @NotNull
    public static final SUpAttribute INSTANCE = new SUpAttribute();
    @NotNull
    private static final String name = "skillapi up attribute";

    private SUpAttribute() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerUpAttributeEvent> getEvent() {
        return PlayerUpAttributeEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerUpAttributeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayerData().getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerUpAttributeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayerData().getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerUpAttributeEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAttribute();
    }

    private static final Object _init_$lambda$3(PlayerUpAttributeEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAttribute();
    }

    static {
        INSTANCE.handler(SUpAttribute::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", SUpAttribute::_init_$lambda$1);
        INSTANCE.addCondition("attribute", "String", SUpAttribute::_init_$lambda$2);
        INSTANCE.addConditionVariable("attribute", SUpAttribute::_init_$lambda$3);
    }
}

