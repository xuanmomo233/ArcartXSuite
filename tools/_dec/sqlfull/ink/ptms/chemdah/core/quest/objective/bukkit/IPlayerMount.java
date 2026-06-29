/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.entity.EntityMountEvent;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerMount;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/spigotmc/event/entity/EntityMountEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerMount
extends ObjectiveCountableI<EntityMountEvent> {
    @NotNull
    public static final IPlayerMount INSTANCE = new IPlayerMount();
    @NotNull
    private static final String name = "entity mount";
    @NotNull
    private static final Class<EntityMountEvent> event = EntityMountEvent.class;

    private IPlayerMount() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityMountEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(EntityMountEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityMountEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getMount().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.mount.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityMountEvent e) {
        return data2.toInferEntity().isEntity(e.getMount());
    }

    static {
        INSTANCE.handler(IPlayerMount::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerMount::_init_$lambda$1);
        INSTANCE.addSimpleCondition("entity", IPlayerMount::_init_$lambda$2);
    }
}

