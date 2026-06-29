/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u5207\u6362\u4e16\u754c\u76ee\u6807", description={"\u73a9\u5bb6\u5207\u6362\u4e16\u754c", "\u652f\u6301\u6e90\u4e16\u754c\u3001\u76ee\u6807\u4e16\u754c\u3001\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u5207\u6362\u6b21\u6570"}, alias={"\u6362\u4e16\u754c", "\u8de8\u4e16\u754c", "\u4e16\u754c\u8f6c\u79fb"}, params={@ParamInfo(name="position", type="Location", description="\u5207\u6362\u540e\u7684\u4f4d\u7f6e"), @ParamInfo(name="world", type="String", description="\u76ee\u6807\u4e16\u754c\u540d\u79f0\uff08\u4e0eworld:to\u76f8\u540c\uff09"), @ParamInfo(name="world:to", type="String", description="\u76ee\u6807\u4e16\u754c\u540d\u79f0"), @ParamInfo(name="world:from", type="String", description="\u6e90\u4e16\u754c\u540d\u79f0")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerChangeWorld;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerChangedWorldEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerChangeWorld
extends ObjectiveCountableI<PlayerChangedWorldEvent> {
    @NotNull
    public static final IPlayerChangeWorld INSTANCE = new IPlayerChangeWorld();
    @NotNull
    private static final String name = "change world";

    private IPlayerChangeWorld() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerChangedWorldEvent> getEvent() {
        return PlayerChangedWorldEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerChangedWorldEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerChangedWorldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerChangedWorldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getWorld().getName();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerChangedWorldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getWorld().getName();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerChangedWorldEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFrom().getName();
    }

    static {
        INSTANCE.handler(IPlayerChangeWorld::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerChangeWorld::_init_$lambda$1);
        INSTANCE.addCondition("world", "String", IPlayerChangeWorld::_init_$lambda$2);
        INSTANCE.addCondition("world:to", "String", IPlayerChangeWorld::_init_$lambda$3);
        INSTANCE.addCondition("world:from", "String", IPlayerChangeWorld::_init_$lambda$4);
    }
}

