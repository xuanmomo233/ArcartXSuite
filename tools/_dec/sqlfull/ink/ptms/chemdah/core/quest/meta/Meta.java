/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.meta;

import ink.ptms.chemdah.core.quest.QuestContainer;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\b&\u0018\u0000*\u0004\b\u0000\u0010\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00028\u0000\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\u0003\u001a\u00028\u0000\u00a2\u0006\n\n\u0002\u0010\u000b\u001a\u0004\b\t\u0010\n\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/meta/Meta;", "T", "", "source", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/Object;Link/ptms/chemdah/core/quest/QuestContainer;)V", "getQuestContainer", "()Link/ptms/chemdah/core/quest/QuestContainer;", "getSource", "()Ljava/lang/Object;", "Ljava/lang/Object;", "Chemdah"})
public abstract class Meta<T> {
    private final T source;
    @NotNull
    private final QuestContainer questContainer;

    public Meta(T source, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        this.source = source;
        this.questContainer = questContainer;
    }

    public final T getSource() {
        return this.source;
    }

    @NotNull
    public final QuestContainer getQuestContainer() {
        return this.questContainer;
    }
}

