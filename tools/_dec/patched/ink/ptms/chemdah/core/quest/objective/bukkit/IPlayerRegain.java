/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityRegainHealthEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableF;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u751f\u547d\u503c\u6062\u590d\u76ee\u6807", description={"\u73a9\u5bb6\u751f\u547d\u503c\u6062\u590d\u65f6\u89e6\u53d1", "\u652f\u6301\u6062\u590d\u91cf\u3001\u6062\u590d\u539f\u56e0\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7d2f\u8ba1\u6062\u590d\u91cf"}, alias={"\u751f\u547d\u6062\u590d", "\u56de\u8840", "\u6cbb\u7597"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="amount", type="Number", description="\u6062\u590d\u91cf"), @ParamInfo(name="reason", type="String", description="\u6062\u590d\u539f\u56e0"), @ParamInfo(name="fast", type="Boolean", description="\u662f\u5426\u4e3a\u5feb\u901f\u6062\u590d")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerRegain;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableF;", "Lorg/bukkit/event/entity/EntityRegainHealthEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerRegain
extends ObjectiveCountableF<EntityRegainHealthEvent> {
    @NotNull
    public static final IPlayerRegain INSTANCE = new IPlayerRegain();
    @NotNull
    private static final String name = "health regain";

    private IPlayerRegain() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityRegainHealthEvent> getEvent() {
        return EntityRegainHealthEvent.class;
    }

    @Override
    public double getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull EntityRegainHealthEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getAmount();
    }

    private static final Player _init_$lambda$0(EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAmount();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getRegainReason().name();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)it, (String)"isFastRegen", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
        if (object == null) {
            object = false;
        }
        return object;
    }

    private static final Object _init_$lambda$5(EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAmount();
    }

    static {
        INSTANCE.handler(IPlayerRegain::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerRegain::_init_$lambda$1);
        INSTANCE.addCondition("amount", "Number", IPlayerRegain::_init_$lambda$2);
        INSTANCE.addCondition("reason", "String", IPlayerRegain::_init_$lambda$3);
        INSTANCE.addCondition("fast", "Boolean", IPlayerRegain::_init_$lambda$4);
        INSTANCE.addConditionVariable("amount", IPlayerRegain::_init_$lambda$5);
    }
}

