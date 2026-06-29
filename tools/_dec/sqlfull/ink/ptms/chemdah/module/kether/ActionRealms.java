/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.module.kether.ActionRealms;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionRealms;", "", "()V", "realms", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
public final class ActionRealms {
    @NotNull
    public static final ActionRealms INSTANCE = new ActionRealms();

    private ActionRealms() {
    }

    @KetherParser(value={"realms"}, namespace="chemdah", shared=true)
    @NotNull
    public final ScriptActionParser<Object> realms() {
        return KetherHelperKt.scriptParser((Function1)realms.1.INSTANCE);
    }
}

