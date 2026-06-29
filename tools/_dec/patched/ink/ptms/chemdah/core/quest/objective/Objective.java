/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  kotlin.Deprecated
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.Unit
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.QuestDataOperator;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.addon.AddonRestart;
import ink.ptms.chemdah.core.quest.objective.Condition;
import ink.ptms.chemdah.core.quest.objective.ConditionRegistry;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.Progress;
import ink.ptms.chemdah.core.quest.objective.PropertyGetter;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0094\u0001\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u000b\b&\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0002B\u0005\u00a2\u0006\u0002\u0010\u0003J$\u00103\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0006\u00104\u001a\u00020\u00062\f\u00105\u001a\b\u0012\u0004\u0012\u00028\u000006J\"\u00107\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0012\u00108\u001a\u000e\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u00020\u0007J.\u00109\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u001e\u00108\u001a\u001a\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u001c0:J6\u00109\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0006\u00104\u001a\u00020\u00062\u001e\u00108\u001a\u001a\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u001c0:J(\u0010;\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0018\u00108\u001a\u0014\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u001c0\u0014J0\u0010;\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0006\u00104\u001a\u00020\u00062\u0018\u00108\u001a\u0014\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u001c0\u0014J(\u0010<\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0018\u00108\u001a\u0014\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00020\u0014J\u0016\u0010=\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0006\u00104\u001a\u00020\u0006J\u0016\u0010>\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0006\u00104\u001a\u00020\u0006J(\u0010?\u001a\u00020\u00192\u0006\u0010#\u001a\u00020\u00062\u0018\u00108\u001a\u0014\u0012\u0004\u0012\u00020@\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00020\u001c0\u0014J&\u0010A\u001a\b\u0012\u0004\u0012\u00020\u001c0B2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010E\u001a\u00020FH\u0016J3\u0010G\u001a\b\u0012\u0004\u0012\u00020\u001c0B2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010E\u001a\u00020F2\u0006\u0010\u000f\u001a\u00028\u0000H\u0016\u00a2\u0006\u0002\u0010HJ&\u0010I\u001a\b\u0012\u0004\u0012\u00020\u001c0B2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010E\u001a\u00020F2\u0006\u0010D\u001a\u00020\u0016H\u0016J\u0018\u0010J\u001a\u00020K2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u0016H\u0016J\u001c\u0010L\u001a\u00020\u00192\u0014\u0010M\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010'0\u0007J\u0018\u0010N\u001a\u00020\u001c2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u0016H\u0016J\u0018\u0010O\u001a\u00020\u001c2\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u0016H\u0016J \u0010P\u001a\u00020\u00192\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010E\u001a\u00020FH\u0016J(\u0010Q\u001a\u00020\u00192\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010E\u001a\u00020F2\u0006\u0010\u000f\u001a\u00020\u0002H\u0016J \u0010R\u001a\u00020\u00192\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010E\u001a\u00020FH\u0016J \u0010S\u001a\u00020\u00192\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010T\u001a\u00020\u001cH\u0016J \u0010U\u001a\u00020\u00192\u0006\u0010C\u001a\u00020\u00152\u0006\u0010D\u001a\u00020\u00162\u0006\u0010T\u001a\u00020\u001cH\u0016R`\u0010\u0004\u001aN\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00028\u0000\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u00070\u0005j&\u0012\u0004\u0012\u00020\u0006\u0012\u001c\u0012\u001a\u0012\u0004\u0012\u00028\u0000\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u0007`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR<\u0010\f\u001a*\u0012\u0004\u0012\u00020\u0006\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\r0\u0005j\u0014\u0012\u0004\u0012\u00020\u0006\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\r`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000bR\u0018\u0010\u000f\u001a\b\u0012\u0004\u0012\u00028\u00000\u0010X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0011\u0010\u0012Rl\u0010\u0013\u001aZ\u0012\u0004\u0012\u00020\u0006\u0012\"\u0012 \u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u00140\u0005j,\u0012\u0004\u0012\u00020\u0006\u0012\"\u0012 \u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00020\b0\u0014`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u000bR<\u0010\u0018\u001a*\u0012\u0004\u0012\u00020\u0006\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00190\r0\u0005j\u0014\u0012\u0004\u0012\u00020\u0006\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00190\r`\tX\u0080\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u000bR\u0014\u0010\u001b\u001a\u00020\u001cX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u001c\u0010\u001f\u001a\u00020\u001c8\u0016X\u0097D\u00a2\u0006\u000e\n\u0000\u0012\u0004\b \u0010\u0003\u001a\u0004\b\u001f\u0010\u001eR\u0014\u0010!\u001a\u00020\u001cX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001eR\u0014\u0010\"\u001a\u00020\u001cX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u001eR\u0012\u0010#\u001a\u00020\u0006X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b$\u0010%R:\u0010(\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010'0\u00072\u0014\u0010&\u001a\u0010\u0012\u0004\u0012\u00028\u0000\u0012\u0006\u0012\u0004\u0018\u00010'0\u0007@BX\u0080\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0014\u0010+\u001a\u00020,X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010.R\u001a\u0010/\u001a\u00020\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b0\u0010\u001e\"\u0004\b1\u00102\u00a8\u0006V"}, d2={"Link/ptms/chemdah/core/quest/objective/Objective;", "E", "", "()V", "conditionVars", "Ljava/util/HashMap;", "", "Ljava/util/function/Function;", "Link/ptms/chemdah/util/Couple;", "Lkotlin1822/collections/HashMap;", "getConditionVars$Chemdah", "()Ljava/util/HashMap;", "conditions", "Link/ptms/chemdah/core/quest/objective/Condition;", "getConditions$Chemdah", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "goalVars", "Link/ptms/chemdah/util/Function2;", "Link/ptms/chemdah/core/PlayerProfile;", "Link/ptms/chemdah/core/quest/Task;", "getGoalVars$Chemdah", "goals", "", "getGoals$Chemdah", "ignoreCancelled", "", "getIgnoreCancelled", "()Z", "isAsync", "isAsync$annotations", "isListener", "isTickable", "name", "getName", "()Ljava/lang/String;", "<set-?>", "Lorg/bukkit/entity/Player;", "playerHandler", "getPlayerHandler$Chemdah", "()Ljava/util/function/Function;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "getPriority", "()Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "using", "getUsing", "setUsing", "(Z)V", "addCondition", "pattern", "getter", "Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "addConditionVariable", "func", "addFullCondition", "Link/ptms/chemdah/util/Function3;", "addGoal", "addGoalVariable", "addPlaceholderCondition", "addPlaceholderGoal", "addSimpleCondition", "Link/ptms/chemdah/core/Data;", "checkComplete", "Ljava/util/concurrent/CompletableFuture;", "profile", "task", "quest", "Link/ptms/chemdah/core/quest/Quest;", "checkCondition", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;", "checkGoal", "getProgress", "Link/ptms/chemdah/core/quest/objective/Progress;", "handler", "handle", "hasCompleteImmediately", "hasCompletedSignature", "onComplete", "onContinue", "onReset", "setCompleteImmediately", "value", "setCompletedSignature", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nObjective.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Objective.kt\nink/ptms/chemdah/core/quest/objective/Objective\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,354:1\n167#2,3:355\n135#2,9:358\n215#2:367\n216#2:369\n144#2:370\n167#2,3:371\n135#2,9:374\n215#2:383\n216#2:385\n144#2:386\n1#3:368\n1#3:384\n*S KotlinDebug\n*F\n+ 1 Objective.kt\nink/ptms/chemdah/core/quest/objective/Objective\n*L\n194#1:355,3\n195#1:358,9\n195#1:367\n195#1:369\n195#1:370\n238#1:371,3\n239#1:374,9\n239#1:383\n239#1:385\n239#1:386\n195#1:368\n239#1:384\n*E\n"})
public abstract class Objective<E> {
    private boolean using;
    @NotNull
    private final HashMap<String, Condition<E>> conditions = new HashMap();
    @NotNull
    private final HashMap<String, Function<E, Couple<String, Object>>> conditionVars = new HashMap();
    @NotNull
    private final HashMap<String, Condition<Unit>> goals = new HashMap();
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
    public final HashMap<String, Condition<E>> getConditions$Chemdah() {
        return this.conditions;
    }

    @NotNull
    public final HashMap<String, Function<E, Couple<String, Object>>> getConditionVars$Chemdah() {
        return this.conditionVars;
    }

    @NotNull
    public final HashMap<String, Condition<Unit>> getGoals$Chemdah() {
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

    public final void addCondition(@NotNull String name, @NotNull String pattern, @NotNull PropertyGetter<E> getter) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)pattern, (String)"pattern");
        Intrinsics.checkNotNullParameter(getter, (String)"getter");
        ((Map)this.conditions).put(name, (Condition)new Condition.Standard<E>(name, pattern, ConditionRegistry.INSTANCE.getPattern(pattern), getter));
    }

    public final void addPlaceholderCondition(@NotNull String name, @NotNull String pattern) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)pattern, (String)"pattern");
        ((Map)this.conditions).put(name, new Condition.Placeholder(name, pattern));
    }

    public final void addSimpleCondition(@NotNull String name, @NotNull Function2<Data, E, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        this.addFullCondition(name, (arg_0, arg_1, arg_2) -> Objective.addSimpleCondition$lambda$1(func, name, arg_0, arg_1, arg_2));
    }

    public final void addFullCondition(@NotNull String name, @NotNull Function3<PlayerProfile, Task, E, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.conditions).put(name, new Condition.Legacy<E>(name, "Legacy", func));
    }

    public final void addFullCondition(@NotNull String name, @NotNull String pattern, @NotNull Function3<PlayerProfile, Task, E, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)pattern, (String)"pattern");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.conditions).put(name, new Condition.Legacy<E>(name, pattern, func));
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public CompletableFuture<Boolean> checkCondition(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull E event) {
        CompletableFuture<Boolean> completableFuture;
        Object element$iv;
        boolean bl;
        block6: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
            Intrinsics.checkNotNullParameter(event, (String)"event");
            Map $this$all$iv = this.conditions;
            boolean $i$f$all = false;
            if ($this$all$iv.isEmpty()) {
                bl = true;
            } else {
                Iterator iterator = $this$all$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    Object object = element$iv = iterator.next();
                    boolean bl2 = false;
                    String name = (String)object.getKey();
                    Condition cond = (Condition)object.getValue();
                    if (!task.getCondition().containsKey(name) || cond.check(profile, task, event)) continue;
                    bl = false;
                    break block6;
                }
                bl = true;
            }
        }
        if (bl) {
            void $this$mapNotNullTo$iv$iv;
            Map $this$mapNotNull$iv = this.conditionVars;
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
                boolean bl5 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            Map vars2 = MapsKt.toMap((Iterable)((List)destination$iv$iv));
            Data data2 = task.getCondition().get("$");
            completableFuture = profile.checkAgent(data2 != null ? data2.getData() : null, quest2, vars2);
        } else {
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(false);
            completableFuture = completableFuture2;
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"{\n            Completabl\u2026edFuture(false)\n        }");
        }
        return completableFuture;
    }

    public final void addPlaceholderGoal(@NotNull String name, @NotNull String pattern) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)pattern, (String)"pattern");
        ((Map)this.goals).put(name, new Condition.Placeholder(name, pattern));
    }

    public final void addGoal(@NotNull String name, @NotNull Function2<PlayerProfile, Task, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.goals).put(name, new Condition.Legacy(name, null, (arg_0, arg_1, arg_2) -> Objective.addGoal$lambda$4(func, arg_0, arg_1, arg_2), 2, null));
    }

    public final void addGoal(@NotNull String name, @NotNull String pattern, @NotNull Function2<PlayerProfile, Task, Boolean> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)pattern, (String)"pattern");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.goals).put(name, new Condition.Legacy<Unit>(name, pattern, (arg_0, arg_1, arg_2) -> Objective.addGoal$lambda$5(func, arg_0, arg_1, arg_2)));
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
        try {
            CompletableFuture<Boolean> completableFuture2;
            if (this.hasCompleteImmediately(profile, task)) {
                CompletableFuture<Boolean> completableFuture3 = CompletableFuture.completedFuture(true);
                completableFuture2 = completableFuture3;
                Intrinsics.checkNotNullExpressionValue(completableFuture3, (String)"completedFuture(true)");
            } else {
                Object element$iv;
                boolean bl;
                block10: {
                    Map $this$all$iv = this.goals;
                    boolean $i$f$all = false;
                    if ($this$all$iv.isEmpty()) {
                        bl = true;
                    } else {
                        Iterator iterator = $this$all$iv.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Object object = element$iv = iterator.next();
                            boolean bl2 = false;
                            Condition cond = (Condition)object.getValue();
                            if (cond.check(profile, task, Unit.INSTANCE)) continue;
                            bl = false;
                            break block10;
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
                    completableFuture2 = profile.checkAgent(data2 != null ? data2.getData() : null, quest2, vars2);
                } else {
                    CompletableFuture<Boolean> completableFuture4 = CompletableFuture.completedFuture(false);
                    completableFuture2 = completableFuture4;
                    Intrinsics.checkNotNullExpressionValue(completableFuture4, (String)"completedFuture(false)");
                }
            }
            completableFuture = completableFuture2;
        }
        catch (Throwable ex) {
            Object[] objectArray = new Object[]{"\u68c0\u67e5\u4efb\u52a1 " + quest2.getId() + " \u7684\u6761\u76ee " + task.getId() + " \u51fa\u9519: " + ex.getMessage()};
            IOKt.warning((Object[])objectArray);
            ex.printStackTrace();
            CompletableFuture<Boolean> completableFuture5 = CompletableFuture.completedFuture(false);
            Intrinsics.checkNotNullExpressionValue(completableFuture5, (String)"{\n            warning(\"\u68c0\u2026edFuture(false)\n        }");
            completableFuture = completableFuture5;
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
            FuturesKt.applyWithError(AddonRestart.Companion.canRestart(task, profile), (Function1)new Function1<Boolean, Unit>(this, task, quest2, profile, future){
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

                public final void invoke(boolean restart2) {
                    if (restart2) {
                        if (new ObjectiveEvents.Restart.Pre(this.this$0, this.$task, this.$quest, this.$profile).call()) {
                            this.this$0.onReset(this.$profile, this.$task, this.$quest);
                        }
                        FuturesKt.failure(this.$future);
                    } else {
                        FuturesKt.applyWithError(this.this$0.checkGoal(this.$profile, this.$quest, this.$task), (Function1)new Function1<Boolean, Unit>(this.this$0, this.$profile, this.$task, this.$quest, this.$future){
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

                            public final void invoke(boolean it) {
                                if (it && !this.this$0.hasCompletedSignature(this.$profile, this.$task) && new ObjectiveEvents.Complete.Pre(this.this$0, this.$task, this.$quest, this.$profile).call()) {
                                    this.$quest.onFreeze(() -> checkComplete.1.1.invoke$lambda$0(this.this$0, this.$profile, this.$task, this.$quest));
                                    FuturesKt.success(this.$future);
                                } else {
                                    FuturesKt.failure(this.$future);
                                }
                            }

                            private static final void invoke$lambda$0(Objective this$0, PlayerProfile $profile, Task $task, Quest $quest) {
                                Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
                                Intrinsics.checkNotNullParameter((Object)$profile, (String)"$profile");
                                Intrinsics.checkNotNullParameter((Object)$task, (String)"$task");
                                Intrinsics.checkNotNullParameter((Object)$quest, (String)"$quest");
                                this$0.onComplete($profile, $task, $quest);
                            }
                        });
                    }
                }
            });
            completableFuture = completableFuture3;
        }
        return completableFuture;
    }

    public void setCompletedSignature(@NotNull PlayerProfile profile, @NotNull Task task, boolean value2) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        profile.dataOperator(task).set("completed", value2);
        Quest quest2 = QuestContainer.getQuest$default(task, profile, false, 2, null);
        if (quest2 != null && quest2.getTemplate().getRecordTaskCompleted()) {
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
        QuestDataOperator operator = profile.dataOperator(task);
        if (operator.getQuest() == null) {
            return false;
        }
        return operator.isTrue("completed");
    }

    public boolean hasCompleteImmediately(@NotNull PlayerProfile profile, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        QuestDataOperator operator = profile.dataOperator(task);
        if (operator.getQuest() == null) {
            return false;
        }
        return operator.isTrue("complete-immediately");
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
        ((Map)this.conditionVars).put(name, arg_0 -> Objective.addConditionVariable$lambda$9(name, func, arg_0));
    }

    public final void addGoalVariable(@NotNull String name, @NotNull Function2<PlayerProfile, Task, Object> func) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter(func, (String)"func");
        ((Map)this.goalVars).put(name, (arg_0, arg_1) -> Objective.addGoalVariable$lambda$10(name, func, arg_0, arg_1));
    }

    private static final Player playerHandler$lambda$0(Object it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return null;
    }

    private static final Boolean addSimpleCondition$lambda$1(Function2 $func, String $name, PlayerProfile playerProfile2, Task task, Object e) {
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

    private static final Boolean addGoal$lambda$4(Function2 $func, PlayerProfile profile, Task task, Unit unit) {
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        return (Boolean)$func.invoke(profile, task);
    }

    private static final Boolean addGoal$lambda$5(Function2 $func, PlayerProfile profile, Task task, Unit unit) {
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        return (Boolean)$func.invoke(profile, task);
    }

    private static final Couple addConditionVariable$lambda$9(String $name, Function $func, Object it) {
        Intrinsics.checkNotNullParameter((Object)$name, (String)"$name");
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Object r = $func.apply(it);
        Intrinsics.checkNotNullExpressionValue(r, (String)"func.apply(it)");
        return new Couple($name, r);
    }

    private static final Couple addGoalVariable$lambda$10(String $name, Function2 $func, PlayerProfile profile, Task task) {
        Intrinsics.checkNotNullParameter((Object)$name, (String)"$name");
        Intrinsics.checkNotNullParameter((Object)$func, (String)"$func");
        Object r = $func.invoke(profile, task);
        Intrinsics.checkNotNullExpressionValue(r, (String)"func(profile, task)");
        return new Couple($name, r);
    }
}

