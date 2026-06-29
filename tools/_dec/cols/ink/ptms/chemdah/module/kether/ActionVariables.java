/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.module.kether.ActionVariables;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.PlayerOperator;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0006\u0018\u0000 \u00032\u00020\u0001:\u0004\u0003\u0004\u0005\u0006B\u0005\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/module/kether/ActionVariables;", "", "()V", "Companion", "VariablesGet", "VariablesKeys", "VariablesSet", "Chemdah"})
public final class ActionVariables {
    @NotNull
    public static final Companion Companion = new Companion(null);

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/module/kether/ActionVariables$Companion;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @KetherParser(value={"var"}, namespace="chemdah", shared=true)
        @NotNull
        public final ScriptActionParser<? extends Object> parser() {
            return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    /*
     * Illegal identifiers - consider using --renameillegalidents true
     */
    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001B\u001f\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\f\b\u0002\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u000b2\n\u0010\f\u001a\u00060\rj\u0002`\u000eH\u0016R\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/module/kether/ActionVariables$VariablesGet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "default", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getDefault", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getKey", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class VariablesGet
    extends ScriptAction<Object> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final ParsedAction<?> default;

        public VariablesGet(@NotNull ParsedAction<?> key, @NotNull ParsedAction<?> parsedAction) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter(parsedAction, (String)"default");
            this.key = key;
            this.default = parsedAction;
        }

        public /* synthetic */ VariablesGet(ParsedAction parsedAction, ParsedAction parsedAction2, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 2) != 0) {
                ParsedAction parsedAction3 = ParsedAction.noop();
                Intrinsics.checkNotNullExpressionValue((Object)parsedAction3, (String)"noop<Any>()");
                parsedAction2 = parsedAction3;
            }
            this(parsedAction, parsedAction2);
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final ParsedAction<?> getDefault() {
            return this.default;
        }

        @NotNull
        public CompletableFuture<Object> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<Object> future = new CompletableFuture<Object>();
            KetherConcurrentKt.str((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.key), (Function1)((Function1)new Function1<String, CompletableFuture<Boolean>>(frame, this, future){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ VariablesGet this$0;
                final /* synthetic */ CompletableFuture<Object> $future;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    this.$future = $future;
                    super(1);
                }

                @NotNull
                public final CompletableFuture<Boolean> invoke(@NotNull String key) {
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                    return KetherConcurrentKt.str((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$frame, this.this$0.getDefault()), (Function1)((Function1)new Function1<String, Boolean>(this.$future, key){
                        final /* synthetic */ CompletableFuture<Object> $future;
                        final /* synthetic */ String $key;
                        {
                            this.$future = $future;
                            this.$key = $key;
                            super(1);
                        }

                        @NotNull
                        public final Boolean invoke(@NotNull String def) {
                            Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                            String string = ChemdahAPI.INSTANCE.getVariable(this.$key);
                            if (string == null) {
                                string = def;
                            }
                            return this.$future.complete(string);
                        }
                    }));
                }
            }));
            return future;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00030\u00020\u00062\n\u0010\u0007\u001a\u00060\bj\u0002`\tH\u0016\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/module/kether/ActionVariables$VariablesKeys;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "", "", "()V", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class VariablesKeys
    extends ScriptAction<List<? extends String>> {
        @NotNull
        public CompletableFuture<List<String>> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            CompletableFuture<List<String>> completableFuture = CompletableFuture.completedFuture(ChemdahAPI.INSTANCE.getVariables());
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(ChemdahAPI.getVariables())");
            return completableFuture;
        }
    }

