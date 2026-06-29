/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.kether.ParsedAction
 *  ink.ptms.chemdah.taboolib.library.kether.QuestContext$Frame
 *  ink.ptms.chemdah.taboolib.library.kether.QuestReader
 *  ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParseBuilderKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptAction
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.module.kether.ActionLocation;
import ink.ptms.chemdah.taboolib.library.kether.ParsedAction;
import ink.ptms.chemdah.taboolib.library.kether.QuestContext;
import ink.ptms.chemdah.taboolib.library.kether.QuestReader;
import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParseBuilderKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptAction;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0006\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J^\u0010\u0003\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u00010\b2\u0018\u0010\n\u001a\u0014\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\f\u0012\u0004\u0012\u00020\r0\u000b2\u0018\u0010\u000e\u001a\u0014\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\f\u0012\u0004\u0012\u00020\r0\u000bJ\u0010\u0010\u000f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0011\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0012\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0013\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0015\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007J\u0010\u0010\u0016\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0010H\u0007\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/module/kether/ActionLocation;", "", "()V", "buildAction", "Link/ptms/chemdah/taboolib/module/kether/ScriptAction;", "it", "Link/ptms/chemdah/taboolib/library/kether/QuestReader;", "read", "Lkotlin1822/Function1;", "Lorg/bukkit/Location;", "write", "Lkotlin1822/Function2;", "", "", "append", "parserBlock", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "parserDistance", "parserPitch", "parserX", "parserY", "parserYaw", "parserZ", "Chemdah"})
public final class ActionLocation {
    @NotNull
    public static final ActionLocation INSTANCE = new ActionLocation();

    private ActionLocation() {
    }

