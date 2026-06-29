/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\b\u0016\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/quest/selector/ItemEnchant;", "", "name", "", "level", "", "(Ljava/lang/String;I)V", "getLevel", "()I", "getName", "()Ljava/lang/String;", "Chemdah"})
public class ItemEnchant {
    @NotNull
    private final String name;
    private final int level;

    public ItemEnchant(@NotNull String name, int level) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        this.name = name;
        this.level = level;
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    public final int getLevel() {
        return this.level;
    }
}

