/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.mythicmobs;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.um.event.MobDeathEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="MythicMobs")
@MetaInfo(source="MythicMobs", name="MythicMobs \u602a\u7269\u51fb\u6740\u76ee\u6807", description={"\u51fb\u6740 MythicMobs \u602a\u7269", "\u652f\u6301\u602a\u7269\u540d\u79f0\u3001\u7b49\u7ea7\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 MythicMobs \u63d2\u4ef6\u652f\u6301"}, alias={"mm\u51fb\u6740", "\u51fb\u6740mm", "\u795e\u8bdd\u602a\u7269"}, params={@ParamInfo(name="position", type="string", description="\u602a\u7269\u6b7b\u4ea1\u4f4d\u7f6e"), @ParamInfo(name="name", type="string", description="\u602a\u7269ID\u6216\u540d\u79f0"), @ParamInfo(name="level", type="number", description="\u602a\u7269\u7b49\u7ea7")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/mythicmobs/MMobDeath;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/chemdah/um/event/MobDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class MMobDeath
extends ObjectiveCountableI<MobDeathEvent> {
    @NotNull
    public static final MMobDeath INSTANCE = new MMobDeath();
    @NotNull
    private static final String name = "mythicmobs kill";

    private MMobDeath() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<MobDeathEvent> getEvent() {
        return MobDeathEvent.class;
    }

    private static final Player _init_$lambda$0(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        LivingEntity livingEntity = it.getKiller();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        LivingEntity livingEntity = it.getKiller();
        Intrinsics.checkNotNull((Object)livingEntity);
        return livingEntity.getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getId();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getLevel();
    }

    private static final Object _init_$lambda$4(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getId();
    }

    private static final Object _init_$lambda$5(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getLevel();
    }

    static {
        INSTANCE.handler(MMobDeath::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", MMobDeath::_init_$lambda$1);
        INSTANCE.addCondition("name", "String", MMobDeath::_init_$lambda$2);
        INSTANCE.addCondition("level", "Number", MMobDeath::_init_$lambda$3);
        INSTANCE.addConditionVariable("name", MMobDeath::_init_$lambda$4);
        INSTANCE.addConditionVariable("level", MMobDeath::_init_$lambda$5);
    }
}

