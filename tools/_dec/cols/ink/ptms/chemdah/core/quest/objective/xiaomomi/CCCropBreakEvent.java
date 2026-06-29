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
import net.momirealms.customcrops.api.event.CropBreakEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="CustomCrops")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/xiaomomi/CCCropBreakEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lnet/momirealms/customcrops/api/event/CropBreakEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CCCropBreakEvent
extends ObjectiveCountableI<CropBreakEvent> {
    @NotNull
    public static final CCCropBreakEvent INSTANCE = new CCCropBreakEvent();
    @NotNull
    private static final String name = "customcrops break use";
    @NotNull
    private static final Class<CropBreakEvent> event = CropBreakEvent.class;

    private CCCropBreakEvent() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<CropBreakEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(CropBreakEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.entityBreaker();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, CropBreakEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.location();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.location()");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, CropBreakEvent e) {
        List<String> list2 = data2.asList();
        String string = e.cropConfig().id();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.cropConfig().id()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$3(Data data2, CropBreakEvent e) {
        List<String> list2 = data2.asList();
        String string = e.cropConfig().seed();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.cropConfig().seed()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$4(Data data2, CropBreakEvent e) {
        List<String> list2 = data2.asList();
        String string = e.blockState().asString();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.blockState().asString()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$5(Data data2, CropBreakEvent e) {
        List<String> list2 = data2.asList();
        String string = e.blockState().type().type().toString();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.blockState().type().type().toString()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$6(Data data2, CropBreakEvent e) {
        List<String> list2 = data2.asList();
        String string = e.cropStageItemID();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.cropStageItemID()");
        return CollectionKt.has(list2, string);
    }

    private static final Boolean _init_$lambda$7(Data data2, CropBreakEvent e) {
        return CollectionKt.has(data2.asList(), e.reason().name());
    }

    static {
        INSTANCE.handler(CCCropBreakEvent::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", CCCropBreakEvent::_init_$lambda$1);
        INSTANCE.addSimpleCondition("crop", CCCropBreakEvent::_init_$lambda$2);
        INSTANCE.addSimpleCondition("crop:seed", CCCropBreakEvent::_init_$lambda$3);
        INSTANCE.addSimpleCondition("block", CCCropBreakEvent::_init_$lambda$4);
        INSTANCE.addSimpleCondition("block:type", CCCropBreakEvent::_init_$lambda$5);
        INSTANCE.addSimpleCondition("stage", CCCropBreakEvent::_init_$lambda$6);
        INSTANCE.addSimpleCondition("reason", CCCropBreakEvent::_init_$lambda$7);
    }
}

