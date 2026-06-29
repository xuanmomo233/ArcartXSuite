/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.ExperienceOrb
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u62fe\u53d6\u7ecf\u9a8c\u7403\u76ee\u6807", description={"\u73a9\u5bb6\u62fe\u53d6\u7ecf\u9a8c\u7403\u65f6\u89e6\u53d1", "\u652f\u6301\u7ecf\u9a8c\u503c\u3001\u6765\u6e90\u539f\u56e0\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7d2f\u8ba1\u62fe\u53d6\u7ecf\u9a8c\u503c"}, alias={"\u62fe\u53d6\u7ecf\u9a8c", "\u6361\u7ecf\u9a8c", "\u6536\u96c6\u7ecf\u9a8c"}, params={@ParamInfo(name="position", type="location", description="\u62fe\u53d6\u7ecf\u9a8c\u7403\u7684\u4f4d\u7f6e"), @ParamInfo(name="reason", type="string", description="\u7ecf\u9a8c\u7403\u7684\u751f\u6210\u539f\u56e0"), @ParamInfo(name="exp", type="number", description="\u7ecf\u9a8c\u7403\u7684\u7ecf\u9a8c\u503c"), @ParamInfo(name="orb", type="entity", description="\u7ecf\u9a8c\u7403\u5b9e\u4f53")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemPickExp;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerPickupExperienceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IItemPickExp
extends ObjectiveCountableI<PlayerPickupExperienceEvent> {
    @NotNull
    public static final IItemPickExp INSTANCE = new IItemPickExp();
    @NotNull
    private static final String name = "pickup exp";

    private IItemPickExp() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerPickupExperienceEvent> getEvent() {
        return PlayerPickupExperienceEvent.class;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull PlayerPickupExperienceEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getExperienceOrb().getExperience();
    }

    private static final Player _init_$lambda$0(PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        ExperienceOrb experienceOrb = it.getExperienceOrb();
        Intrinsics.checkNotNullExpressionValue((Object)experienceOrb, (String)"it.experienceOrb");
        return String.valueOf(Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)experienceOrb, (String)"getSpawnReason", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null));
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperienceOrb().getExperience();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperienceOrb();
    }

    private static final Object _init_$lambda$5(PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperienceOrb().getExperience();
    }

    static {
        INSTANCE.handler(IItemPickExp::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IItemPickExp::_init_$lambda$1);
        INSTANCE.addCondition("reason", "String", IItemPickExp::_init_$lambda$2);
        INSTANCE.addCondition("exp", "Number", IItemPickExp::_init_$lambda$3);
        INSTANCE.addCondition("orb", "Entity", IItemPickExp::_init_$lambda$4);
        INSTANCE.addConditionVariable("exp", IItemPickExp::_init_$lambda$5);
    }
}

