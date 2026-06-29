/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.module.kether.ActionMath;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J\u0010\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J\u0010\u0010\u0006\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J\u0010\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/module/kether/ActionMath;", "", "()V", "ceil", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "floor", "max", "min", "Chemdah"})
public final class ActionMath {
    @NotNull
    public static final ActionMath INSTANCE = new ActionMath();

    private ActionMath() {
    }

    @KetherParser(value={"max"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> max() {
        return KetherHelperKt.scriptParser((Function1)max.1.INSTANCE);
    }

    @KetherParser(value={"min"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> min() {
        return KetherHelperKt.scriptParser((Function1)min.1.INSTANCE);
    }

    @KetherParser(value={"ceil"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> ceil() {
        return KetherHelperKt.scriptParser((Function1)ceil.1.INSTANCE);
    }

    @KetherParser(value={"floor"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> floor() {
        return KetherHelperKt.scriptParser((Function1)floor.1.INSTANCE);
    }
}

