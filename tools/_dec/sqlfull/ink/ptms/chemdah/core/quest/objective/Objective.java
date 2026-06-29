/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.addon.AddonRestart;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.util.Couple;
import ink.ptms.chemdah.util.Function2;
import ink.ptms.chemdah.util.Function3;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.Unit;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0080\u0001\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u000b\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0002B\u0005\u00a2\u0006\u0002\u0010\u0003J\"\u00102\u001a\u0002032\u0006\u0010\"\u001a\u00020\u00062\u0012\u00104\u001a\u000e\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00020\u0007J.\u00105\u001a\u0002032\u0006\u0010\"\u001a\u00020\u00062\u001e\u00104\u001a\u001a\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00100\rJ(\u00106\u001a\u0002032\u0006\u0010\"\u001a\u00020\u00062\u0018\u00104\u001a\u0014\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00100\u0017J(\u00107\u001a\u0002032\u0006\u0010\"\u001a\u00020\u00062\u0018\u00104\u001a\u0014\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00020\u0017J(\u00108\u001a\u0002032\u0006\u0010\"\u001a\u00020\u00062\u0018\u00104\u001a\u0014\u0012\u0004\u0012\u000209\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00100\u0017J&\u0010:\u001a\b\u0012\u0004\u0012\u00020\u00100;2\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010>\u001a\u00020?H\u0016J3\u0010@\u001a\b\u0012\u0004\u0012\u00020\u00100;2\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010>\u001a\u00020?2\u0006\u0010\u0012\u001a\u00028\u0000H\u0016\u00a2\u0006\u0002\u0010AJ&\u0010B\u001a\b\u0012\u0004\u0012\u00020\u00100;2\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010>\u001a\u00020?2\u0006\u0010=\u001a\u00020\u000fH\u0016J\u0018\u0010C\u001a\u00020D2\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000fH\u0016J\u001c\u0010E\u001a\u0002032\u0014\u0010F\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010&0\u0007J\u0018\u0010G\u001a\u00020\u00102\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000fH\u0016J\u0018\u0010H\u001a\u00020\u00102\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000fH\u0016J \u0010I\u001a\u0002032\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010>\u001a\u00020?H\u0016J(\u0010J\u001a\u0002032\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010>\u001a\u00020?2\u0006\u0010\u0012\u001a\u00020\u0002H\u0016J \u0010K\u001a\u0002032\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010>\u001a\u00020?H\u0016J \u0010L\u001a\u0002032\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010M\u001a\u00020\u0010H\u0016J \u0010N\u001a\u0002032\u0006\u0010<\u001a\u00020\u000e2\u0006\u0010=\u001a\u00020\u000f2\u0006\u0010M\u001a\u00020\u0010H\u0016R`\u0010\u0004\u001aN\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00028\u0000\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u00070\u0005j&\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00028\u0000\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u0007`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR`\u0010\f\u001aN\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00100\r0\u0005j&\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00100\r`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000bR\u0018\u0010\u0012\u001a\b\u0012\u0004\u0012\u00028\u00000\u0013X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0014\u0010\u0015Rl\u0010\u0016\u001aZ\u0012\u0004\u0012\u00020\u0006\u0012\"\u0012 \u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u00170\u0005j,\u0012\u0004\u0012\u00020\u0006\u0012\"\u0012 \u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u0017`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u000bRT\u0010\u0019\u001aB\u0012\u0004\u0012\u00020\u0006\u0012\u0016\u0012\u0014\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00100\u00170\u0005j \u0012\u0004\u0012\u00020\u0006\u0012\u0016\u0012\u0014\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00100\u0017`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u000bR\u0014\u0010\u001b\u001a\u00020\u0010X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001dR\u001c\u0010\u001e\u001a\u00020\u00108\u0016X\u0097D\u00a2\u0006\u000e\n\u0000\u0012\u0004\b\u001f\u0010\u0003\u001a\u0004\b\u001e\u0010\u001dR\u0014\u0010 \u001a\u00020\u0010X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u001dR\u0014\u0010!\u001a\u00020\u0010X\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001dR\u0012\u0010\"\u001a\u00020\u0006X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b#\u0010$R:\u0010'\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010&0\u00072\u0014\u0010%\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010&0\u0007@BX\u0080\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010)R\u0014\u0010*\u001a\u00020+X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010-R\u001a\u0010.\u001a\u00020\u0010X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b/\u0010\u001d\"\u0004\b0\u00101\u00a8\u0006O"}, d2={"Link/ptms/chemdah/core/quest/objective/Objective;", "E", "", "()V", "conditionVars", "Ljava/util/HashMap;", "", "Ljava/util/function/Function;", "Link/ptms/chemdah/util/Couple;", "Lkotlin1822/collections/HashMap;", "getConditionVars$Chemdah", "()Ljava/util/HashMap;", "conditions", "Link/ptms/chemdah/util/Function3;", "Link/ptms/chemdah/core/PlayerProfile;", "Link/ptms/chemdah/core/quest/Task;", "", "getConditions$Chemdah", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "goalVars", "Link/ptms/chemdah/util/Function2;", "getGoalVars$Chemdah", "goals", "getGoals$Chemdah", "ignoreCancelled", "getIgnoreCancelled", "()Z", "isAsync", "isAsync$annotations", "isListener", "isTickable", "name", "getName", "()Ljava/lang/String;", "<set-?>", "Lorg/bukkit/entity/Player;", "playerHandler", "getPlayerHandler$Chemdah", "()Ljava/util/function/Function;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "getPriority", "()Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "using", "getUsing", "setUsing", "(Z)V", "addConditionVariable", "", "func", "addFullCondition", "addGoal", "addGoalVariable", "addSimpleCondition", "Link/ptms/chemdah/core/Data;", "checkComplete", "Ljava/util/concurrent/CompletableFuture;", "profile", "task", "quest", "Link/ptms/chemdah/core/quest/Quest;", "checkCondition", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;", "checkGoal", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "handler", "handle", "hasCompleteImmediately", "hasCompletedSignature", "onComplete", "onContinue", "onReset", "setCompleteImmediately", "value", "setCompletedSignature", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nObjective.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Objective.kt\nink/ptms/chemdah/core/quest/objective/Objective\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,301:1\n167#2,3:302\n135#2,9:305\n215#2:314\n216#2:316\n144#2:317\n167#2,3:318\n135#2,9:321\n215#2:330\n216#2:332\n144#2:333\n1#3:315\n1#3:331\n*S KotlinDebug\n*F\n+ 1 Objective.kt\nink/ptms/chemdah/core/quest/objective/Objective\n*L\n171#1:302,3\n172#1:305,9\n172#1:314\n172#1:316\n172#1:317\n197#1:318,3\n198#1:321,9\n198#1:330\n198#1:332\n198#1:333\n172#1:315\n198#1:331\n*E\n"})
public abstract class Objective<E> {
    private boolean using;
    @NotNull
    private final HashMap<String, Function3<PlayerProfile, Task, E, Boolean>> conditions = new HashMap();
    @NotNull
    private final HashMap<String, Function<E, Couple<String, Object>>> conditionVars = new HashMap();
    @NotNull
    private final HashMap<String, Function2<PlayerProfile, Task, Boolean>> goals = new HashMap();
    @NotNull
    private final HashMap<String, Function2<PlayerProfile, Task, Couple<String, Object>>> goalVars = new HashMap();
    @NotNull
    private final EventPriority priority = EventPriority.HIGHEST;
    private final boolean ignoreCancelled;
    private final boolean isListener;
    private final boolean isAsync;
    private final boolean isTickable;
    @NotNull
    private Function<E, Player> playerHandler = Objective::playerHandler$lambda$0;

