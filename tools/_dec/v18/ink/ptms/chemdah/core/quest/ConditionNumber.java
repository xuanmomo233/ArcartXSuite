/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.core.quest.ConditionNumber;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0004\n\u0002\b\u0002\u0018\u00002\u00020\u0001:\u0001\u000fB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/ConditionNumber;", "", "source", "", "(Ljava/lang/String;)V", "matcher", "Link/ptms/chemdah/core/quest/ConditionNumber$Matcher;", "getMatcher", "()Link/ptms/chemdah/core/quest/ConditionNumber$Matcher;", "getSource", "()Ljava/lang/String;", "check", "", "input", "", "Matcher", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConditionNumber.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConditionNumber.kt\nink/ptms/chemdah/core/quest/ConditionNumber\n+ 2 CoerceExtensions.kt\ntaboolib/common5/CoerceExtensionsKt\n*L\n1#1,34:1\n11#2:35\n11#2:36\n11#2:37\n11#2:38\n11#2:39\n11#2:40\n11#2:41\n11#2:42\n*S KotlinDebug\n*F\n+ 1 ConditionNumber.kt\nink/ptms/chemdah/core/quest/ConditionNumber\n*L\n17#1:35\n18#1:36\n19#1:37\n20#1:38\n21#1:39\n23#1:40\n24#1:41\n28#1:42\n*E\n"})
public final class ConditionNumber {
    @NotNull
    private final String source;
    @NotNull
    private final Matcher matcher;

    /*
     * WARNING - void declaration
     */
    public ConditionNumber(@NotNull String source) {
        Matcher matcher2;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
        if (StringsKt.startsWith$default((String)this.source, (String)">=", (boolean)false, (int)2, null)) {
            String string = this.source.substring(2);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String $this$cdouble$iv = string;
            boolean $i$f$getCdouble = false;
            Matcher matcher3 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.1.INSTANCE));
            matcher2 = matcher3;
        } else if (StringsKt.startsWith$default((String)this.source, (String)"<=", (boolean)false, (int)2, null)) {
            String string = this.source.substring(2);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String $this$cdouble$iv = string;
            boolean $i$f$getCdouble = false;
            Matcher matcher4 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.2.INSTANCE));
            matcher2 = matcher4;
        } else if (StringsKt.startsWith$default((CharSequence)this.source, (char)'>', (boolean)false, (int)2, null)) {
            String string = this.source.substring(1);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String $this$cdouble$iv = string;
            boolean $i$f$getCdouble = false;
            Matcher matcher5 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.3.INSTANCE));
            matcher2 = matcher5;
        } else if (StringsKt.startsWith$default((CharSequence)this.source, (char)'<', (boolean)false, (int)2, null)) {
            String string = this.source.substring(1);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String $this$cdouble$iv = string;
            boolean $i$f$getCdouble = false;
            Matcher matcher6 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.4.INSTANCE));
            matcher2 = matcher6;
        } else if (StringsKt.startsWith$default((CharSequence)this.source, (char)'=', (boolean)false, (int)2, null)) {
            String string = this.source.substring(1);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String $this$cdouble$iv = string;
            boolean $i$f$getCdouble = false;
            Matcher matcher7 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.5.INSTANCE));
            matcher2 = matcher7;
        } else if (StringsKt.contains$default((CharSequence)this.source, (CharSequence)"..", (boolean)false, (int)2, null)) {
            void $this$cdouble$iv;
            void $this$cdouble$iv2;
            String string = ((Object)StringsKt.trim((CharSequence)StringsKt.substringBefore$default((String)this.source, (String)"..", null, (int)2, null))).toString();
            boolean $i$f$getCdouble = false;
            double min2 = Coerce.toDouble((Object)$this$cdouble$iv2);
            String string2 = ((Object)StringsKt.trim((CharSequence)StringsKt.substringAfter$default((String)this.source, (String)"..", null, (int)2, null))).toString();
            boolean $i$f$getCdouble2 = false;
            double max2 = Coerce.toDouble((Object)$this$cdouble$iv);
            matcher2 = new Matcher(min2, (Function2<? super Double, ? super Number, Boolean>)((Function2)new Function2<Double, Number, Boolean>(min2, max2){
                final /* synthetic */ double $min;
                final /* synthetic */ double $max;
                {
                    this.$min = $min;
                    this.$max = $max;
                    super(2);
                }

                @NotNull
                public final Boolean invoke(double d, @NotNull Number input) {
                    Intrinsics.checkNotNullParameter((Object)input, (String)"input");
                    double d2 = input.doubleValue();
                    return this.$min <= d2 ? d2 <= this.$max : false;
                }
            }));
        } else {
            String $this$cdouble$iv = this.source;
            boolean $i$f$getCdouble = false;
            Matcher matcher8 = new Matcher(Coerce.toDouble((Object)$this$cdouble$iv), (Function2<? super Double, ? super Number, Boolean>)((Function2)matcher.7.INSTANCE));
            matcher2 = matcher8;
        }
        this.matcher = matcher2;
    }

    @NotNull
    public final String getSource() {
        return this.source;
    }

    @NotNull
    public final Matcher getMatcher() {
        return this.matcher;
    }

    public final boolean check(@NotNull Number input) {
        Intrinsics.checkNotNullParameter((Object)input, (String)"input");
        return (Boolean)this.matcher.getMatch().invoke((Object)this.matcher.getNum(), (Object)input);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0004\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\u0018\u00002\u00020\u0001BG\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u00126\u0010\u0004\u001a2\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\u0002\u0012\u0013\u0012\u00110\b\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\t\u0012\u0004\u0012\u00020\n0\u0005\u00a2\u0006\u0002\u0010\u000bRA\u0010\u0004\u001a2\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\u0002\u0012\u0013\u0012\u00110\b\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\t\u0012\u0004\u0012\u00020\n0\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/ConditionNumber$Matcher;", "", "num", "", "match", "Lkotlin1822/Function2;", "Lkotlin1822/ParameterName;", "name", "", "input", "", "(DLkotlin1822/jvm/functions/Function2;)V", "getMatch", "()Lkotlin1822/jvm/functions/Function2;", "getNum", "()D", "Chemdah"})
    public static final class Matcher {
        private final double num;
        @NotNull
        private final Function2<Double, Number, Boolean> match;

        public Matcher(double num, @NotNull Function2<? super Double, ? super Number, Boolean> match) {
            Intrinsics.checkNotNullParameter(match, (String)"match");
            this.num = num;
            this.match = match;
        }

        public /* synthetic */ Matcher(double d, Function2 function2, int n, DefaultConstructorMarker defaultConstructorMarker) {
            if ((n & 1) != 0) {
                d = 0.0;
            }
            this(d, (Function2<? super Double, ? super Number, Boolean>)function2);
        }

        public final double getNum() {
            return this.num;
        }

        @NotNull
        public final Function2<Double, Number, Boolean> getMatch() {
            return this.match;
        }
    }
}

