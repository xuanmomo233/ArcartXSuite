/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u0012\u001a\u00020\u00062\u0006\u0010\u0013\u001a\u00020\u00062\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0015R\u0018\u0010\u0005\u001a\t\u0018\u00010\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0018\u0010\n\u001a\t\u0018\u00010\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0018\u0010\u000e\u001a\t\u0018\u00010\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\tR\u0018\u0010\u0010\u001a\t\u0018\u00010\u0006\u00a2\u0006\u0002\b\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\t\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/conversation/LineFormat;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "body", "", "Lorg/jetbrains/annotations/NotNull;", "getBody", "()Ljava/lang/String;", "bottom", "getBottom", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "single", "getSingle", "top", "getTop", "format", "message", "idx", "", "size", "Chemdah"})
public final class LineFormat {
    @NotNull
    private final ConfigurationSection root;
    @Nullable
    private final String single;
    @Nullable
    private final String top;
    @Nullable
    private final String body;
    @Nullable
    private final String bottom;

    public LineFormat(@NotNull ConfigurationSection root2) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        String string = this.root.getString("single");
        this.single = string != null ? UtilKt.colored((String)string) : null;
        String string2 = this.root.getString("top");
        this.top = string2 != null ? UtilKt.colored((String)string2) : null;
        String string3 = this.root.getString("body");
        this.body = string3 != null ? UtilKt.colored((String)string3) : null;
        String string4 = this.root.getString("bottom");
        this.bottom = string4 != null ? UtilKt.colored((String)string4) : null;
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @Nullable
    public final String getSingle() {
        return this.single;
    }

    @Nullable
    public final String getTop() {
        return this.top;
    }

    @Nullable
    public final String getBody() {
        return this.body;
    }

    @Nullable
    public final String getBottom() {
        return this.bottom;
    }

    @NotNull
    public final String format(@NotNull String message2, int idx, int size) {
        Intrinsics.checkNotNullParameter((Object)message2, (String)"message");
        if (this.single == null || this.top == null || this.body == null || this.bottom == null) {
            return message2;
        }
        return StringsKt.replace$default((String)(size == 1 ? this.single : (idx == 0 ? this.top : (idx + 1 < size ? this.body : this.bottom))), (String)"{text}", (String)message2, (boolean)false, (int)4, null);
    }
}

