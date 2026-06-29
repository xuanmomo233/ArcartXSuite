/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api.event.plugin;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import java.util.List;
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u001f\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u00a6\u0002\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\t\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/api/event/plugin/CollectEvent;", "", "invoke", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quests", "", "Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
public interface CollectEvent {
    public void invoke(@NotNull PlayerProfile var1, @NotNull List<Quest> var2);
}

