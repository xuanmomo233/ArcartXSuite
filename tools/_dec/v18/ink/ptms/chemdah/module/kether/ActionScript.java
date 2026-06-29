/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.Quest
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import com.google.common.collect.ImmutableList;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.module.kether.ActionScript;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.Quest;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0005\u0018\u0000 \u00032\u00020\u0001:\u0003\u0003\u0004\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/module/kether/ActionScript;", "", "()V", "Companion", "ScriptRun", "ScriptStop", "Chemdah"})
public final class ActionScript {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00010\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionScript$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"script"}, namespace="chemdah", shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B'\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0010\u0010\u0007\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\t0\b\u00a2\u0006\u0002\u0010\nJ\u001a\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00020\u00122\n\u0010\u0013\u001a\u00060\u0014j\u0002`\u0015H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u001b\u0010\u0007\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0016"}, d2={"Link/ptms/chemdah/module/kether/ActionScript$ScriptRun;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "name", "", "self", "", "using", "", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "(Ljava/lang/String;ZLjava/util/List;)V", "getName", "()Ljava/lang/String;", "getSelf", "()Z", "getUsing", "()Ljava/util/List;", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class ScriptRun
    extends ScriptAction<Object> {
        @NotNull
        private final String name;
        private final boolean self;
        @NotNull
        private final List<ParsedAction<?>> using;

        public ScriptRun(@NotNull String name, boolean self, @NotNull List<? extends ParsedAction<?>> using) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter(using, (String)"using");
            this.name = name;
            this.self = self;
            this.using = using;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        public final boolean getSelf() {
            return this.self;
        }

        @NotNull
        public final List<ParsedAction<?>> getUsing() {
            return this.using;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> future = new CompletableFuture<Object>();
            ArrayList<Object> args = new ArrayList<Object>();
            ScriptRun.run$process(this, frame, future, args, 0);
            return future;
        }

        private static final void run$process$lambda$0(ArrayList $args, int $cur, ScriptRun this$0, QuestContext.Frame $frame, CompletableFuture $future, Object it) {
            Intrinsics.checkNotNullParameter((Object)$args, (String)"$args");
            Intrinsics.checkNotNullParameter((Object)((Object)this$0), (String)"this$0");
            Intrinsics.checkNotNullParameter((Object)$frame, (String)"$frame");
            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
            $args.add(it);
            ScriptRun.run$process(this$0, $frame, $future, $args, $cur + 1);
        }

        private static final void run$process(ScriptRun this$0, QuestContext.Frame $frame, CompletableFuture<Object> future, ArrayList<Object> args, int cur) {
            if (cur < this$0.using.size()) {
                $frame.newFrame(this$0.using.get(cur)).run().thenAccept(arg_0 -> ScriptRun.run$process$lambda$0(args, cur, this$0, $frame, future, arg_0));
            } else {
                Quest script = (Quest)ChemdahAPI.INSTANCE.getWorkspace().getScripts().get(this$0.name);
                if (script != null) {
                    KetherHelperKt.runKether$default(null, (boolean)false, (Function0)((Function0)new Function0<CompletableFuture<Void>>(this$0, $frame, script, args, future){
                        final /* synthetic */ ScriptRun this$0;
                        final /* synthetic */ QuestContext.Frame $frame;
                        final /* synthetic */ Quest $script;
                        final /* synthetic */ ArrayList<Object> $args;
                        final /* synthetic */ CompletableFuture<Object> $future;
                        {
                            this.this$0 = $receiver;
                            this.$frame = $frame;
                            this.$script = $script;
                            this.$args = $args;
                            this.$future = $future;
                            super(0);
                        }

                        public final CompletableFuture<Void> invoke() {
                            String scriptId = this.this$0.getSelf() ? this.this$0.getName() + '@' + UtilsForKetherKt.getBukkitPlayer(this.$frame).getName() : this.this$0.getName();
                            ScriptContext scriptContext2 = ScriptContext.Companion.create(this.$script, (Function1)new Function1<ScriptContext, Unit>(this.$frame, this.$args){
                                final /* synthetic */ QuestContext.Frame $frame;
                                final /* synthetic */ ArrayList<Object> $args;
                                {
                                    this.$frame = $frame;
                                    this.$args = $args;
                                    super(1);
                                }

                                /*
                                 * WARNING - void declaration
                                 */
                                public final void invoke(@NotNull ScriptContext $this$create) {
                                    Intrinsics.checkNotNullParameter((Object)$this$create, (String)"$this$create");
                                    $this$create.setSender((ProxyCommandSender)AdapterKt.adaptPlayer((Object)UtilsForKetherKt.getBukkitPlayer(this.$frame)));
                                    Iterable $this$forEachIndexed$iv = this.$args;
                                    boolean $i$f$forEachIndexed = false;
                                    int index$iv = 0;
                                    for (T item$iv : $this$forEachIndexed$iv) {
                                        void any;
                                        int n;
                                        if ((n = index$iv++) < 0) {
                                            CollectionsKt.throwIndexOverflow();
                                        }
                                        T t = item$iv;
                                        int index = n;
                                        boolean bl = false;
                                        $this$create.set("arg" + index, (Object)any);
                                    }
                                }
                            });
                            return ChemdahAPI.INSTANCE.getWorkspace().runScript(scriptId, scriptContext2).thenAccept(arg_0 -> run.process.2.invoke$lambda$0(this.$future, arg_0));
                        }

                        private static final void invoke$lambda$0(CompletableFuture $future, Object it) {
                            Intrinsics.checkNotNullParameter((Object)$future, (String)"$future");
                            $future.complete(it);
                        }
                    }), (int)3, null);
                } else {
                    Object[] objectArray = new Object[]{"Script " + this$0.name + " not found."};
                    IOKt.warning((Object[])objectArray);
                    future.complete(null);
                }
            }
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001a\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00020\r2\n\u0010\u000e\u001a\u00060\u000fj\u0002`\u0010H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/kether/ActionScript$ScriptStop;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "name", "", "self", "", "(Ljava/lang/String;Z)V", "getName", "()Ljava/lang/String;", "getSelf", "()Z", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nActionScript.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionScript.kt\nink/ptms/chemdah/module/kether/ActionScript$ScriptStop\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,104:1\n288#2,2:105\n*S KotlinDebug\n*F\n+ 1 ActionScript.kt\nink/ptms/chemdah/module/kether/ActionScript$ScriptStop\n*L\n59#1:105,2\n*E\n"})
    public static final class ScriptStop
    extends ScriptAction<Void> {
        @NotNull
        private final String name;
        private final boolean self;

        public ScriptStop(@NotNull String name, boolean self) {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            this.name = name;
            this.self = self;
        }

        @NotNull
        public final String getName() {
            return this.name;
        }

        public final boolean getSelf() {
            return this.self;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Object v1;
            block2: {
                Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
                String namespace = this.self ? this.name + '@' + UtilsForKetherKt.getBukkitPlayer(frame).getName() : this.name;
                ImmutableList immutableList = ImmutableList.copyOf((Collection)ChemdahAPI.INSTANCE.getWorkspace().getRunningScript());
                Intrinsics.checkNotNullExpressionValue((Object)immutableList, (String)"copyOf(ChemdahAPI.workspace.getRunningScript())");
                Iterable $this$firstOrNull$iv = (Iterable)immutableList;
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    ScriptContext it = (ScriptContext)element$iv;
                    boolean bl = false;
                    if (!Intrinsics.areEqual((Object)it.getId(), (Object)namespace)) continue;
                    v1 = element$iv;
                    break block2;
                }
                v1 = null;
            }
            ScriptContext script = v1;
            if (script != null) {
                ChemdahAPI.INSTANCE.getWorkspace().terminateScript(script);
            }
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
    }
}

