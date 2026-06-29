/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0007\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u001c\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/core/conversation/ConversationTransfer;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "id", "", "getId", "()Ljava/lang/String;", "setId", "(Ljava/lang/String;)V", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "Chemdah"})
public class ConversationTransfer {
    @NotNull
    private final ConfigurationSection root;
    @Nullable
    private String id;

    public ConversationTransfer(@NotNull ConfigurationSection root2) {
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        this.id = this.root.getString("id");
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @Nullable
    public final String getId() {
        return this.id;
    }

    public final void setId(@Nullable String string) {
        this.id = string;
    }
}

