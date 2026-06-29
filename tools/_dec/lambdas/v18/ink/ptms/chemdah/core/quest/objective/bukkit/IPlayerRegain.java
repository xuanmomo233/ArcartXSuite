/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityRegainHealthEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableF;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J \u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0002H\u0016R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerRegain;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableF;", "Lorg/bukkit/event/entity/EntityRegainHealthEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "getCount", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nIPlayerRegain.kt\nKotlin\n*S Kotlin\n*F\n+ 1 IPlayerRegain.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerRegain\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,48:1\n1747#2,3:49\n*S KotlinDebug\n*F\n+ 1 IPlayerRegain.kt\nink/ptms/chemdah/core/quest/objective/bukkit/IPlayerRegain\n*L\n35#1:49,3\n*E\n"})
public final class IPlayerRegain
extends ObjectiveCountableF<EntityRegainHealthEvent> {
    @NotNull
    public static final IPlayerRegain INSTANCE = new IPlayerRegain();
    @NotNull
    private static final String name = "health regain";
    @NotNull
    private static final Class<EntityRegainHealthEvent> event = EntityRegainHealthEvent.class;

    private IPlayerRegain() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<EntityRegainHealthEvent> getEvent() {
        return event;
    }

    @Override
    public double getCount(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull EntityRegainHealthEvent event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        return event.getAmount();
    }

    private static final Player _init_$lambda$0(EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Entity entity = it.getEntity();
        return entity instanceof Player ? (Player)entity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityRegainHealthEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityRegainHealthEvent e) {
        return data2.toConditionNumber().check(e.getAmount());
    }

    private static final Boolean _init_$lambda$4(Data data2, EntityRegainHealthEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getRegainReason().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$5(Data data2, EntityRegainHealthEvent e) {
        Boolean bl = data2.toBoolean();
        Intrinsics.checkNotNullExpressionValue((Object)e, (String)"e");
        return Intrinsics.areEqual((Object)bl, (Object)Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)e, (String)"isFastRegen", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null));
    }

    private static final Object _init_$lambda$6(EntityRegainHealthEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getAmount();
    }

    static {
        INSTANCE.handler(IPlayerRegain::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerRegain::_init_$lambda$1);
        INSTANCE.addSimpleCondition("amount", IPlayerRegain::_init_$lambda$2);
        INSTANCE.addSimpleCondition("reason", IPlayerRegain::_init_$lambda$4);
        INSTANCE.addSimpleCondition("fast", IPlayerRegain::_init_$lambda$5);
        INSTANCE.addConditionVariable("amount", IPlayerRegain::_init_$lambda$6);
    }
}

