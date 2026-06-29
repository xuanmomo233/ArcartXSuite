/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event.plugin;

import ink.ptms.chemdah.core.quest.objective.Objective;
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u001d\u0010\u0002\u001a\u00020\u00032\n\u0010\u0004\u001a\u0006\u0012\u0002\b\u00030\u00052\u0006\u0010\u0006\u001a\u00020\u0001H\u00a6\u0002\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0007\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/api/event/plugin/ObjectiveCallEvent;", "", "invoke", "", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "event", "Chemdah"})
public interface ObjectiveCallEvent {
    public void invoke(@NotNull Objective<?> var1, @NotNull Object var2);
}

