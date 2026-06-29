/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt
 *  kotlin.Metadata
 *  kotlin.jvm.JvmStatic
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\n\b\u0016\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eB\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0001\u0012\u0006\u0010\u0003\u001a\u00020\u0001\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u0011\u0010\u0003\u001a\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\f\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/objective/Progress;", "", "value", "target", "percent", "", "(Ljava/lang/Object;Ljava/lang/Object;D)V", "getPercent", "()D", "setPercent", "(D)V", "getTarget", "()Ljava/lang/Object;", "getValue", "Companion", "Chemdah"})
public class Progress {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final Object value;
    @NotNull
    private final Object target;
    private double percent;
    @NotNull
    private static final Progress ZERO = new Progress(0, 0, 0.0);

    public Progress(@NotNull Object value2, @NotNull Object target, double percent) {
        double d;
        Progress progress;
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        this.value = value2;
        this.target = target;
        Progress progress2 = this;
        try {
            progress = progress2;
            d = CoerceExtensionsKt.format$default((double)RangesKt.coerceIn((double)percent, (double)0.0, (double)1.0), (int)0, null, (int)3, null);
        }
        catch (Throwable throwable) {
            progress = progress2;
            d = 0.0;
        }
        progress.percent = d;
    }

    @NotNull
    public final Object getValue() {
        return this.value;
    }

    @NotNull
    public final Object getTarget() {
        return this.target;
    }

    public final double getPercent() {
        return this.percent;
    }

    public final void setPercent(double d) {
        this.percent = d;
    }

    @NotNull
    public static final Progress getZERO() {
        return Companion.getZERO();
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0004\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\b\u001a\u00020\u0004*\u00020\t2\u0006\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\u000b\u001a\u00020\fR\u001c\u0010\u0003\u001a\u00020\u00048\u0006X\u0087\u0004\u00a2\u0006\u000e\n\u0000\u0012\u0004\b\u0005\u0010\u0002\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/quest/objective/Progress$Companion;", "", "()V", "ZERO", "Link/ptms/chemdah/core/quest/objective/Progress;", "getZERO$annotations", "getZERO", "()Link/ptms/chemdah/core/quest/objective/Progress;", "toProgress", "", "target", "percent", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nProgress.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Progress.kt\nink/ptms/chemdah/core/quest/objective/Progress$Companion\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,32:1\n11#2:33\n*S KotlinDebug\n*F\n+ 1 Progress.kt\nink/ptms/chemdah/core/quest/objective/Progress$Companion\n*L\n30#1:33\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Progress getZERO() {
            return ZERO;
        }

        @JvmStatic
        public static /* synthetic */ void getZERO$annotations() {
        }

        @NotNull
        public final Progress toProgress(@NotNull Number $this$toProgress, @NotNull Number target, double percent) {
            Intrinsics.checkNotNullParameter((Object)$this$toProgress, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)target, (String)"target");
            return new Progress($this$toProgress, target, percent);
        }

        public static /* synthetic */ Progress toProgress$default(Companion companion, Number number, Number number2, double d, int n, Object object) {
            if ((n & 2) != 0) {
                Number $this$cdouble$iv = number;
                boolean $i$f$getCdouble = false;
                double d2 = Coerce.toDouble((Object)$this$cdouble$iv);
                $this$cdouble$iv = number2;
                $i$f$getCdouble = false;
                d = d2 / Coerce.toDouble((Object)$this$cdouble$iv);
            }
            return companion.toProgress(number, number2, d);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

