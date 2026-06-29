/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.self;

import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(source="chemdah", name="\u5bf9\u8bdd\u7ed3\u675f\u76ee\u6807", description={"\u73a9\u5bb6\u6b63\u5e38\u7ed3\u675f\u5bf9\u8bdd", "\u652f\u6301\u4f4d\u7f6e\u3001\u5bf9\u8bddID\u7b49\u6761\u4ef6\u5224\u65ad", "\u9002\u7528\u4e8e Chemdah \u5185\u90e8\u5bf9\u8bdd\u7cfb\u7edf"}, alias={"\u5bf9\u8bdd\u7ed3\u675f", "\u5bf9\u8bdd\u5b8c\u6210", "conversation\u7ed3\u675f"}, params={@ParamInfo(name="position", type="location", description="\u68c0\u6d4b\u5bf9\u8bdd\u7ed3\u675f\u4f4d\u7f6e"), @ParamInfo(name="id", type="string", description="\u5bf9\u8bddID\uff0c\u7528\u4e8e\u8fc7\u6ee4\u7279\u5b9a\u5bf9\u8bdd")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/self/CConversationClosed;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CConversationClosed
extends ObjectiveCountableI<ConversationEvents.Closed> {
    @NotNull
    public static final CConversationClosed INSTANCE = new CConversationClosed();
    @NotNull
    private static final String name = "player conversation closed";

    private CConversationClosed() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ConversationEvents.Closed> getEvent() {
        return ConversationEvents.Closed.class;
    }

    private static final Player _init_$lambda$0(ConversationEvents.Closed it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return !it.getRefuse() ? it.getSession().getPlayer() : null;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ConversationEvents.Closed it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getSession().getOrigin();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, ConversationEvents.Closed it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getSession().getConversation().getId();
    }

    private static final Object _init_$lambda$3(ConversationEvents.Closed it) {
        Intrinsics.checkNotNullParameter((Object)((Object)it), (String)"it");
        return it.getSession().getConversation().getId();
    }

    static {
        INSTANCE.handler(CConversationClosed::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CConversationClosed::_init_$lambda$1);
        INSTANCE.addCondition("id", "String", CConversationClosed::_init_$lambda$2);
        INSTANCE.addConditionVariable("id", CConversationClosed::_init_$lambda$3);
    }
}

