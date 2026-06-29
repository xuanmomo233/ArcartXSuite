/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.core.quest.QuestContainer;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b&\u0018\u00002\u00020\u0001B\u0017\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0001\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\u0002\u0010\u0005R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0001\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/core/quest/addon/Addon;", "", "root", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/Object;Link/ptms/chemdah/core/quest/QuestContainer;)V", "getQuestContainer", "()Link/ptms/chemdah/core/quest/QuestContainer;", "getRoot", "()Ljava/lang/Object;", "Chemdah"})
public abstract class Addon {
    @Nullable
    private final Object root;
    @NotNull
    private final QuestContainer questContainer;

    public Addon(@Nullable Object root2, @NotNull QuestContainer questContainer) {
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        this.root = root2;
        this.questContainer = questContainer;
    }

    @Nullable
    public final Object getRoot() {
        return this.root;
    }

    @NotNull
    public final QuestContainer getQuestContainer() {
        return this.questContainer;
    }
}

