/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.mythicmobs;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.um.event.MobDeathEvent;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="MythicMobs")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/mythicmobs/MMobDeath;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/chemdah/um/event/MobDeathEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nMMobDeath.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MMobDeath.kt\nink/ptms/chemdah/core/quest/objective/mythicmobs/MMobDeath\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,35:1\n1747#2,3:36\n*S KotlinDebug\n*F\n+ 1 MMobDeath.kt\nink/ptms/chemdah/core/quest/objective/mythicmobs/MMobDeath\n*L\n22#1:36,3\n*E\n"})
public final class MMobDeath
extends ObjectiveCountableI<MobDeathEvent> {
    @NotNull
    public static final MMobDeath INSTANCE = new MMobDeath();
    @NotNull
    private static final String name = "mythicmobs kill";
    @NotNull
    private static final Class<MobDeathEvent> event = MobDeathEvent.class;

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
        return event;
    }

    private static final Player _init_$lambda$0(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        LivingEntity livingEntity = it.getKiller();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, MobDeathEvent e) {
        InferArea inferArea = data2.toPosition();
        LivingEntity livingEntity = e.getKiller();
        Intrinsics.checkNotNull((Object)livingEntity);
        Location location = livingEntity.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.killer!!.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, MobDeathEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getMob().getId(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$4(Data data2, MobDeathEvent e) {
        return data2.toConditionNumber().check(e.getMob().getLevel());
    }

    private static final Object _init_$lambda$5(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getId();
    }

    private static final Object _init_$lambda$6(MobDeathEvent it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getMob().getLevel();
    }

    static {
        INSTANCE.handler(MMobDeath::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", MMobDeath::_init_$lambda$1);
        INSTANCE.addSimpleCondition("name", MMobDeath::_init_$lambda$3);
        INSTANCE.addSimpleCondition("level", MMobDeath::_init_$lambda$4);
        INSTANCE.addConditionVariable("name", MMobDeath::_init_$lambda$5);
        INSTANCE.addConditionVariable("level", MMobDeath::_init_$lambda$6);
    }
}