    public Objective() {
        this.ignoreCancelled = true;
        this.isListener = true;
    }

    public final boolean getUsing() {
        return this.using;
    }

    public final void setUsing(boolean bl) {
        this.using = bl;
    }

    @NotNull
    public final HashMap<String, Function3<PlayerProfile, Task, E, Boolean>> getConditions$Chemdah() {
        return this.conditions;
    }

    @NotNull
    public final HashMap<String, Function<E, Couple<String, Object>>> getConditionVars$Chemdah() {
        return this.conditionVars;
    }

    @NotNull
    public final HashMap<String, Function2<PlayerProfile, Task, Boolean>> getGoals$Chemdah() {
        return this.goals;
    }

    @NotNull
    public final HashMap<String, Function2<PlayerProfile, Task, Couple<String, Object>>> getGoalVars$Chemdah() {
        return this.goalVars;
    }

    @NotNull
    public abstract String getName();

    @NotNull
    public abstract Class<E> getEvent();

    @NotNull
    public EventPriority getPriority() {
        return this.priority;
    }

    public boolean getIgnoreCancelled() {
        return this.ignoreCancelled;
    }

    public boolean isListener() {
        return this.isListener;
    }

    public boolean isAsync() {
        return this.isAsync;
    }

    @Deprecated(message="\u4e0d\u518d\u652f\u6301")
    public static /* synthetic */ void isAsync$annotations() {
    }

