/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerStatistic;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerStatisticIncrementEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIPlayerStatistic.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IPlayerStatistic.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerStatistic\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,61:1\n1747#2,3:62\n1747#2,3:65\n1747#2,3:68\n*S KotlinDebug\n*F\n+ 1 IPlayerStatistic.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerStatistic\n*L\n30#1:62,3\n33#1:65,3\n36#1:68,3\n*E\n"})
public final class IPlayerStatistic
extends ObjectiveCountableI<PlayerStatisticIncrementEvent> {
    @NotNull
    public static final IPlayerStatistic INSTANCE = new IPlayerStatistic();
    @NotNull
    private static final String name = "player statistic";
    @NotNull
    private static final Class<PlayerStatisticIncrementEvent> event = PlayerStatisticIncrementEvent.class;

    private IPlayerStatistic() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerStatisticIncrementEvent> getEvent() {
        return event;
    }

    @Override
    public int getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull PlayerStatisticIncrementEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getNewValue() - event.getPreviousValue();
    }

    private static final Player _init_$lambda$0(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerStatisticIncrementEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerStatisticIncrementEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    if (!StringsKt.equals((String)it, (String)e.getStatistic().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$5(Data data2, PlayerStatisticIncrementEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    EntityType entityType = e.getEntityType();
                    if (!StringsKt.equals((String)it, (String)(entityType != null ? entityType.name() : null), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$7(Data data2, PlayerStatisticIncrementEvent e) {
        boolean bl;
        block3: {
            Iterable $this$any$iv = data2.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String it = (String)element$iv;
                    boolean bl2 = false;
                    Material material = e.getMaterial();
                    if (!StringsKt.equals((String)it, (String)(material != null ? material.name() : null), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$8(Data data2, PlayerStatisticIncrementEvent e) {
        return data2.toConditionNumber().check(e.getNewValue());
    }

    private static final Boolean _init_$lambda$9(Data data2, PlayerStatisticIncrementEvent e) {
        return data2.toConditionNumber().check(e.getNewValue());
    }

    private static final Boolean _init_$lambda$10(Data data2, PlayerStatisticIncrementEvent e) {
        return data2.toConditionNumber().check(e.getPreviousValue());
    }

    private static final Object _init_$lambda$11(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$12(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$13(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPreviousValue();
    }

    static {
        INSTANCE.handler(IPlayerStatistic::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerStatistic::_init_$lambda$1);
        INSTANCE.addSimpleCondition("statistic", IPlayerStatistic::_init_$lambda$3);
        INSTANCE.addSimpleCondition("type:entity", IPlayerStatistic::_init_$lambda$5);
        INSTANCE.addSimpleCondition("type:material", IPlayerStatistic::_init_$lambda$7);
        INSTANCE.addSimpleCondition("value", IPlayerStatistic::_init_$lambda$8);
        INSTANCE.addSimpleCondition("value:new", IPlayerStatistic::_init_$lambda$9);
        INSTANCE.addSimpleCondition("value:previous", IPlayerStatistic::_init_$lambda$10);
        INSTANCE.addConditionVariable("value", IPlayerStatistic::_init_$lambda$11);
        INSTANCE.addConditionVariable("value:new", IPlayerStatistic::_init_$lambda$12);
        INSTANCE.addConditionVariable("value:previous", IPlayerStatistic::_init_$lambda$13);
    }
}

