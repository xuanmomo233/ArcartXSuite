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
package ink.ptms.chemdah.module.party;

import ink.ptms.chemdah.module.party.TestPartyCommand;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

@CommandHeader(name="chtp", aliases={"chemdahTestParty"}, permission="chemdah.command.testparty")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0006R\u0016\u0010\u000b\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0006R\u0016\u0010\r\u001a\u00020\u000e8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0016\u0010\u0011\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0006\u00a8\u0006\u0013"}, d2={"Link/ptms/chemdah/module/party/TestPartyCommand;", "", "()V", "add", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getAdd", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "create", "getCreate", "delete", "getDelete", "list", "getList", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "remove", "getRemove", "Chemdah"})
public final class TestPartyCommand {
    @NotNull
    public static final TestPartyCommand INSTANCE = new TestPartyCommand();
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody create = SimpleCommandKt.subCommand((Function1)create.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody add = SimpleCommandKt.subCommand((Function1)add.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody remove = SimpleCommandKt.subCommand((Function1)remove.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody delete = SimpleCommandKt.subCommand((Function1)delete.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody list = SimpleCommandKt.subCommand((Function1)list.1.INSTANCE);

    private TestPartyCommand() {
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getCreate() {
        return create;
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
    public final SimpleCommandBody getDelete() {
        return delete;
    }

    @NotNull
    public final SimpleCommandBody getList() {
        return list;
    }
}