    public boolean isTickable() {
        return this.isTickable;
    }

    @NotNull
    public final Function<E, Player> getPlayerHandler$Chemdah() {
        return this.playerHandler;
    }

    public final void handler(@NotNull Function<E, Player> handle) {
        Intrinsics.checkNotNullParameter(handle, (String)"handle");
        this.playerHandler = handle;
    }

    public void onContinue(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull Object event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
    }

    public void onComplete(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        this.setCompletedSignature(profile, task, true);
        if (this.hasCompleteImmediately(profile, task)) {
            this.setCompleteImmediately(profile, task, false);
        }
        QuestContainer.agent$default(task, quest2.getProfile(), AgentType.TASK_COMPLETED, null, null, 12, null);
        new ObjectiveEvents.Complete.Post(this, task, quest2, profile).call();
    }

    public void onReset(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        profile.dataOperator(task).clear();
        QuestContainer.agent$default(task, quest2.getProfile(), AgentType.TASK_RESTARTED, null, null, 12, null);
        new ObjectiveEvents.Restart.Post(this, task, quest2, profile).call();
    }

    public final void addSimpleCondition(@NotNull String name, @NotNull Function2<Data, E, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.conditions).put(name, (arg_0, arg_1, arg_2) -> Objective.addSimpleCondition$lambda$1(func, name, arg_0, arg_1, arg_2));
    }

    public final void addFullCondition(@NotNull String name, @NotNull Function3<PlayerProfile, Task, E, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.conditions).put(name, func);
    }

    /*
     * Unable to fully structure code
     */
    @NotNull
    public CompletableFuture<Boolean> checkCondition(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest, @NotNull E event) {
        block6: {
            block7: {
                Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
                Intrinsics.checkNotNullParameter((Object)task, (String)"task");
                Intrinsics.checkNotNullParameter((Object)quest, (String)"quest");
                Intrinsics.checkNotNullParameter(event, (String)"event");
                $this$all$iv = this.conditions;
                $i$f$all = false;
                if (!$this$all$iv.isEmpty()) break block7;
                v0 = true;
                break block6;
            }
            var7_8 = $this$all$iv.entrySet().iterator();
            while (var7_8.hasNext()) {
                var9_11 = element$iv = var7_8.next();
                $i$a$-all-Objective$checkCondition$1 = false;
                name = (String)var9_11.getKey();
                cond = (Function3)var9_11.getValue();
                if (!task.getCondition().containsKey(name)) ** GOTO lbl-1000
                v1 = cond.invoke(profile, task, event);
                Intrinsics.checkNotNullExpressionValue(v1, (String)"cond(profile, task, event)");
                if (((Boolean)v1).booleanValue()) lbl-1000:
                // 2 sources

                {
                    v2 = true;
                } else {
                    v2 = false;
                }
                if (v2) continue;
                v0 = false;
                break block6;
            }
            v0 = true;
        }
        if (v0) {
            $this$mapNotNull$iv = this.conditionVars;
            $i$f$mapNotNull = false;
            element$iv = $this$mapNotNull$iv;
            destination$iv$iv = new ArrayList<E>();
            $i$f$mapNotNullTo = false;
            $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            $i$f$forEach = false;
            var13_16 = $this$forEach$iv$iv$iv.entrySet().iterator();
            while (var13_16.hasNext()) {
                element$iv$iv = element$iv$iv$iv = var13_16.next();
                $i$a$-forEach-MapsKt___MapsKt$mapNotNullTo$1$iv$iv = false;
                it = element$iv$iv;
                $i$a$-mapNotNull-Objective$checkCondition$vars$1 = false;
                if ((Pair)UtilsKt.safely((Function0)new Function0<Pair<? extends String, ? extends Object>>(it, event){
                    final /* synthetic */ Map.Entry<String, Function<E, Couple<String, Object>>> $it;
                    final /* synthetic */ E $event;
                    {
                        this.$it = $it;
                        this.$event = $event;
                        super(0);
                    }

                    @NotNull
                    public final Pair<String, Object> invoke() {
                        return this.$it.getValue().apply(this.$event).toPair();
                    }
                }) == null) continue;
                $i$a$-let-MapsKt___MapsKt$mapNotNullTo$1$1$iv$iv = false;
                destination$iv$iv.add(it$iv$iv);
            }
            vars = MapsKt.toMap((Iterable)((List)destination$iv$iv));
            v3 = task.getCondition().get("$");
            v4 = profile.checkAgent(v3 != null ? v3.getData() : null, quest, vars);
        } else {
            v5 = CompletableFuture.completedFuture(false);
            v4 = v5;
            Intrinsics.checkNotNullExpressionValue(v5, (String)"{\n            Completabl\u2026edFuture(false)\n        }");
        }
        return v4;
    }

    public final void addGoal(@NotNull String name, @NotNull Function2<PlayerProfile, Task, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.goals).put(name, func);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public CompletableFuture<Boolean> checkGoal(@NotNull PlayerProfile profile, @NotNull Quest quest2, @NotNull Task task) {
        CompletableFuture<Boolean> completableFuture;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        if (this.hasCompleteImmediately(profile, task)) {
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(true);
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"completedFuture(true)");
        } else {
            Object element$iv;
            boolean bl;
            block8: {
                Map $this$all$iv = this.goals;
                boolean $i$f$all = false;
                if ($this$all$iv.isEmpty()) {
                    bl = true;
                } else {
                    Iterator iterator = $this$all$iv.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Object it = element$iv = iterator.next();
                        boolean bl2 = false;
                        Object r = ((Function2)it.getValue()).invoke(profile, task);
                        Intrinsics.checkNotNullExpressionValue(r, (String)"it.value(profile, task)");
                        if (((Boolean)r).booleanValue()) continue;
                        bl = false;
                        break block8;
                    }
                    bl = true;
                }
            }
            if (bl) {
                void $this$mapNotNullTo$iv$iv;
                Map $this$mapNotNull$iv = this.goalVars;
                boolean $i$f$mapNotNull = false;
                element$iv = $this$mapNotNull$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$mapNotNullTo = false;
                void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                boolean $i$f$forEach = false;
                Iterator iterator = $this$forEach$iv$iv$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    Pair it$iv$iv;
                    Map.Entry element$iv$iv$iv;
                    Map.Entry element$iv$iv = element$iv$iv$iv = iterator.next();
                    boolean bl3 = false;
                    Map.Entry it = element$iv$iv;
                    boolean bl4 = false;
                    if ((Pair)UtilsKt.safely((Function0)new Function0<Pair<? extends String, ? extends Object>>(it, profile, task){
                        final /* synthetic */ Map.Entry<String, Function2<PlayerProfile, Task, Couple<String, Object>>> $it;
                        final /* synthetic */ PlayerProfile $profile;
                        final /* synthetic */ Task $task;
                        {
                            this.$it = $it;
                            this.$profile = $profile;
                            this.$task = $task;
                            super(0);
                        }

                        @NotNull
                        public final Pair<String, Object> invoke() {
                            return this.$it.getValue().invoke(this.$profile, this.$task).toPair();
                        }
                    }) == null) continue;
                    boolean bl5 = false;
                    destination$iv$iv.add(it$iv$iv);
                }
                Map vars2 = MapsKt.toMap((Iterable)((List)destination$iv$iv));
                Data data2 = task.getGoal().get("$");
                completableFuture = profile.checkAgent(data2 != null ? data2.getData() : null, quest2, vars2);
            } else {
                CompletableFuture<Boolean> completableFuture3 = CompletableFuture.completedFuture(false);
                completableFuture = completableFuture3;
                Intrinsics.checkNotNullExpressionValue(completableFuture3, (String)"completedFuture(false)");
            }
        }
        return completableFuture;
    }

    @NotNull
    public CompletableFuture<Boolean> checkComplete(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2) {
        CompletableFuture<Boolean> completableFuture;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        if (this.hasCompletedSignature(profile, task)) {
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(true);
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"{\n            Completabl\u2026tedFuture(true)\n        }");
        } else {
            CompletableFuture<Boolean> completableFuture3;
            CompletableFuture<Boolean> future = completableFuture3 = new CompletableFuture<Boolean>();
            boolean bl = false;
            CompletionStage completionStage = AddonRestart.Companion.canRestart(task, profile).thenAccept(arg_0 -> Objective.checkComplete$lambda$7$lambda$6((Function1)new Function1<Boolean, Unit>(this, task, quest2, profile, future){
                final /* synthetic */ Objective<E> this$0;
                final /* synthetic */ Task $task;
                final /* synthetic */ Quest $quest;
                final /* synthetic */ PlayerProfile $profile;
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.this$0 = $receiver;
                    this.$task = $task;
                    this.$quest = $quest;
                    this.$profile = $profile;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(Boolean restart2) {
                    Intrinsics.checkNotNullExpressionValue((Object)restart2, (String)"restart");
                    if (restart2.booleanValue()) {
                        if (new ObjectiveEvents.Restart.Pre(this.this$0, this.$task, this.$quest, this.$profile).call()) {
                            this.this$0.onReset(this.$profile, this.$task, this.$quest);
                        }
                        FuturesKt.failure(this.$future);
                    } else {
                        CompletionStage completionStage = this.this$0.checkGoal(this.$profile, this.$quest, this.$task).thenAccept(arg_0 -> checkComplete.1.1.invoke$lambda$0((Function1)new Function1<Boolean, Unit>(this.this$0, this.$profile, this.$task, this.$quest, this.$future){
                            final /* synthetic */ Objective<E> this$0;
                            final /* synthetic */ PlayerProfile $profile;
                            final /* synthetic */ Task $task;
                            final /* synthetic */ Quest $quest;
                            final /* synthetic */ CompletableFuture<Boolean> $future;
                            {
                                this.this$0 = $receiver;
                                this.$profile = $profile;
                                this.$task = $task;
                                this.$quest = $quest;
                                this.$future = $future;
                                super(1);
                            }

                            public final void invoke(Boolean it) {
                                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                                if (it.booleanValue() && !this.this$0.hasCompletedSignature(this.$profile, this.$task) && new ObjectiveEvents.Complete.Pre(this.this$0, this.$task, this.$quest, this.$profile).call()) {
                                    this.this$0.onComplete(this.$profile, this.$task, this.$quest);
                                    FuturesKt.success(this.$future);
                                } else {
                                    FuturesKt.failure(this.$future);
                                }
                            }
                        }, arg_0));
                        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun checkComplete(p\u2026        }\n        }\n    }");
                        KetherConcurrentKt.except((CompletableFuture)completionStage);
                    }
                }

                private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    $tmp0.invoke(p0);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun checkComplete(p\u2026        }\n        }\n    }");
            KetherConcurrentKt.except((CompletableFuture)completionStage);
            completableFuture = completableFuture3;
        }
        return completableFuture;
    }

    public void setCompletedSignature(@NotNull PlayerProfile profile, @NotNull Task task, boolean value2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        profile.dataOperator(task).set("completed", value2);
        Quest quest2 = QuestContainer.getQuest$default(task, profile, false, 2, null);
        if (quest2 != null) {
            profile.getPersistentDataContainer().set("quest.complete." + quest2.getId() + '.' + task.getId(), System.currentTimeMillis());
        }
    }

    public void setCompleteImmediately(@NotNull PlayerProfile profile, @NotNull Task task, boolean value2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        profile.dataOperator(task).set("complete-immediately", value2);
    }

    public boolean hasCompletedSignature(@NotNull PlayerProfile profile, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        return profile.dataOperator(task).isTrue("completed");
    }

    public boolean hasCompleteImmediately(@NotNull PlayerProfile profile, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        return profile.dataOperator(task).isTrue("complete-immediately");
    }

    @NotNull
    public Progress getProgress(@NotNull PlayerProfile profile, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        return Progress.Companion.getZERO();
    }

    public final void addConditionVariable(@NotNull String name, @NotNull Function<E, Object> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.conditionVars).put(name, arg_0 -> Objective.addConditionVariable$lambda$8(name, func, arg_0));
    }

    public final void addGoalVariable(@NotNull String name, @NotNull Function2<PlayerProfile, Task, Object> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.goalVars).put(name, (arg_0, arg_1) -> Objective.addGoalVariable$lambda$9(name, func, arg_0, arg_1));
    }

    private static final Player playerHandler$lambda$0(Object it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return null;
    }

    private static final Boolean addSimpleCondition$lambda$1(Function2 $func, String $name, PlayerProfile playerProfile, Task task, Object e) {
        Boolean bl;
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Intrinsics.checkNotNullParameter((Object)$name, (String)"$name");
        try {
            Data data2 = task.getCondition().get($name);
            Intrinsics.checkNotNull((Object)data2);
            bl = (Boolean)$func.invoke(data2, e);
        }
        catch (NoSuchMethodError ex) {
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                        \u6761\u4ef6 \"" + $name + "\" \u4e0d\u517c\u5bb9\u5f53\u524d\u7684 Minecraft \u7248\u672c\u3002\n                        Condition \"" + $name + "\" is not compatible with the current minecraft version.\n                    "))};
            IOKt.warning((Object[])objectArray);
            objectArray = new Object[]{ex.getMessage()};
            IOKt.warning((Object[])objectArray);
            bl = false;
        }
        return bl;
    }

    private static final void checkComplete$lambda$7$lambda$6(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final Couple addConditionVariable$lambda$8(String $name, Function $func, Object it) {
        Intrinsics.checkNotNullParameter((Object)$name, (String)"$name");
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object r = $func.apply(it);
        Intrinsics.checkNotNullExpressionValue(r, (String)"func.apply(it)");
        return new Couple($name, r);
    }

    private static final Couple addGoalVariable$lambda$9(String $name, Function2 $func, PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullParameter((Object)$name, (String)"$name");
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Object r = $func.invoke(profile, task);
        Intrinsics.checkNotNullExpressionValue(r, (String)"func(profile, task)");
        return new Couple($name, r);
    }
}