    @KetherParser(value={"x"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserX() {
        return KetherHelperKt.scriptParser((Function1)parserX.1.INSTANCE);
    }

    @KetherParser(value={"y"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserY() {
        return KetherHelperKt.scriptParser((Function1)parserY.1.INSTANCE);
    }

    @KetherParser(value={"z"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserZ() {
        return KetherHelperKt.scriptParser((Function1)parserZ.1.INSTANCE);
    }

    @KetherParser(value={"yaw"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserYaw() {
        return KetherHelperKt.scriptParser((Function1)parserYaw.1.INSTANCE);
    }

    @KetherParser(value={"pitch"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserPitch() {
        return KetherHelperKt.scriptParser((Function1)parserPitch.1.INSTANCE);
    }

    @KetherParser(value={"block"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserBlock() {
        return KetherHelperKt.scriptParser((Function1)parserBlock.1.INSTANCE);
    }

    @KetherParser(value={"distance"}, shared=true)
    @NotNull
    public final ScriptActionParser<Object> parserDistance() {
        return KetherHelperKt.scriptParser((Function1)parserDistance.1.INSTANCE);
    }

    /*
     * Unable to fully structure code
     */
    @NotNull
    public final ScriptAction<Object> buildAction(@NotNull QuestReader it, @NotNull Function1<? super Location, ? extends Object> read, @NotNull Function2<? super Location, ? super Double, Unit> write, @NotNull Function2<? super Location, ? super Double, Unit> append) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Intrinsics.checkNotNullParameter(read, (String)"read");
        Intrinsics.checkNotNullParameter(write, (String)"write");
        Intrinsics.checkNotNullParameter(append, (String)"append");
        it.expect("in");
        input = it.nextParsedAction();
        try {
            block10: {
                it.mark();
                var7_6 = new String[]{"=", "+", "to", "add", "increase"};
                var6_8 = KetherParseBuilderKt.expects((QuestReader)it, (String[])var7_6);
                switch (var6_8.hashCode()) {
                    case 96417: {
                        if (var6_8.equals("add")) break;
                        ** break;
                    }
                    case 3707: {
                        if (!var6_8.equals("to")) {
                            ** break;
                        }
                        ** GOTO lbl28
                    }
                    case 43: {
                        if (var6_8.equals("+")) break;
                        ** break;
                    }
                    case 95321666: {
                        if (var6_8.equals("increase")) break;
                        ** break;
                    }
                    case 61: {
                        if (!var6_8.equals("=")) ** break;
lbl28:
                        // 2 sources

                        value = it.nextParsedAction();
                        v0 = KetherParseBuilderKt.actionFuture$default(null, (Function2)((Function2)new Function2<QuestContext.Frame, CompletableFuture<Object>, Object>(input, value, write){
                            final /* synthetic */ ParsedAction<?> $input;
                            final /* synthetic */ ParsedAction<?> $value;
                            final /* synthetic */ Function2<Location, Double, Unit> $write;
                            {
                                this.$input = $input;
                                this.$value = $value;
                                this.$write = $write;
                                super(2);
                            }

                            @Nullable
                            public final Object invoke(@NotNull QuestContext.Frame $this$actionFuture, @NotNull CompletableFuture<Object> f) {
                                Intrinsics.checkNotNullParameter((Object)$this$actionFuture, (String)"$this$actionFuture");
                                Intrinsics.checkNotNullParameter(f, (String)"f");
                                return $this$actionFuture.newFrame(this.$input).run().thenApply(arg_0 -> buildAction.1.invoke$lambda$0((Function1)new Function1<Location, CompletableFuture<Boolean>>($this$actionFuture, this.$value, this.$write, f){
                                    final /* synthetic */ QuestContext.Frame $this_actionFuture;
                                    final /* synthetic */ ParsedAction<?> $value;
                                    final /* synthetic */ Function2<Location, Double, Unit> $write;
                                    final /* synthetic */ CompletableFuture<Object> $f;
                                    {
                                        this.$this_actionFuture = $this_actionFuture;
                                        this.$value = $value;
                                        this.$write = $write;
                                        this.$f = $f;
                                        super(1);
                                    }

                                    public final CompletableFuture<Boolean> invoke(Location loc) {
                                        ParsedAction<?> parsedAction = this.$value;
                                        Intrinsics.checkNotNullExpressionValue(parsedAction, (String)"value");
                                        return KetherConcurrentKt.double((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$this_actionFuture, parsedAction), (Function1)((Function1)new Function1<Double, Boolean>(this.$write, loc, this.$f){
                                            final /* synthetic */ Function2<Location, Double, Unit> $write;
                                            final /* synthetic */ Location $loc;
                                            final /* synthetic */ CompletableFuture<Object> $f;
                                            {
                                                this.$write = $write;
                                                this.$loc = $loc;
                                                this.$f = $f;
                                                super(1);
                                            }

                                            @NotNull
                                            public final Boolean invoke(double value2) {
                                                Location location = this.$loc;
                                                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"loc");
                                                this.$write.invoke((Object)location, (Object)value2);
                                                return this.$f.complete(this.$loc);
                                            }
                                        }));
                                    }
                                }, arg_0));
                            }

                            private static final CompletableFuture invoke$lambda$0(Function1 $tmp0, Object p0) {
                                Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                                return (CompletableFuture)$tmp0.invoke(p0);
                            }
                        }), (int)1, null);
                        break block10;
                    }
                }
                value = it.nextParsedAction();
                v0 = KetherParseBuilderKt.actionFuture$default(null, (Function2)((Function2)new Function2<QuestContext.Frame, CompletableFuture<Object>, Object>(input, value, append){
                    final /* synthetic */ ParsedAction<?> $input;
                    final /* synthetic */ ParsedAction<?> $value;
                    final /* synthetic */ Function2<Location, Double, Unit> $append;
                    {
                        this.$input = $input;
                        this.$value = $value;
                        this.$append = $append;
                        super(2);
                    }

                    @Nullable
                    public final Object invoke(@NotNull QuestContext.Frame $this$actionFuture, @NotNull CompletableFuture<Object> f) {
                        Intrinsics.checkNotNullParameter((Object)$this$actionFuture, (String)"$this$actionFuture");
                        Intrinsics.checkNotNullParameter(f, (String)"f");
                        return $this$actionFuture.newFrame(this.$input).run().thenApply(arg_0 -> buildAction.2.invoke$lambda$0((Function1)new Function1<Location, CompletableFuture<Boolean>>($this$actionFuture, this.$value, this.$append, f){
                            final /* synthetic */ QuestContext.Frame $this_actionFuture;
                            final /* synthetic */ ParsedAction<?> $value;
                            final /* synthetic */ Function2<Location, Double, Unit> $append;
                            final /* synthetic */ CompletableFuture<Object> $f;
                            {
                                this.$this_actionFuture = $this_actionFuture;
                                this.$value = $value;
                                this.$append = $append;
                                this.$f = $f;
                                super(1);
                            }

                            public final CompletableFuture<Boolean> invoke(Location loc) {
                                ParsedAction<?> parsedAction = this.$value;
                                Intrinsics.checkNotNullExpressionValue(parsedAction, (String)"value");
                                return KetherConcurrentKt.double((CompletableFuture)KetherHelperKt.run((QuestContext.Frame)this.$this_actionFuture, parsedAction), (Function1)((Function1)new Function1<Double, Boolean>(this.$append, loc, this.$f){
                                    final /* synthetic */ Function2<Location, Double, Unit> $append;
                                    final /* synthetic */ Location $loc;
                                    final /* synthetic */ CompletableFuture<Object> $f;
                                    {
                                        this.$append = $append;
                                        this.$loc = $loc;
                                        this.$f = $f;
                                        super(1);
                                    }

                                    @NotNull
                                    public final Boolean invoke(double value2) {
                                        Location location = this.$loc;
                                        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"loc");
                                        this.$append.invoke((Object)location, (Object)value2);
                                        return this.$f.complete(this.$loc);
                                    }
                                }));
                            }
                        }, arg_0));
                    }

                    private static final CompletableFuture invoke$lambda$0(Function1 $tmp0, Object p0) {
                        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                        return (CompletableFuture)$tmp0.invoke(p0);
                    }
                }), (int)1, null);
                break block10;
lbl34:
                // 6 sources

                throw new IllegalStateException("out of case".toString());
            }
            var6_8 = v0;
        }
        catch (Exception ex) {
            it.reset();
            var6_8 = KetherParseBuilderKt.actionFuture$default(null, (Function2)((Function2)new Function2<QuestContext.Frame, CompletableFuture<Object>, Object>(input, read){
                final /* synthetic */ ParsedAction<?> $input;
                final /* synthetic */ Function1<Location, Object> $read;
                {
                    this.$input = $input;
                    this.$read = $read;
                    super(2);
                }

                @Nullable
                public final Object invoke(@NotNull QuestContext.Frame $this$actionFuture, @NotNull CompletableFuture<Object> f) {
                    Intrinsics.checkNotNullParameter((Object)$this$actionFuture, (String)"$this$actionFuture");
                    Intrinsics.checkNotNullParameter(f, (String)"f");
                    return $this$actionFuture.newFrame(this.$input).run().thenApply(arg_0 -> buildAction.3.invoke$lambda$0((Function1)new Function1<Location, Boolean>(f, this.$read){
                        final /* synthetic */ CompletableFuture<Object> $f;
                        final /* synthetic */ Function1<Location, Object> $read;
                        {
                            this.$f = $f;
                            this.$read = $read;
                            super(1);
                        }

                        public final Boolean invoke(Location loc) {
                            Intrinsics.checkNotNullExpressionValue((Object)loc, (String)"loc");
                            return this.$f.complete(this.$read.invoke((Object)loc));
                        }
                    }, arg_0));
                }

                private static final Boolean invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    return (Boolean)$tmp0.invoke(p0);
                }
            }), (int)1, null);
        }
        return var6_8;
    }
}

