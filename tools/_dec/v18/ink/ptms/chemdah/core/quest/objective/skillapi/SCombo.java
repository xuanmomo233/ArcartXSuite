/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.api.event.PlayerComboFinishEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.skillapi;

import com.sucy.skill.api.event.PlayerComboFinishEvent;
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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="SkillAPI")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/skillapi/SCombo;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lcom/sucy/skill/api/event/PlayerComboFinishEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nSCombo.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SCombo.kt\nink/ptms/chemdah/core/quest/objective/skillapi/SCombo\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,39:1\n1747#2,3:40\n*S KotlinDebug\n*F\n+ 1 SCombo.kt\nink/ptms/chemdah/core/quest/objective/skillapi/SCombo\n*L\n29#1:40,3\n*E\n"})
public final class SCombo
extends ObjectiveCountableI<PlayerComboFinishEvent> {
    @NotNull
    public static final SCombo INSTANCE = new SCombo();
    @NotNull
    private static final String name = "skillapi combo";
    @NotNull
    private static final Class<PlayerComboFinishEvent> event = PlayerComboFinishEvent.class;

    private SCombo() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerComboFinishEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerComboFinishEvent it) {
        InferArea inferArea = data2.toPosition();
        Location location = it.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"it.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerComboFinishEvent e) {
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

    private static final Boolean _init_$lambda$4(Data data2, PlayerComboFinishEvent it) {
        return data2.toConditionNumber().check(it.getCombo());
    }

    private static final Object _init_$lambda$5(PlayerComboFinishEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getCombo();
    }

    static {
        INSTANCE.handler(SCombo::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", SCombo::_init_$lambda$1);
        INSTANCE.addSimpleCondition("skill", SCombo::_init_$lambda$3);
        INSTANCE.addSimpleCondition("combo", SCombo::_init_$lambda$4);
        INSTANCE.addConditionVariable("combo", SCombo::_init_$lambda$5);
    }
}

