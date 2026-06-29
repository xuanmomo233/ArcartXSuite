/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0007\u001a\u00020\u00052\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004J\u0014\u0010\t\u001a\u00020\u00052\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004J\u0006\u0010\n\u001a\u00020\u0005J\u0006\u0010\u000b\u001a\u00020\u0005R\u0016\u0010\u0003\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0006\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/util/ProcessBool;", "", "()V", "elseFunc", "Lkotlin1822/Function0;", "", "trueFunc", "ifTrue", "func", "orElse", "runElse", "runTrue", "Chemdah"})
public final class ProcessBool {
    @Nullable
    private Function0<Unit> trueFunc;
    @Nullable
    private Function0<Unit> elseFunc;

    public final void ifTrue(@NotNull Function0<Unit> func) {
        Intrinsics.checkNotNullParameter(func, (String)"func");
        this.trueFunc = func;
    }

    public final void orElse(@NotNull Function0<Unit> func) {
        Intrinsics.checkNotNullParameter(func, (String)"func");
        this.elseFunc = func;
    }

    public final void runTrue() {
        block0: {
            Function0<Unit> function0 = this.trueFunc;
            if (function0 == null) break block0;
            function0.invoke();
        }
    }

    public final void runElse() {
        block0: {
            Function0<Unit> function0 = this.elseFunc;
            if (function0 == null) break block0;
            function0.invoke();
        }
    }
}

