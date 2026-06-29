/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u804a\u5929\u76ee\u6807", description={"\u73a9\u5bb6\u53d1\u9001\u804a\u5929\u6d88\u606f", "\u652f\u6301\u6d88\u606f\u5185\u5bb9\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u804a\u5929\u6b21\u6570"}, alias={"\u804a\u5929", "\u53d1\u8a00", "\u8bf4\u8bdd"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u7684\u4f4d\u7f6e"), @ParamInfo(name="message", type="String", description="\u804a\u5929\u6d88\u606f\u5185\u5bb9")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\f\u001a\u00020\rX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerChat;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/AsyncPlayerChatEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "getPriority", "()Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "Chemdah"})
public final class IPlayerChat
extends ObjectiveCountableI<AsyncPlayerChatEvent> {
    @NotNull
    public static final IPlayerChat INSTANCE = new IPlayerChat();
    @NotNull
    private static final String name = "player chat";
    @NotNull
    private static final EventPriority priority = EventPriority.LOWEST;

    private IPlayerChat() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<AsyncPlayerChatEvent> getEvent() {
        return AsyncPlayerChatEvent.class;
    }

    @Override
    @NotNull
    public EventPriority getPriority() {
        return priority;
    }

    private static final Player _init_$lambda$0(AsyncPlayerChatEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, AsyncPlayerChatEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, AsyncPlayerChatEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getMessage();
    }

    private static final Object _init_$lambda$3(AsyncPlayerChatEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getMessage();
    }

    static {
        INSTANCE.handler(IPlayerChat::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerChat::_init_$lambda$1);
        INSTANCE.addCondition("message", "String", IPlayerChat::_init_$lambda$2);
        INSTANCE.addConditionVariable("message", IPlayerChat::_init_$lambda$3);
    }
}

