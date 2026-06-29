/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/core/quest/selector/InferAreaParser;", "", "()V", "parse", "Link/ptms/chemdah/core/quest/selector/InferArea;", "source", "", "noWorld", "", "Chemdah"})
public class InferAreaParser {
    @NotNull
    public InferArea parse(@NotNull String source, boolean noWorld) {
        InferArea inferArea;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        try {
            inferArea = StringsKt.contains$default((CharSequence)source, (CharSequence)"!>", (boolean)false, (int)2, null) ? (InferArea)new InferArea.Inverted(source, new InferArea.Area(StringsKt.replace$default((String)source, (String)"!>", (String)">", (boolean)false, (int)4, null), noWorld), noWorld) : (StringsKt.contains$default((CharSequence)source, (CharSequence)"!~", (boolean)false, (int)2, null) ? (InferArea)new InferArea.Inverted(source, new InferArea.Range(StringsKt.replace$default((String)source, (String)"!~", (String)"~", (boolean)false, (int)4, null), noWorld), noWorld) : (StringsKt.contains$default((CharSequence)source, (CharSequence)">", (boolean)false, (int)2, null) ? (InferArea)new InferArea.Area(source, noWorld) : (StringsKt.contains$default((CharSequence)source, (CharSequence)"~", (boolean)false, (int)2, null) ? (InferArea)new InferArea.Range(source, noWorld) : (InferArea)new InferArea.Single(source, noWorld))));
        }
        catch (Throwable e) {
            inferArea = new InferArea.Unrecognized(source, String.valueOf(e.getMessage()));
        }
        return inferArea;
    }
}

