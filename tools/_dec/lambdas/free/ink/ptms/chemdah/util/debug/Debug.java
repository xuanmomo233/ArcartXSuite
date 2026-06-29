/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.util.debug;

import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/util/debug/Debug;", "", "display", "", "(Ljava/lang/String;ILjava/lang/String;)V", "getDisplay", "()Ljava/lang/String;", "CONVERSATION", "UNKNOWN", "Chemdah"})
public final class Debug
extends Enum<Debug> {
    @NotNull
    private final String display;
    public static final /* enum */ Debug CONVERSATION = new Debug("\u5bf9\u8bdd");
    public static final /* enum */ Debug UNKNOWN = new Debug("\u672a\u5206\u7c7b");
    private static final /* synthetic */ Debug[] $VALUES;

    private Debug(String display2) {
        this.display = display2;
    }

    @NotNull
    public final String getDisplay() {
        return this.display;
    }

    public static Debug[] values() {
        return (Debug[])$VALUES.clone();
    }

    public static Debug valueOf(String value2) {
        return Enum.valueOf(Debug.class, value2);
    }

    static {
        $VALUES = debugArray = new Debug[]{Debug.CONVERSATION, Debug.UNKNOWN};
    }
}

