/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.momirealms.customfishing.api.event.FishingResultEvent
 *  net.momirealms.customfishing.api.event.FishingResultEvent$Result
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
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
import net.momirealms.customfishing.api.event.FishingResultEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomFishing")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CFFishingSuccess;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customfishing/api/event/FishingResultEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CFFishingSuccess
extends ObjectiveCountableI<FishingResultEvent> {
    @NotNull
    public static final CFFishingSuccess INSTANCE = new CFFishingSuccess();
    @NotNull
    private static final String name = "customfishing success";
    @NotNull
    private static final Class<FishingResultEvent> event = FishingResultEvent.class;

    private CFFishingSuccess() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<FishingResultEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(FishingResultEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getResult() == FishingResultEvent.Result.SUCCESS ? it.getPlayer() : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, FishingResultEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, FishingResultEvent e) {
        return data2.toInferEntity().isEntity((Entity)e.getFishHook());
    }

    private static final Boolean _init_$lambda$3(Data data2, FishingResultEvent e) {
        List<String> list2 = data2.asList();
        String string = e.getLoot().id();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.loot.id()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$4(Data data2, FishingResultEvent e) {
        return data2.toConditionNumber().check(e.getAmount());
    }

    static {
        INSTANCE.handler(CFFishingSuccess::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CFFishingSuccess::_init_$lambda$1);
        INSTANCE.addSimpleCondition("hook", CFFishingSuccess::_init_$lambda$2);
        INSTANCE.addSimpleCondition("loot", CFFishingSuccess::_init_$lambda$3);
        INSTANCE.addSimpleCondition("amount", CFFishingSuccess::_init_$lambda$4);
    }
}

