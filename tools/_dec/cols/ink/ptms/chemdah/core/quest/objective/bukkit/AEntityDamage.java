/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestDataOperator;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

@Abstract
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000<\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004J%\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00028\u0000H\u0016\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J(\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u000b\u001a\u00020\u0013H\u0016\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "E", "Lorg/bukkit/event/entity/EntityDamageEvent;", "Link/ptms/chemdah/core/quest/objective/Objective;", "()V", "getDamage", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Lorg/bukkit/event/entity/EntityDamageEvent;)D", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "onContinue", "", "quest", "Link/ptms/chemdah/core/quest/Quest;", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAEntityDamage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AEntityDamage.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,75:1\n11#2:76\n11#2:77\n11#2:81\n11#2:82\n1747#3,3:78\n*S KotlinDebug\n*F\n+ 1 AEntityDamage.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage\n*L\n62#1:76\n67#1:77\n48#1:81\n51#1:82\n39#1:78,3\n*E\n"})
public abstract class AEntityDamage<E extends EntityDamageEvent>
extends Objective<E> {
    public AEntityDamage() {
        this.addSimpleCondition("position", AEntityDamage::_init_$lambda$0);
        this.addSimpleCondition("victim", AEntityDamage::_init_$lambda$1);
        this.addSimpleCondition("damage", AEntityDamage::_init_$lambda$2);
        this.addSimpleCondition("damage:final", AEntityDamage::_init_$lambda$3);
        this.addSimpleCondition("cause", AEntityDamage::_init_$lambda$5);
        this.addConditionVariable("damage", AEntityDamage::_init_$lambda$6);
        this.addConditionVariable("damage:final", AEntityDamage::_init_$lambda$7);
        this.addGoal("damage", AEntityDamage::_init_$lambda$8);
        this.addGoalVariable("damage", AEntityDamage::_init_$lambda$9);
    }

    @Override
    public void onContinue(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull Object event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        QuestDataOperator.add$default(profile.dataOperator(task), "damage", this.getDamage(profile, task, (EntityDamageEvent)event), 0.0, 4, null);
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Progress progress;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Data $this$cdouble$iv = task.getGoal().get("damage", 1);
        boolean $i$f$getCdouble = false;
        double target = Coerce.format((double)Coerce.toDouble((Object)$this$cdouble$iv));
        if (this.hasCompletedSignature(profile, task)) {
            progress = Progress.Companion.toProgress(target, target, 1.0);
        } else {
            Data $this$cdouble$iv2 = profile.dataOperator(task).get("damage", 0);
            boolean $i$f$getCdouble2 = false;
            double damage = Coerce.toDouble((Object)$this$cdouble$iv2);
            progress = Progress.Companion.toProgress(Coerce.format((double)damage), target, Coerce.format((double)(damage / target)));
        }
        return progress;
    }

    public double getDamage(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull E event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter(event, (String)"event");
        return event.getFinalDamage();
    }

    private static final Boolean _init_$lambda$0(Data data2, EntityDamageEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getEntity().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.entity.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$1(Data data2, EntityDamageEvent e) {
        return data2.toInferEntity().isEntity(e.getEntity());
    }

    private static final Boolean _init_$lambda$2(Data data2, EntityDamageEvent e) {
        return data2.toConditionNumber().check(e.getDamage());
    }

    private static final Boolean _init_$lambda$3(Data data2, EntityDamageEvent e) {
        return data2.toConditionNumber().check(e.getFinalDamage());
    }

    private static final Boolean _init_$lambda$5(Data data2, EntityDamageEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getCause().name(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Object _init_$lambda$6(EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$7(EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFinalDamage();
    }

    private static final Boolean _init_$lambda$8(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cdouble$iv = task.getGoal().get("damage", 1);
        boolean $i$f$getCdouble = false;
        return profile.dataOperator(task).more("damage", Coerce.toDouble((Object)$this$cdouble$iv));
    }

    private static final Object _init_$lambda$9(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cdouble$iv = profile.dataOperator(task).get("damage", 0);
        boolean $i$f$getCdouble = false;
        return Coerce.toDouble((Object)$this$cdouble$iv);
    }
}

