/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityPortalExitEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u4f20\u9001\u95e8\u79bb\u5f00\u76ee\u6807", description={"\u73a9\u5bb6\u79bb\u5f00\u4f20\u9001\u95e8\u65f6\u89e6\u53d1", "\u652f\u6301\u76ee\u6807\u4f4d\u7f6e\u548c\u8d77\u59cb\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u79bb\u5f00\u6b21\u6570"}, alias={"\u79bb\u5f00\u4f20\u9001\u95e8", "\u4f20\u9001\u5b8c\u6210", "\u4f20\u9001\u5230\u8fbe"}, params={@ParamInfo(name="position", type="Location", description="\u76ee\u6807\u4f4d\u7f6e\uff08\u4e0eposition:to\u76f8\u540c\uff09"), @ParamInfo(name="position:to", type="Location", description="\u76ee\u6807\u4f4d\u7f6e"), @ParamInfo(name="position:from", type="Location", description="\u8d77\u59cb\u4f4d\u7f6e")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerPortalExit;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityPortalExitEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerPortalExit
extends ObjectiveCountableI<EntityPortalExitEvent> {
    @NotNull
    public static final IPlayerPortalExit INSTANCE = new IPlayerPortalExit();
    @NotNull
    private static final String name = "portal exit";

    private IPlayerPortalExit() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityPortalExitEvent> getEvent() {
        return EntityPortalExitEvent.class;
    }

    private static final Player _init_$lambda$0(EntityPortalExitEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityPortalExitEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        if (location == null) {
            location = UnitsKt.getEMPTY_LOCATION();
        }
        return location;
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityPortalExitEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        if (location == null) {
            location = UnitsKt.getEMPTY_LOCATION();
        }
        return location;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityPortalExitEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFrom();
    }

    static {
        INSTANCE.handler(IPlayerPortalExit::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerPortalExit::_init_$lambda$1);
        INSTANCE.addCondition("position:to", "Location", IPlayerPortalExit::_init_$lambda$2);
        INSTANCE.addCondition("position:from", "Location", IPlayerPortalExit::_init_$lambda$3);
    }
}

