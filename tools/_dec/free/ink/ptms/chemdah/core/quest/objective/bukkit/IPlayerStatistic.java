/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Material
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerStatisticIncrementEvent
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
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u7edf\u8ba1\u6570\u636e\u76ee\u6807", description={"\u73a9\u5bb6\u7edf\u8ba1\u6570\u636e\u53d8\u5316\u65f6\u89e6\u53d1", "\u652f\u6301\u7edf\u8ba1\u7c7b\u578b\u3001\u5b9e\u4f53\u7c7b\u578b\u3001\u6750\u6599\u7c7b\u578b\u3001\u6570\u503c\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u589e\u91cf\u53d8\u5316"}, alias={"\u7edf\u8ba1", "\u6570\u636e\u7edf\u8ba1", "\u6210\u5c31\u7edf\u8ba1"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="statistic", type="String", description="\u7edf\u8ba1\u7c7b\u578b"), @ParamInfo(name="type:entity", type="String", description="\u5b9e\u4f53\u7c7b\u578b"), @ParamInfo(name="type:material", type="String", description="\u6750\u6599\u7c7b\u578b"), @ParamInfo(name="value", type="Number", description="\u65b0\u503c"), @ParamInfo(name="value:new", type="Number", description="\u65b0\u503c"), @ParamInfo(name="value:previous", type="Number", description="\u65e7\u503c")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerStatistic;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerStatisticIncrementEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
public final class IPlayerStatistic
extends ObjectiveCountableI<PlayerStatisticIncrementEvent> {
    @NotNull
    public static final IPlayerStatistic INSTANCE = new IPlayerStatistic();
    @NotNull
    private static final String name = "player statistic";

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
        return PlayerStatisticIncrementEvent.class;
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

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getStatistic().name();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        EntityType entityType = it.getEntityType();
        return entityType != null ? entityType.name() : null;
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Material material = it.getMaterial();
        return material != null ? material.name() : null;
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$6(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$7(PlayerProfile playerProfile2, Task task, PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPreviousValue();
    }

    private static final Object _init_$lambda$8(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$9(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewValue();
    }

    private static final Object _init_$lambda$10(PlayerStatisticIncrementEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPreviousValue();
    }

    static {
        INSTANCE.handler(IPlayerStatistic::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerStatistic::_init_$lambda$1);
        INSTANCE.addCondition("statistic", "String", IPlayerStatistic::_init_$lambda$2);
        INSTANCE.addCondition("type:entity", "String", IPlayerStatistic::_init_$lambda$3);
        INSTANCE.addCondition("type:material", "String", IPlayerStatistic::_init_$lambda$4);
        INSTANCE.addCondition("value", "Number", IPlayerStatistic::_init_$lambda$5);
        INSTANCE.addCondition("value:new", "Number", IPlayerStatistic::_init_$lambda$6);
        INSTANCE.addCondition("value:previous", "Number", IPlayerStatistic::_init_$lambda$7);
        INSTANCE.addConditionVariable("value", IPlayerStatistic::_init_$lambda$8);
        INSTANCE.addConditionVariable("value:new", IPlayerStatistic::_init_$lambda$9);
        INSTANCE.addConditionVariable("value:previous", IPlayerStatistic::_init_$lambda$10);
    }
}

