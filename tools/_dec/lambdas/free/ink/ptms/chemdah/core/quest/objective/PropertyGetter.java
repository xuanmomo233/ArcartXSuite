/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.jvm.JvmStatic
 *  kotlin1822.Unit
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Task;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin1822.Unit;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00e6\u0080\u0001\u0018\u0000 \n*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0002:\u0001\nJ'\u0010\u0003\u001a\u0004\u0018\u00010\u00022\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00028\u0000H&\u00a2\u0006\u0002\u0010\t\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u000b\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "E", "", "get", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Ljava/lang/Object;)Ljava/lang/Object;", "Companion", "Chemdah"})
public interface PropertyGetter<E> {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.core.quest.objective.PropertyGetter$Companion.$$INSTANCE;

    @Nullable
    public Object get(@NotNull PlayerProfile var1, @NotNull Task var2, @NotNull E var3);

    @NotNull
    public static PropertyGetter<Unit> getNULL() {
        return Companion.getNULL();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\"\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00048\u0006X\u0087\u0004\u00a2\u0006\u000e\n\u0000\u0012\u0004\b\u0006\u0010\u0002\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/quest/objective/PropertyGetter$Companion;", "", "()V", "NULL", "Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "", "getNULL$annotations", "getNULL", "()Link/ptms/chemdah/core/quest/objective/PropertyGetter;", "Chemdah"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;
        @NotNull
        private static final PropertyGetter<Unit> NULL;

        private Companion() {
        }

        @NotNull
        public final PropertyGetter<Unit> getNULL() {
            return NULL;
        }

        @JvmStatic
        public static /* synthetic */ void getNULL$annotations() {
        }

        private static final Object NULL$lambda$0(PlayerProfile playerProfile2, Task task, Unit unit) {
            Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
            Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
            Intrinsics.checkNotNullParameter((Object)unit, (String)"<anonymous parameter 2>");
            return null;
        }

        static {
            $$INSTANCE = new Companion();
            NULL = Companion::NULL$lambda$0;
        }
    }
}

