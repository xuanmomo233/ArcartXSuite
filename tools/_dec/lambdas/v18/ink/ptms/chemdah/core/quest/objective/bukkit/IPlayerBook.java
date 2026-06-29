/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerEditBookEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.objective.bukkit;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI;
import ink.ptms.chemdah.core.quest.selector.InferArea;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u0005X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBook;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerEditBookEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBook
extends ObjectiveCountableI<PlayerEditBookEvent> {
    @NotNull
    public static final IPlayerBook INSTANCE = new IPlayerBook();
    @NotNull
    private static final String name = "edit book";
    @NotNull
    private static final Class<PlayerEditBookEvent> event = PlayerEditBookEvent.class;

    private IPlayerBook() {
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public Class<PlayerEditBookEvent> getEvent() {
        return event;
    }

    private static final Player _init_$lambda$0(PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Boolean _init_$lambda$1(Data data2, PlayerEditBookEvent e) {
        InferArea inferArea = data2.toPosition();
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        return inferArea.inside(location);
    }

    private static final Boolean _init_$lambda$2(Data data2, PlayerEditBookEvent e) {
        return data2.toBoolean() == e.isSigning();
    }

    private static final Boolean _init_$lambda$3(Data data2, PlayerEditBookEvent e) {
        return data2.toConditionNumber().check(e.getNewBookMeta().getPageCount());
    }

    private static final Boolean _init_$lambda$4(Data data2, PlayerEditBookEvent e) {
        return StringsKt.contains$default((CharSequence)String.valueOf(e.getNewBookMeta().getTitle()), (CharSequence)data2.toString(), (boolean)false, (int)2, null);
    }

    private static final Boolean _init_$lambda$5(Data data2, PlayerEditBookEvent e) {
        return StringsKt.contains$default((CharSequence)e.getNewBookMeta().getPages().toString(), (CharSequence)data2.toString(), (boolean)false, (int)2, null);
    }

    private static final Object _init_$lambda$6(PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewBookMeta().getPageCount();
    }

    private static final Object _init_$lambda$7(PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getNewBookMeta().getTitle());
    }

    private static final Object _init_$lambda$8(PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewBookMeta().getPages().toString();
    }

    static {
        INSTANCE.handler(IPlayerBook::_init_$lambda$0);
        INSTANCE.addSimpleCondition("position", IPlayerBook::_init_$lambda$1);
        INSTANCE.addSimpleCondition("signing", IPlayerBook::_init_$lambda$2);
        INSTANCE.addSimpleCondition("page", IPlayerBook::_init_$lambda$3);
        INSTANCE.addSimpleCondition("title", IPlayerBook::_init_$lambda$4);
        INSTANCE.addSimpleCondition("content", IPlayerBook::_init_$lambda$5);
        INSTANCE.addConditionVariable("page", IPlayerBook::_init_$lambda$6);
        INSTANCE.addConditionVariable("title", IPlayerBook::_init_$lambda$7);
        INSTANCE.addConditionVariable("content", IPlayerBook::_init_$lambda$8);
    }
}

