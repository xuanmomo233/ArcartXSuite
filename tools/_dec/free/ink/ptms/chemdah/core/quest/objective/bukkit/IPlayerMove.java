/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u73a9\u5bb6\u79fb\u52a8\u76ee\u6807", description={"\u73a9\u5bb6\u79fb\u52a8", "\u652f\u6301\u8d77\u59cb\u4f4d\u7f6e\u3001\u76ee\u6807\u4f4d\u7f6e\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u79fb\u52a8\u6b21\u6570"}, alias={"\u79fb\u52a8", "\u884c\u8d70", "\u4f20\u9001\u68c0\u6d4b"}, params={@ParamInfo(name="position", type="Location", description="\u79fb\u52a8\u540e\u7684\u4f4d\u7f6e"), @ParamInfo(name="position:to", type="Location", description="\u79fb\u52a8\u7684\u76ee\u6807\u4f4d\u7f6e"), @ParamInfo(name="position:from", type="Location", description="\u79fb\u52a8\u7684\u8d77\u59cb\u4f4d\u7f6e")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0016\u0010\b\u001a\u00070\t\u00a2\u0006\u0002\b\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0014\u0010\r\u001a\u00020\u000eX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerMove;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerMoveEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "lock", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getLock", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "name", "", "getName", "()Ljava/lang/String;", "onQuit", "", "e", "Lorg/bukkit/event/player/PlayerQuitEvent;", "Chemdah"})
public final class IPlayerMove
extends ObjectiveCountableI<PlayerMoveEvent> {
    @NotNull
    public static final IPlayerMove INSTANCE = new IPlayerMove();
    @NotNull
    private static final String name = "player move";
    @NotNull
    private static final Baffle lock;

    private IPlayerMove() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerMoveEvent> getEvent() {
        return PlayerMoveEvent.class;
    }

    @NotNull
    public final Baffle getLock() {
        return lock;
    }

    @SubscribeEvent
    private final void onQuit(PlayerQuitEvent e) {
        lock.reset(e.getPlayer().getName());
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static final Player _init_$lambda$0(PlayerMoveEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        if (!BukkitEventKt.isMovement((PlayerMoveEvent)it)) return null;
        if (!lock.hasNext(it.getPlayer().getName())) return null;
        Player player2 = it.getPlayer();
        return player2;
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerMoveEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        Intrinsics.checkNotNull((Object)location);
        return location;
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerMoveEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        Location location = it.getTo();
        Intrinsics.checkNotNull((Object)location);
        return location;
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerMoveEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getFrom();
    }

    static {
        Baffle baffle = Baffle.of((long)200L, (TimeUnit)TimeUnit.MILLISECONDS);
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(200, TimeUnit.MILLISECONDS)");
        lock = baffle;
        INSTANCE.handler(IPlayerMove::_init_$lambda$0);
        INSTANCE.addCondition("position", "Location", IPlayerMove::_init_$lambda$1);
        INSTANCE.addCondition("position:to", "Location", IPlayerMove::_init_$lambda$2);
        INSTANCE.addCondition("position:from", "Location", IPlayerMove::_init_$lambda$3);
    }
}

