/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util.debug;

import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.util.debug.DebugCommands;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="chemdah-debug", aliases={"chdebug"})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/util/debug/DebugCommands;", "", "()V", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "Chemdah"})
public final class DebugCommands {
    @NotNull
    public static final DebugCommands INSTANCE = new DebugCommands();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);

    private DebugCommands() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }
}

