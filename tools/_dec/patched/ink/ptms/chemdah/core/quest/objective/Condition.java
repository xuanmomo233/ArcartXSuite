/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.ConditionPattern;
import ink.ptms.chemdah.core.quest.objective.PropertyGetter;
import ink.ptms.chemdah.util.Function3;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\bv\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0002:\u0003\u000f\u0010\u0011J%\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00028\u0000H&\u00a2\u0006\u0002\u0010\u000eR\u0012\u0010\u0003\u001a\u00020\u0004X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u0082\u0001\u0003\u0012\u0013\u0014\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0015\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/quest/objective/Condition;", "E", "", "patternName", "", "getPatternName", "()Ljava/lang/String;", "check", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)Z", "Legacy", "Placeholder", "Standard", "Link/ptms/chemdah/core/quest/objective/Condition$Legacy;", "Link/ptms/chemdah/core/quest/objective/Condition$Placeholder;", "Link/ptms/chemdah/core/quest/objective/Condition$Standard;", "Chemdah"})
public interface Condition<E> {
    @NotNull
    public String getPatternName();

    public boolean check(@NotNull PlayerProfile var1, @NotNull Task var2, @NotNull E var3);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\f\u0018\u0000*\b\b\u0001\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B7\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u0012\u001e\u0010\u0007\u001a\u001a\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00028\u0001\u0012\u0004\u0012\u00020\u000b0\b\u00a2\u0006\u0002\u0010\fJ%\u0010\u0012\u001a\u00020\u000b2\u0006\u0010\u0013\u001a\u00020\t2\u0006\u0010\u0014\u001a\u00020\n2\u0006\u0010\u0015\u001a\u00028\u0001H\u0016\u00a2\u0006\u0002\u0010\u0016R)\u0010\u0007\u001a\u001a\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00028\u0001\u0012\u0004\u0012\u00020\u000b0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0006\u001a\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/objective/Condition$Legacy;", "E", "", "Link/ptms/chemdah/core/quest/objective/Condition;", "name", "", "patternName", "function", "Link/ptms/chemdah/util/Function3;", "Link/ptms/chemdah/core/PlayerProfile;", "Link/ptms/chemdah/core/quest/Task;", "", "(Ljava/lang/String;Ljava/lang/String;Link/ptms/chemdah/util/Function3;)V", "getFunction", "()Link/ptms/chemdah/util/Function3;", "getName", "()Ljava/lang/String;", "getPatternName", "check", "profile", "task", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)Z", "Chemdah"})
    public static final class Legacy<E>
    implements Condition<E> {
        @NotNull
        private final String name;
        @NotNull
        private final String patternName;
        @NotNull
        private final Function3<PlayerProfile, Task, E, Boolean> function;

        public Legacy(@NotNull String name, @NotNull String patternName, @NotNull Function3<PlayerProfile, Task, E, Boolean> function) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)patternName, (String)"patternName");
            Intrinsics.checkNotNullParameter(function, (String)"function");
            this.name = name;
            this.patternName = patternName;
            this.function = function;
        }

        public /* synthetic */ Legacy(String string, String string2, Function3 function3, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                string2 = "Legacy";
            }
            this(string, string2, function3);
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getPatternName() {
            return this.patternName;
        }

        @NotNull
        public final Function3<PlayerProfile, Task, E, Boolean> getFunction() {
            return this.function;
        }

        @Override
        public boolean check(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull E event) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            Intrinsics.checkNotNullParameter(event, (String)"event");
            Boolean bl = this.function.invoke(profile, task, event);
            Intrinsics.checkNotNullExpressionValue((Object)bl, (String)"function(profile, task, event)");
            return bl;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000*\b\b\u0001\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u0015\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J%\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00028\u0001H\u0016\u00a2\u0006\u0002\u0010\u0012R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0014\u0010\u0006\u001a\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\t\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/core/quest/objective/Condition$Placeholder;", "E", "", "Link/ptms/chemdah/core/quest/objective/Condition;", "name", "", "patternName", "(Ljava/lang/String;Ljava/lang/String;)V", "getName", "()Ljava/lang/String;", "getPatternName", "check", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)Z", "Chemdah"})
    public static final class Placeholder<E>
    implements Condition<E> {
        @NotNull
        private final String name;
        @NotNull
        private final String patternName;

        public Placeholder(@NotNull String name, @NotNull String patternName) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)patternName, (String)"patternName");
            this.name = name;
            this.patternName = patternName;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getPatternName() {
            return this.patternName;
        }

        @Override
        public boolean check(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull E event) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            Intrinsics.checkNotNullParameter(event, (String)"event");
            return true;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000*\b\b\u0001\u0010\u0001*\u00020\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B3\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u000e\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\b\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00028\u00010\n\u00a2\u0006\u0002\u0010\u000bJ%\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00028\u0001H\u0016\u00a2\u0006\u0002\u0010\u001aR\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00028\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0019\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0014\u0010\u0006\u001a\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000f\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/core/quest/objective/Condition$Standard;", "E", "", "Link/ptms/chemdah/core/quest/objective/Condition;", "name", "", "patternName", "pattern", "Link/ptms/chemdah/core/quest/objective/ConditionPattern;", "getter", "Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "(Ljava/lang/String;Ljava/lang/String;Link/ptms/chemdah/core/quest/objective/ConditionPattern;Link/ptms/chemdah/core/quest/objective/PropertyGetter;)V", "getGetter", "()Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "getName", "()Ljava/lang/String;", "getPattern", "()Link/ptms/chemdah/core/quest/objective/ConditionPattern;", "getPatternName", "check", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)Z", "Chemdah"})
    public static final class Standard<E>
    implements Condition<E> {
        @NotNull
        private final String name;
        @NotNull
        private final String patternName;
        @NotNull
        private final ConditionPattern<Object> pattern;
        @NotNull
        private final PropertyGetter<E> getter;

        public Standard(@NotNull String name, @NotNull String patternName, @NotNull ConditionPattern<Object> pattern, @NotNull PropertyGetter<E> getter) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)patternName, (String)"patternName");
            Intrinsics.checkNotNullParameter(pattern, (String)"pattern");
            Intrinsics.checkNotNullParameter(getter, (String)"getter");
            this.name = name;
            this.patternName = patternName;
            this.pattern = pattern;
            this.getter = getter;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getPatternName() {
            return this.patternName;
        }

        @NotNull
        public final ConditionPattern<Object> getPattern() {
            return this.pattern;
        }

        @NotNull
        public final PropertyGetter<E> getGetter() {
            return this.getter;
        }

        @Override
        public boolean check(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull E event) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            Intrinsics.checkNotNullParameter(event, (String)"event");
            Data data2 = task.getCondition().get(this.name);
            Intrinsics.checkNotNull((Object)data2);
            return this.pattern.check(data2, this.getter.get(profile, task, event));
        }
    }
}

