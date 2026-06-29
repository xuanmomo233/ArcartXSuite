/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.AnimalTamer
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityTameEvent
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
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTameEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u5b9e\u4f53\u9a6f\u670d\u76ee\u6807", description={"\u73a9\u5bb6\u9a6f\u670d\u5b9e\u4f53", "\u652f\u6301\u5b9e\u4f53\u7c7b\u578b\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u9a6f\u670d\u6570\u91cf"}, alias={"\u9a6f\u670d", "\u9a6f\u5316", "\u9a6f\u517b"}, params={@ParamInfo(name="position", type="Location", description="\u88ab\u9a6f\u670d\u5b9e\u4f53\u7684\u4f4d\u7f6e"), @ParamInfo(name="entity", type="Entity", description="\u88ab\u9a6f\u670d\u7684\u5b9e\u4f53")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IEntityTame;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/entity/EntityTameEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IEntityTame
extends ObjectiveCountableI<EntityTameEvent> {
    @NotNull
    public static final IEntityTame INSTANCE = new IEntityTame();
    @NotNull
    private static final String name = "entity tame";

    private IEntityTame() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityTameEvent> getEvent() {
        return EntityTameEvent.class;
    }

    private static final Player _init_$lambda$0(EntityTameEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        AnimalTamer animalTamer = it.getOwner();
        return animalTamer instanceof Player ? (Player)animalTamer : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityTameEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityTameEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    static {
        INSTANCE.handler(IEntityTame::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IEntityTame::_init_$lambda$1);
        INSTANCE.addCondition("entity", "Entity", IEntityTame::_init_$lambda$2);
    }
}

