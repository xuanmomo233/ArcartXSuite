/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.module.kether.KetherConcurrentKt;
import ink.ptms.chemdah.util.ProcessBool;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000<\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0003\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a*\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u001a0\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00060\u0001\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u0002H\u0003\u0012\u0004\u0012\u00020\u00060\b\u001a0\u0010\t\u001a\b\u0012\u0004\u0012\u0002H\u00030\u0001\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\u0012\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u0002H\u00030\b\u001a0\u0010\f\u001a\b\u0012\u0004\u0012\u0002H\u00030\u0001\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\u0012\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u00060\b\u001a\u0010\u0010\r\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u000e0\u0001\u001a\u0010\u0010\u000f\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u000e0\u0001\u001a/\u0010\u0010\u001a\u00020\u0006\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\u0017\u0010\u0011\u001a\u0013\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00060\b\u00a2\u0006\u0002\b\u0013\u001a$\u0010\u0014\u001a\u00020\u0006\"\u0004\b\u0000\u0010\u0003*\b\u0012\u0004\u0012\u0002H\u00030\u00012\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a8\u0006\u0015"}, d2={"acceptWithError", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "T", "callback", "Lkotlin1822/Function0;", "", "applyWithError", "Lkotlin1822/Function1;", "except", "fn", "", "exceptNull", "failure", "", "success", "thenBool", "proc", "Link/ptms/chemdah/util/ProcessBool;", "Lkotlin1822/ExtensionFunctionType;", "thenTrue", "Chemdah"})
public final class FuturesKt {
    public static final <T> void thenTrue(@NotNull CompletableFuture<T> $this$thenTrue, @NotNull Function0<Unit> proc) {
        Intrinsics.checkNotNullParameter($this$thenTrue, (String)"<this>");
        Intrinsics.checkNotNullParameter(proc, (String)"proc");
        $this$thenTrue.thenApply(arg_0 -> FuturesKt.thenTrue$lambda$0((Function1)new Function1<T, Unit>(proc){
            final /* synthetic */ Function0<Unit> $proc;
            {
                this.$proc = $proc;
                super(1);
            }

            public final void invoke(T it) {
                if (!Intrinsics.areEqual(it, (Object)false)) {
                    this.$proc.invoke();
                }
            }
        }, arg_0));
    }

