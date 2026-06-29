/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.module.command.CommandChemdahPlayerLevel;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="ChemdahPlayerLevel", aliases={"chpl"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\n8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0016\u0010\r\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0016\u0010\u000f\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0006\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahPlayerLevel;", "", "()V", "addexp", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getAddexp", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "addlevel", "getAddlevel", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "setexp", "getSetexp", "setlevel", "getSetlevel", "Chemdah"})
public final class CommandChemdahPlayerLevel {
    @NotNull
    public static final CommandChemdahPlayerLevel INSTANCE = new CommandChemdahPlayerLevel();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody addlevel = SimpleCommandKt.subCommand((Function1)addlevel.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody setlevel = SimpleCommandKt.subCommand((Function1)setlevel.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody addexp = SimpleCommandKt.subCommand((Function1)addexp.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody setexp = SimpleCommandKt.subCommand((Function1)setexp.1.INSTANCE);

    private CommandChemdahPlayerLevel() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getAddlevel() {
        return addlevel;
    }

    @NotNull
    public final SimpleCommandBody getSetlevel() {
        return setlevel;
    }

    @NotNull
    public final SimpleCommandBody getAddexp() {
        return addexp;
    }

    @NotNull
    public final SimpleCommandBody getSetexp() {
        return setexp;
    }
}

