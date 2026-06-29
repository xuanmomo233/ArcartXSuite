/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.skillapi;

import com.sucy.skill.api.event.SkillDamageEvent;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="SkillAPI")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/skillapi/SSkillDamage;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/sucy/skill/api/event/SkillDamageEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nSSkillDamage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SSkillDamage.kt\nink/ptms/chemdah/core/quest/objective/skillapi/SSkillDamage\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,41:1\n1747#2,3:42\n*S KotlinDebug\n*F\n+ 1 SSkillDamage.kt\nink/ptms/chemdah/core/quest/objective/skillapi/SSkillDamage\n*L\n29#1:42,3\n*E\n"})
public final class SSkillDamage
extends ObjectiveCountableI<SkillDamageEvent> {
    @NotNull
    public static final SSkillDamage INSTANCE = new SSkillDamage();
    @NotNull
    private static final String name = "skillapi skill damage";
    @NotNull
    private static final Class<SkillDamageEvent> event = SkillDamageEvent.class;

    private SSkillDamage() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<SkillDamageEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(SkillDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        LivingEntity livingEntity = it.getDamager();
        return livingEntity instanceof Player ? (Player)livingEntity : null;
    }

    private static final Boolean _init_$lambda$1(Data data2, SkillDamageEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getDamager().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.damager.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, SkillDamageEvent e) {
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
                    if (!StringsKt.equals((String)it, (String)e.getSkill().getName(), (boolean)true)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final Boolean _init_$lambda$4(Data data2, SkillDamageEvent e) {
        return data2.toInferEntity().isEntity((Entity)e.getTarget());
    }

    private static final Boolean _init_$lambda$5(Data data2, SkillDamageEvent it) {
        return data2.toConditionNumber().check(it.getDamage());
    }

    private static final Object _init_$lambda$6(SkillDamageEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getDamage();
    }

    static {
        INSTANCE.handler(SSkillDamage::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", SSkillDamage::_init_$lambda$1);
        INSTANCE.addSimpleCondition("skill", SSkillDamage::_init_$lambda$3);
        INSTANCE.addSimpleCondition("target", SSkillDamage::_init_$lambda$4);
        INSTANCE.addSimpleCondition("damage", SSkillDamage::_init_$lambda$5);
        INSTANCE.addConditionVariable("damage", SSkillDamage::_init_$lambda$6);
    }
}