    public static final <T> void thenBool(@NotNull CompletableFuture<T> $this$thenBool, @NotNull Function1<? super ProcessBool, Unit> proc) {
        Intrinsics.checkNotNullParameter($this$thenBool, (String)"<this>");
        Intrinsics.checkNotNullParameter(proc, (String)"proc");
        ProcessBool processBool = new ProcessBool();
        proc.invoke((Object)processBool);
        ProcessBool processBool2 = processBool;
        CompletionStage completionStage = $this$thenBool.thenApply(arg_0 -> FuturesKt.thenBool$lambda$1((Function1)new Function1<T, Unit>(processBool2){
            final /* synthetic */ ProcessBool $processBool;
            {
                this.$processBool = $processBool;
                super(1);
            }

            public final void invoke(T it) {
                if (!Intrinsics.areEqual(it, (Object)false)) {
                    this.$processBool.runTrue();
                } else {
                    this.$processBool.runElse();
                }
            }
        }, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"processBool = ProcessBoo\u2026ocessBool.runElse()\n    }");
        FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(processBool2){
            final /* synthetic */ ProcessBool $processBool;
            {
                this.$processBool = $processBool;
                super(1);
            }

            public final void invoke(@NotNull Throwable it) {
                Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                this.$processBool.runElse();
            }
        }));
    }

    @NotNull
    public static final <T> CompletableFuture<T> except(@NotNull CompletableFuture<T> $this$except, @NotNull Function1<? super Throwable, ? extends T> fn) {
        Intrinsics.checkNotNullParameter($this$except, (String)"<this>");
        Intrinsics.checkNotNullParameter(fn, (String)"fn");
        CompletionStage completionStage = $this$except.exceptionally(arg_0 -> FuturesKt.except$lambda$2(fn, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"exceptionally {\n        \u2026ce()\n        fn(it)\n    }");
        return completionStage;
    }

    @NotNull
    public static final <T> CompletableFuture<T> exceptNull(@NotNull CompletableFuture<T> $this$exceptNull, @NotNull Function1<? super Throwable, Unit> fn) {
        Intrinsics.checkNotNullParameter($this$exceptNull, (String)"<this>");
        Intrinsics.checkNotNullParameter(fn, (String)"fn");
        CompletionStage completionStage = $this$exceptNull.exceptionally(arg_0 -> FuturesKt.exceptNull$lambda$3(fn, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"exceptionally {\n        \u2026fn(it)\n        null\n    }");
        return completionStage;
    }

    public static final void success(@NotNull CompletableFuture<Boolean> $this$success) {
        Intrinsics.checkNotNullParameter($this$success, (String)"<this>");
        $this$success.complete(true);
    }

    public static final void failure(@NotNull CompletableFuture<Boolean> $this$failure) {
        Intrinsics.checkNotNullParameter($this$failure, (String)"<this>");
        $this$failure.complete(false);
    }

    @NotNull
    public static final <T> CompletableFuture<Unit> applyWithError(@NotNull CompletableFuture<T> $this$applyWithError, @NotNull Function1<? super T, Unit> callback) {
        Intrinsics.checkNotNullParameter($this$applyWithError, (String)"<this>");
        Intrinsics.checkNotNullParameter(callback, (String)"callback");
        CompletionStage completionStage = $this$applyWithError.thenApply(arg_0 -> FuturesKt.applyWithError$lambda$4((Function1)new Function1<T, Unit>(callback){
            final /* synthetic */ Function1<T, Unit> $callback;
            {
                this.$callback = $callback;
                super(1);
            }

            public final void invoke(T it) {
                this.$callback.invoke(it);
            }
        }, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"callback: (T) -> Unit): \u2026henApply { callback(it) }");
        return KetherConcurrentKt.except((CompletableFuture)completionStage);
    }

    @NotNull
    public static final <T> CompletableFuture<Void> acceptWithError(@NotNull CompletableFuture<T> $this$acceptWithError, @NotNull Function0<Unit> callback) {
        Intrinsics.checkNotNullParameter($this$acceptWithError, (String)"<this>");
        Intrinsics.checkNotNullParameter(callback, (String)"callback");
        CompletionStage completionStage = $this$acceptWithError.thenAccept(arg_0 -> FuturesKt.acceptWithError$lambda$5((Function1)new Function1<T, Unit>(callback){
            final /* synthetic */ Function0<Unit> $callback;
            {
                this.$callback = $callback;
                super(1);
            }

            public final void invoke(T it) {
                this.$callback.invoke();
            }
        }, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"callback: () -> Unit): C\u2026thenAccept { callback() }");
        return KetherConcurrentKt.except((CompletableFuture)completionStage);
    }

    private static final Unit thenTrue$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Unit)$tmp0.invoke(p0);
    }

    private static final Unit thenBool$lambda$1(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Unit)$tmp0.invoke(p0);
    }

    private static final Object except$lambda$2(Function1 $fn, Throwable it) {
        Intrinsics.checkNotNullParameter((Object)$fn, (String)"$fn");
        it.printStackTrace();
        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
        return $fn.invoke((Object)it);
    }

    private static final Object exceptNull$lambda$3(Function1 $fn, Throwable it) {
        Intrinsics.checkNotNullParameter((Object)$fn, (String)"$fn");
        it.printStackTrace();
        Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
        $fn.invoke((Object)it);
        return null;
    }

    private static final Unit applyWithError$lambda$4(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Unit)$tmp0.invoke(p0);
    }

    private static final void acceptWithError$lambda$5(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }
}

