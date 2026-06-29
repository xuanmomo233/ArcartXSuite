/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestDataOperator;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Abstract;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

@Abstract
@MetaInfo(name="\u5b9e\u4f53\u53d7\u4f24\u76ee\u6807", description={"\u5b9e\u4f53\u53d7\u5230\u4f24\u5bb3\u65f6\u89e6\u53d1", "\u652f\u6301\u53d7\u5bb3\u8005\u3001\u4f24\u5bb3\u503c\u3001\u4f24\u5bb3\u7c7b\u578b\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u4f24\u5bb3\u6b21\u6570\u6216\u7d2f\u8ba1\u4f24\u5bb3\u503c"}, alias={"\u53d7\u4f24", "\u4f24\u5bb3", "\u9020\u6210\u4f24\u5bb3"}, params={@ParamInfo(name="position", type="Location", description="\u53d7\u4f24\u5b9e\u4f53\u7684\u4f4d\u7f6e"), @ParamInfo(name="victim", type="Entity", description="\u53d7\u5bb3\u8005\u5b9e\u4f53"), @ParamInfo(name="damage", type="Number", description="\u4f24\u5bb3\u503c"), @ParamInfo(name="damage:final", type="Number", description="\u6700\u7ec8\u4f24\u5bb3\u503c"), @ParamInfo(name="cause", type="String", description="\u4f24\u5bb3\u7c7b\u578b\uff08ENTITY_ATTACK/FALL/FIRE/DROWNING\u7b49\uff09")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000<\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\b'\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004J%\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00028\u0000H\u0016\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J(\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u000b\u001a\u00020\u0013H\u0016\u00a8\u0006\u0014"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage;", "E", "Lorg/bukkit/event/entity/EntityDamageEvent;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "getDamage", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Lorg/bukkit/event/entity/EntityDamageEvent;)D", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "onContinue", "", "quest", "Link/ptms/chemdah/core/quest/Quest;", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAEntityDamage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AEntityDamage.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,97:1\n12#2:98\n12#2:99\n12#2:100\n12#2:101\n*S KotlinDebug\n*F\n+ 1 AEntityDamage.kt\nink/ptms/chemdah/core/quest/objective/bukkit/AEntityDamage\n*L\n81#1:98\n89#1:99\n66#1:100\n69#1:101\n*E\n"})
public abstract class AEntityDamage<E extends EntityDamageEvent>
extends ObjectiveCountableI<E> {
    public AEntityDamage() {
        this.addCondition("position", "Location", AEntityDamage::_init_$lambda$0);
        this.addCondition("victim", "Entity", AEntityDamage::_init_$lambda$1);
        this.addCondition("damage", "Number", AEntityDamage::_init_$lambda$2);
        this.addCondition("damage:final", "Number", AEntityDamage::_init_$lambda$3);
        this.addCondition("cause", "String", AEntityDamage::_init_$lambda$4);
        this.addConditionVariable("damage", AEntityDamage::_init_$lambda$5);
        this.addConditionVariable("damage:final", AEntityDamage::_init_$lambda$6);
        this.addGoal("damage", "Number", AEntityDamage::_init_$lambda$7);
        this.addGoalVariable("damage", AEntityDamage::_init_$lambda$8);
    }

    @Override
    public void onContinue(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull Object event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        super.onContinue(profile, task, quest2, event);
        QuestDataOperator.add$default(profile.dataOperator(task), "damage", this.getDamage(profile, task, (EntityDamageEvent)event), 0.0, 4, null);
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Progress progress;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Data $this$cdouble$iv = task.getGoal().get("damage", 0);
        boolean $i$f$getCdouble = false;
        double target = Coerce.format((double)Coerce.toDouble((Object)$this$cdouble$iv));
        if (target == 0.0) {
            return super.getProgress(profile, task);
        }
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

    private static final Object _init_$lambda$0(PlayerProfile playerProfile2, Task task, EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity().getLocation();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getEntity();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFinalDamage();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCause().name();
    }

    private static final Object _init_$lambda$5(EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    private static final Object _init_$lambda$6(EntityDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFinalDamage();
    }

    private static final Boolean _init_$lambda$7(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cdouble$iv = task.getGoal().get("damage", 1);
        boolean $i$f$getCdouble = false;
        return profile.dataOperator(task).more("damage", Coerce.toDouble((Object)$this$cdouble$iv));
    }

    private static final Object _init_$lambda$8(PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        Data $this$cdouble$iv = profile.dataOperator(task).get("damage", 0);
        boolean $i$f$getCdouble = false;
        return Coerce.toDouble((Object)$this$cdouble$iv);
    }
}

