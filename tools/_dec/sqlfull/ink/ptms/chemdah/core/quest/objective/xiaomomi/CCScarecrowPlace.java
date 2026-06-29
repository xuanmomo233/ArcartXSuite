/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.xiaomomi;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.util.CollectionKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import net.momirealms.customcrops.api.event.ScarecrowPlaceEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCScarecrowPlace;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/ScarecrowPlaceEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCScarecrowPlace
extends ObjectiveCountableI<ScarecrowPlaceEvent> {
    @NotNull
    public static final CCScarecrowPlace INSTANCE = new CCScarecrowPlace();
    @NotNull
    private static final String name = "customcrops scarecrow place";
    @NotNull
    private static final Class<ScarecrowPlaceEvent> event = ScarecrowPlaceEvent.class;

    private CCScarecrowPlace() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ScarecrowPlaceEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(ScarecrowPlaceEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, ScarecrowPlaceEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.location();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.location()");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, ScarecrowPlaceEvent e) {
        List<String> list2 = data2.asList();
        String string = e.scarecrowItemID();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.scarecrowItemID()");
        return CollectionKt.has(list2, string);
    }

    static {
        INSTANCE.handler(CCScarecrowPlace::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CCScarecrowPlace::_init_$lambda$1);
        INSTANCE.addSimpleCondition("item", CCScarecrowPlace::_init_$lambda$2);
    }
}

