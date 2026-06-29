/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.ExperienceOrb
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.util.CollectionKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IItemPickExp;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/destroystokyo/paper/event/player/PlayerPickupExperienceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IItemPickExp
extends ObjectiveCountableI<PlayerPickupExperienceEvent> {
    @NotNull
    public static final IItemPickExp INSTANCE = new IItemPickExp();
    @NotNull
    private static final String name = "pickup exp";
    @NotNull
    private static final Class<PlayerPickupExperienceEvent> event = PlayerPickupExperienceEvent.class;

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
        return event;
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

    private static final Boolean _init_$lambda$1(Data data2, PlayerPickupExperienceEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerPickupExperienceEvent e) {
        List<String> list2 = data2.asList();
        ExperienceOrb experienceOrb = e.getExperienceOrb();
        Intrinsics.checkNotNullExpressionValue((Object)experienceOrb, (String)"e.experienceOrb");
        return CollectionKt.has(list2, String.valueOf(Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)experienceOrb, (String)"getSpawnReason", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null)));
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerPickupExperienceEvent e) {
        return data2.toConditionNumber().check(e.getExperienceOrb().getExperience());
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerPickupExperienceEvent e) {
        return data2.toInferEntity().isEntity((Entity)e.getExperienceOrb());
    }

    private static final Object _init_$lambda$5(PlayerPickupExperienceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getExperienceOrb().getExperience();
    }

    static {
        INSTANCE.handler(IItemPickExp::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IItemPickExp::_init_$lambda$1);
        INSTANCE.addSimpleCondition("reason", IItemPickExp::_init_$lambda$2);
        INSTANCE.addSimpleCondition("exp", IItemPickExp::_init_$lambda$3);
        INSTANCE.addSimpleCondition("orb", IItemPickExp::_init_$lambda$4);
        INSTANCE.addConditionVariable("exp", IItemPickExp::_init_$lambda$5);
    }
}

