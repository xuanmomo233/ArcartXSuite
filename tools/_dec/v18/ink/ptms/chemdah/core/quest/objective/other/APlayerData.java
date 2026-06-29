/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.ConditionNumber;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.Collection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0005\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0016J \u0010\u000f\u001a\u00020\u00102\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\u0010H&R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0007R\u0014\u0010\b\u001a\u00020\u0006X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2={"Link/ptms/chemdah/core/quest/objective/other/APlayerData;", "E", "", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "()V", "isListener", "", "()Z", "isTickable", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "getValue", "", "key", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nAPlayerData.kt\nKotlin\n*S Kotlin\n*F\n+ 1 APlayerData.kt\nink/ptms/chemdah/core/quest/objective/other/APlayerData\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,50:1\n11#2:51\n1747#3,3:52\n*S KotlinDebug\n*F\n+ 1 APlayerData.kt\nink/ptms/chemdah/core/quest/objective/other/APlayerData\n*L\n42#1:51\n32#1:52,3\n*E\n"})
public abstract class APlayerData<E>
extends ObjectiveCountableI<E> {
    private final boolean isListener;
    private final boolean isTickable;

    public APlayerData() {
        this.isTickable = true;
        this.addFullCondition("position", APlayerData::_init_$lambda$0);
        this.addGoal("kv", (arg_0, arg_1) -> APlayerData._init_$lambda$2(this, arg_0, arg_1));
    }

    @Override
    public boolean isListener() {
        return this.isListener;
    }

    @Override
    public boolean isTickable() {
        return this.isTickable;
    }

    @Override
    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Progress progress;
        double target;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Object object = task.getGoal().get("value");
        double d = object != null && (object = ((Data)object).toConditionNumber()) != null && (object = ((ConditionNumber)object).getMatcher()) != null ? ((ConditionNumber.Matcher)object).getNum() : (target = 0.0);
        if (this.hasCompletedSignature(profile, task)) {
            progress = Progress.Companion.toProgress(target, target, 1.0);
        } else {
            String $this$cdouble$iv = this.getValue(profile, task, String.valueOf(task.getGoal().get("key")));
            boolean $i$f$getCdouble = false;
            progress = Progress.Companion.toProgress$default(Progress.Companion, Coerce.toDouble((Object)$this$cdouble$iv), target, 0.0, 2, null);
        }
        return progress;
    }

    @NotNull
    public abstract String getValue(@NotNull PlayerProfile var1, @NotNull Task var2, @NotNull String var3);

    private static final Boolean _init_$lambda$0(PlayerProfile profile, Task task, Object object) {
        Data data2 = task.getCondition().get("position");
        Intrinsics.checkNotNull((Object)data2);
        InferArea inferArea = data2.toPosition();
        Location location = profile.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"profile.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(APlayerData this$0, PlayerProfile profile, Task task) {
        Boolean bl;
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        Data data2 = task.getGoal().get("value");
        if (data2 == null) {
            return false;
        }
        Data checkValue = data2;
        Intrinsics.checkNotNullExpressionValue((Object)profile, (String)"profile");
        Intrinsics.checkNotNullExpressionValue((Object)task, (String)"task");
        String data3 = this$0.getValue(profile, task, String.valueOf(task.getGoal().get("key")));
        if (Intrinsics.areEqual((Object)data3, (Object)"null")) {
            return false;
        }
        if (StringsKt.toDoubleOrNull((String)data3) != null) {
            bl = checkValue.toConditionNumber().check(Double.parseDouble(data3));
        } else {
            boolean bl2;
            block7: {
                Iterable $this$any$iv = checkValue.asList();
                boolean $i$f$any = false;
                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                    bl2 = false;
                } else {
                    for (Object element$iv : $this$any$iv) {
                        String it = (String)element$iv;
                        boolean bl3 = false;
                        if (!Intrinsics.areEqual((Object)it, (Object)data3)) continue;
                        bl2 = true;
                        break block7;
                    }
                    bl2 = false;
                }
            }
            bl = bl2;
        }
        return bl;
    }
}

