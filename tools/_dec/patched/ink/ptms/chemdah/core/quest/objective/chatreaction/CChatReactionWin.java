/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  me.clip.chatreaction.events.ReactionWinEvent
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.chatreaction;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import me.clip.chatreaction.events.ReactionWinEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="ChatReaction")
@MetaInfo(source="ChatReaction", name="ChatReaction \u7ade\u731c\u83b7\u80dc\u76ee\u6807", description={"\u73a9\u5bb6\u5728 ChatReaction \u7ade\u731c\u4e2d\u83b7\u80dc", "\u652f\u6301\u4f4d\u7f6e\u6761\u4ef6\u5224\u65ad", "\u9700\u8981 ChatReaction \u63d2\u4ef6\u652f\u6301"}, alias={"chatreaction\u83b7\u80dc", "\u7ade\u731c\u83b7\u80dc", "\u804a\u5929\u7ade\u731c"}, params={@ParamInfo(name="position", type="string", description="\u73a9\u5bb6\u6240\u5728\u4f4d\u7f6e")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/chatreaction/CChatReactionWin;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lme/clip/chatreaction/events/ReactionWinEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class CChatReactionWin
extends ObjectiveCountableI<ReactionWinEvent> {
    @NotNull
    public static final CChatReactionWin INSTANCE = new CChatReactionWin();
    @NotNull
    private static final String name = "chatreaction win";

    private CChatReactionWin() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<ReactionWinEvent> getEvent() {
        return ReactionWinEvent.class;
    }

    private static final Player _init_$lambda$0(ReactionWinEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getWinner();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, ReactionWinEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getWinner().getLocation();
    }

    static {
        INSTANCE.handler(CChatReactionWin::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", CChatReactionWin::_init_$lambda$1);
    }
}