    /*
     * Illegal identifiers - consider using --renameillegalidents true
     */
    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B3\u0012\n\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\f\b\u0002\u0010\b\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\u0002\u0010\tJ\u001a\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00020\u00112\n\u0010\u0012\u001a\u00060\u0013j\u0002`\u0014H\u0016R\u0015\u0010\b\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0015\u0010\u0003\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0015\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000b\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/module/kether/ActionVariables$VariablesSet;", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "Ljava/lang/Void;", "key", "Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "value", "symbol", "Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "default", "(Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;Link/ptms/chemdah/taboolib/library/kether/ParsedAction;)V", "getDefault", "()Link/ptms/chemdah/taboolib/library/kether/ParsedAction;", "getKey", "getSymbol", "()Link/ptms/chemdah/taboolib/module/kether/PlayerOperator$Method;", "getValue", "run", "Ljava/util/concurrent/CompletableFuture;", "frame", "Link/ptms/chemdah/taboolib/library/kether/QuestContext$Frame;", "Link/ptms/chemdah/taboolib/module/kether/ScriptFrame;", "Chemdah"})
    public static final class VariablesSet
    extends ScriptAction<Void> {
        @NotNull
        private final ParsedAction<?> key;
        @NotNull
        private final ParsedAction<?> value;
        @NotNull
        private final PlayerOperator.Method symbol;
        @NotNull
        private final ParsedAction<?> default;

        public VariablesSet(@NotNull ParsedAction<?> key, @NotNull ParsedAction<?> value2, @NotNull PlayerOperator.Method symbol, @NotNull ParsedAction<?> parsedAction) {
            Intrinsics.checkNotNullParameter(key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            Intrinsics.checkNotNullParameter((Object)symbol, (String)"symbol");
            Intrinsics.checkNotNullParameter(parsedAction, (String)"default");
            this.key = key;
            this.value = value2;
            this.symbol = symbol;
            this.default = parsedAction;
        }

        public /* synthetic */ VariablesSet(ParsedAction parsedAction, ParsedAction parsedAction2, PlayerOperator.Method method, ParsedAction parsedAction3, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 8) != 0) {
                ParsedAction parsedAction4 = ParsedAction.noop();
                Intrinsics.checkNotNullExpressionValue((Object)parsedAction4, (String)"noop<Any>()");
                parsedAction3 = parsedAction4;
            }
            this(parsedAction, parsedAction2, method, parsedAction3);
        }

        @NotNull
        public final ParsedAction<?> getKey() {
            return this.key;
        }

        @NotNull
        public final ParsedAction<?> getValue() {
            return this.value;
        }

        @NotNull
        public final PlayerOperator.Method getSymbol() {
            return this.symbol;
        }

        @NotNull
        public final ParsedAction<?> getDefault() {
            return this.default;
        }

        @NotNull
        public CompletableFuture<Void> run(@NotNull QuestContext.Frame frame) {
            Intrinsics.checkNotNullParameter((Object)frame, (String)"frame");
            KetherConcurrentKt.str((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)frame, this.key), (Function1)((Function1)new Function1<String, CompletableFuture<CompletableFuture<PlatformExecutor.PlatformTask>>>(frame, this){
                final /* synthetic */ QuestContext.Frame $frame;
                final /* synthetic */ VariablesSet this$0;
                {
                    this.$frame = $frame;
                    this.this$0 = $receiver;
                    super(1);
                }

                @NotNull
                public final CompletableFuture<CompletableFuture<PlatformExecutor.PlatformTask>> invoke(@NotNull String key) {
                    Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                    return KetherConcurrentKt.str((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$frame, this.this$0.getValue()), (Function1)((Function1)new Function1<String, CompletableFuture<PlatformExecutor.PlatformTask>>(this.$frame, this.this$0, key){
                        final /* synthetic */ QuestContext.Frame $frame;
                        final /* synthetic */ VariablesSet this$0;
                        final /* synthetic */ String $key;
                        {
                            this.$frame = $frame;
                            this.this$0 = $receiver;
                            this.$key = $key;
                            super(1);
                        }

                        @NotNull
                        public final CompletableFuture<PlatformExecutor.PlatformTask> invoke(@NotNull String value2) {
                            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                            return KetherConcurrentKt.str((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$frame, this.this$0.getDefault()), (Function1)((Function1)new Function1<String, PlatformExecutor.PlatformTask>(this.$key, value2, this.this$0){
                                final /* synthetic */ String $key;
                                final /* synthetic */ String $value;
                                final /* synthetic */ VariablesSet this$0;
                                {
                                    this.$key = $key;
                                    this.$value = $value;
                                    this.this$0 = $receiver;
                                    super(1);
                                }

                                @NotNull
                                public final PlatformExecutor.PlatformTask invoke(@NotNull String def) {
                                    Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                                    return ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(this.$key, this.$value, this.this$0, def){
                                        final /* synthetic */ String $key;
                                        final /* synthetic */ String $value;
                                        final /* synthetic */ VariablesSet this$0;
                                        final /* synthetic */ String $def;
                                        {
                                            this.$key = $key;
                                            this.$value = $value;
                                            this.this$0 = $receiver;
                                            this.$def = $def;
                                            super(1);
                                        }

                                        public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submitAsync) {
                                            Intrinsics.checkNotNullParameter((Object)$this$submitAsync, (String)"$this$submitAsync");
                                            ChemdahAPI.INSTANCE.setVariable(this.$key, this.$value, this.this$0.getSymbol() == PlayerOperator.Method.INCREASE, this.$def);
                                        }
                                    }), (int)7, null);
                                }
                            }));
                        }
                    }));
                }
            }));
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
    }
}

