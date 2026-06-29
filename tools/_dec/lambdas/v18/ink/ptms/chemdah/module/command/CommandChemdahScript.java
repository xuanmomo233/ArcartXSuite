/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt
 *  ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.library.kether.Quest
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  ink.ptms.chemdah.taboolib.module.kether.Workspace
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.command;

import ink.ptms.chemdah.module.command.CommandChemdahScript;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.CommandHeader;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandBody;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandKt;
import ink.ptms.chemdah.taboolib.common.platform.command.SimpleCommandMain;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.library.kether.Quest;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.taboolib.module.kether.Workspace;
import ink.ptms.chemdah.taboolib.platform.util.BukkitLangKt;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * Illegal identifiers - consider using --renameillegalidents true
 */
@CommandHeader(name="ChemdahScript", aliases={"chs"}, permission="chemdah.command")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\b\u0006\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J;\u0010\u001f\u001a\u00020 2\u0006\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$2\n\b\u0002\u0010%\u001a\u0004\u0018\u00010$2\u000e\b\u0002\u0010&\u001a\b\u0012\u0004\u0012\u00020$0'H\u0000\u00a2\u0006\u0004\b(\u0010)J\u001b\u0010*\u001a\u00020 *\u00020\"2\b\u0010+\u001a\u0004\u0018\u00010\u0001H\u0000\u00a2\u0006\u0002\b,R\u0016\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0016\u0010\u0007\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0006R\u0016\u0010\t\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u0006R\u0016\u0010\u000b\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u0006R\u0016\u0010\r\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u0006R\u0016\u0010\u000f\u001a\u00020\u00108\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0016\u0010\u0013\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0006R\u0016\u0010\u0015\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0006R\u0016\u0010\u0017\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0006R\u001b\u0010\u0019\u001a\u00020\u001a8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001d\u0010\u001e\u001a\u0004\b\u001b\u0010\u001c\u00a8\u0006-"}, d2={"Link/ptms/chemdah/module/command/CommandChemdahScript;", "", "()V", "debug", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "getDebug", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandBody;", "invoke", "getInvoke", "invoke-now", "getInvoke-now", "invoke-wait", "getInvoke-wait", "list", "getList", "main", "Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "getMain", "()Link/ptms/chemdah/taboolib/common/platform/command/SimpleCommandMain;", "reload", "getReload", "run", "getRun", "stop", "getStop", "workspace", "Link/ptms/chemdah/taboolib/module/kether/Workspace;", "getWorkspace", "()Link/ptms/chemdah/taboolib/module/kether/Workspace;", "workspace$delegate", "Lkotlin1822/Lazy;", "commandRun", "", "sender", "Lorg/bukkit/command/CommandSender;", "file", "", "viewer", "args", "", "commandRun$Chemdah", "(Lorg/bukkit/command/CommandSender;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V", "sendResult", "v", "sendResult$Chemdah", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nCommandChemdahScript.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CommandChemdahScript.kt\nink/ptms/chemdah/module/command/CommandChemdahScript\n+ 2 ArrayIntrinsics.kt\nkotlin/ArrayIntrinsicsKt\n*L\n1#1,178:1\n26#2:179\n*S KotlinDebug\n*F\n+ 1 CommandChemdahScript.kt\nink/ptms/chemdah/module/command/CommandChemdahScript\n*L\n152#1:179\n*E\n"})
public final class CommandChemdahScript {
    @NotNull
    public static final CommandChemdahScript INSTANCE = new CommandChemdahScript();
    @NotNull
    private static final Lazy workspace$delegate = LazyMakerKt.unsafeLazy((Function0)workspace.2.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandMain main = SimpleCommandKt.mainCommand((Function1)main.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody run = SimpleCommandKt.subCommand((Function1)run.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody stop = SimpleCommandKt.subCommand((Function1)stop.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody list = SimpleCommandKt.subCommand((Function1)list.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody reload = SimpleCommandKt.subCommand((Function1)reload.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody debug = SimpleCommandKt.subCommand((Function1)debug.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody invoke = SimpleCommandKt.subCommand((Function1)invoke.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody invoke-now = SimpleCommandKt.subCommand((Function1)invoke-now.1.INSTANCE);
    @CommandBody
    @NotNull
    private static final SimpleCommandBody invoke-wait = SimpleCommandKt.subCommand((Function1)invoke-wait.1.INSTANCE);

    private CommandChemdahScript() {
    }

    @NotNull
    public final Workspace getWorkspace() {
        Lazy lazy = workspace$delegate;
        return (Workspace)lazy.getValue();
    }

    @NotNull
    public final SimpleCommandMain getMain() {
        return main;
    }

    @NotNull
    public final SimpleCommandBody getRun() {
        return run;
    }

    @NotNull
    public final SimpleCommandBody getStop() {
        return stop;
    }

    @NotNull
    public final SimpleCommandBody getList() {
        return list;
    }

    @NotNull
    public final SimpleCommandBody getReload() {
        return reload;
    }

    @NotNull
    public final SimpleCommandBody getDebug() {
        return debug;
    }

    @NotNull
    public final SimpleCommandBody getInvoke() {
        return invoke;
    }

    @NotNull
    public final SimpleCommandBody getInvoke-now() {
        return invoke-now;
    }

    @NotNull
    public final SimpleCommandBody getInvoke-wait() {
        return invoke-wait;
    }

    public final void sendResult$Chemdah(@NotNull CommandSender $this$sendResult, @Nullable Object v) {
        Intrinsics.checkNotNullParameter((Object)$this$sendResult, (String)"<this>");
        try {
            Class.forName(StringsKt.substringBefore$default((String)String.valueOf(v), (char)'$', null, (int)2, null));
            StringBuilder stringBuilder = new StringBuilder().append("\u00a7c[System] \u00a77Result: \u00a7f");
            Object object = v;
            Intrinsics.checkNotNull((Object)object);
            $this$sendResult.sendMessage(stringBuilder.append(object.getClass().getSimpleName()).append(" \u00a77(Java Object)").toString());
        }
        catch (Throwable _) {
            $this$sendResult.sendMessage("\u00a7c[System] \u00a77Result: \u00a7f" + v);
        }
    }

    public final void commandRun$Chemdah(@NotNull CommandSender sender, @NotNull String file, @Nullable String viewer, @NotNull String[] args) {
        Intrinsics.checkNotNullParameter((Object)sender, (String)"sender");
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)args, (String)"args");
        Quest script = (Quest)this.getWorkspace().getScripts().get(file);
        if (script != null) {
            ScriptContext ctx2 = ScriptContext.Companion.create(script, (Function1)new Function1<ScriptContext, Unit>(viewer, args){
                final /* synthetic */ String $viewer;
                final /* synthetic */ String[] $args;
                {
                    this.$viewer = $viewer;
                    this.$args = $args;
                    super(1);
                }

                public final void invoke(@NotNull ScriptContext $this$create) {
                    Player player;
                    Intrinsics.checkNotNullParameter((Object)$this$create, (String)"$this$create");
                    if (this.$viewer != null && (player = Bukkit.getPlayerExact((String)this.$viewer)) != null) {
                        $this$create.setSender(AdapterKt.adaptCommandSender((Object)player));
                    }
                    for (int i = 0; i < this.$args.length; ++i) {
                        $this$create.set("arg" + i, (Object)this.$args[i]);
                    }
                }
            });
            try {
                this.getWorkspace().runScript(file, ctx2);
            }
            catch (Throwable t) {
                Object[] objectArray = new Object[1];
                Intrinsics.checkNotNullExpressionValue((Object)t.getLocalizedMessage(), (String)"t.localizedMessage");
                BukkitLangKt.sendLang((CommandSender)sender, (String)"command-script-error", (Object[])objectArray);
                KetherHelperKt.printKetherErrorMessage((Throwable)t, (boolean)true);
            }
        } else {
            BukkitLangKt.sendLang((CommandSender)sender, (String)"command-script-not-found", (Object[])new Object[0]);
        }
    }

    public static /* synthetic */ void commandRun$Chemdah$default(CommandChemdahScript commandChemdahScript, CommandSender commandSender, String string, String string2, String[] stringArray, int n, Object object) {
        if ((n & 4) != 0) {
            string2 = null;
        }
        if ((n & 8) != 0) {
            boolean $i$f$emptyArray = false;
            stringArray = new String[]{};
        }
        commandChemdahScript.commandRun$Chemdah(commandSender, string, string2, stringArray);
    }
}

