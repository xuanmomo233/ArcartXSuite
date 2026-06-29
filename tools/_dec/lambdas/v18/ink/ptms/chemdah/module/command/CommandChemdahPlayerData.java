/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function1
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.module.command.CommandChemdahPlayerData;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="ChemdahPlayerData", aliases={"chpd"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\n8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0016\u0010\r\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0016\u0010\u000f\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0006\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahPlayerData;", "", "()V", "add", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getAdd", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "clear", "getClear", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "remove", "getRemove", "set", "getSet", "Chemdah"})
public final class CommandChemdahPlayerData {
    @NotNull
    public static final CommandChemdahPlayerData INSTANCE = new CommandChemdahPlayerData();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody set = SimpleCommandKt.subCommand((Function1)set.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody add = SimpleCommandKt.subCommand((Function1)add.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody remove = SimpleCommandKt.subCommand((Function1)remove.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody clear = SimpleCommandKt.subCommand((Function1)clear.1.INSTANCE);

    private CommandChemdahPlayerData() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getSet() {
        return set;
    }

    @NotNull
    public final SimpleCommandBody getAdd() {
        return add;
    }

    @NotNull
    public final SimpleCommandBody getRemove() {
        return remove;
    }

    @NotNull
    public final SimpleCommandBody getClear() {
        return clear;
    }
}

