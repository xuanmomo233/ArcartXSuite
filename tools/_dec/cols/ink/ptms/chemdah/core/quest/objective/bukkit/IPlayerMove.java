/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
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
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0016\u0010\b\u001a\u00070\t\u00a2\u0006\u0002\b\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0014\u0010\r\u001a\u00020\u000eX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerMove;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerMoveEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "lock", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "getLock", "()Link/ptms/chemdah/taboolib/common5/Baffle;", "name", "", "getName", "()Ljava/lang/String;", "onQuit", "", "e", "Lorg/bukkit/event/player/PlayerQuitEvent;", "Chemdah"})
public final class IPlayerMove
extends ObjectiveCountableI<PlayerMoveEvent> {
    @NotNull
    public static final IPlayerMove INSTANCE = new IPlayerMove();
    @NotNull
    private static final String name = "player move";
    @NotNull
    private static final Class<PlayerMoveEvent> event = PlayerMoveEvent.class;
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
        return event;
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
        Player player = it.getPlayer();
        return player;
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerMoveEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getTo();
        Intrinsics.checkNotNull((Object)location);
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerMoveEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getTo();
        Intrinsics.checkNotNull((Object)location);
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerMoveEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getFrom();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.from");
        return inferArea.inside(location);
    }

    static {
        Baffle baffle = Baffle.of((long)200L, (TimeUnit)TimeUnit.MILLISECONDS);
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(200, TimeUnit.MILLISECONDS)");
        lock = baffle;
        INSTANCE.handler(IPlayerMove::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerMove::_init_$lambda$1);
        INSTANCE.addSimpleCondition("position:to", IPlayerMove::_init_$lambda$2);
        INSTANCE.addSimpleCondition("position:from", IPlayerMove::_init_$lambda$3);
    }
}

