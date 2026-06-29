/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerEditBookEvent
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
import org.bukkit.event.player.PlayerEditBookEvent;
import org.jetbrains.annotations.NotNull;

@Dependency(plugin="minecraft")
@MetaInfo(name="\u7f16\u8f91\u4e66\u7c4d\u76ee\u6807", description={"\u73a9\u5bb6\u7f16\u8f91\u6216\u7b7e\u7f72\u4e66\u7c4d\u65f6\u89e6\u53d1", "\u652f\u6301\u7b7e\u7f72\u72b6\u6001\u3001\u9875\u6570\u3001\u6807\u9898\u3001\u5185\u5bb9\u7b49\u6761\u4ef6\u5224\u65ad", "\u53ef\u7edf\u8ba1\u7f16\u8f91\u6b21\u6570"}, alias={"\u5199\u4e66", "\u7f16\u8f91\u4e66", "\u7b7e\u7f72\u4e66\u7c4d"}, params={@ParamInfo(name="position", type="Location", description="\u73a9\u5bb6\u4f4d\u7f6e"), @ParamInfo(name="signing", type="Boolean", description="\u662f\u5426\u7b7e\u7f72"), @ParamInfo(name="page", type="Number", description="\u9875\u6570"), @ParamInfo(name="title", type="String", description="\u4e66\u7c4d\u6807\u9898"), @ParamInfo(name="content", type="String", description="\u4e66\u7c4d\u5185\u5bb9")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003R\u001a\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00020\u00058VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u00020\tX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/bukkit/IPlayerBook;", "Link/ptms/chemdah/core/quest/objective/ObjectiveCountableI;", "Lorg/bukkit/event/player/PlayerEditBookEvent;", "()V", "event", "Ljava/lang/Class;", "getEvent", "()Ljava/lang/Class;", "name", "", "getName", "()Ljava/lang/String;", "Chemdah"})
public final class IPlayerBook
extends ObjectiveCountableI<PlayerEditBookEvent> {
    @NotNull
    public static final IPlayerBook INSTANCE = new IPlayerBook();
    @NotNull
    private static final String name = "edit book";

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
        return PlayerEditBookEvent.class;
    }

    private static final Player _init_$lambda$0(PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer();
    }

    private static final Object _init_$lambda$1(PlayerProfile playerProfile2, Task task, PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getPlayer().getLocation();
    }

    private static final Object _init_$lambda$2(PlayerProfile playerProfile2, Task task, PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.isSigning();
    }

    private static final Object _init_$lambda$3(PlayerProfile playerProfile2, Task task, PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewBookMeta().getPageCount();
    }

    private static final Object _init_$lambda$4(PlayerProfile playerProfile2, Task task, PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return String.valueOf(it.getNewBookMeta().getTitle());
    }

    private static final Object _init_$lambda$5(PlayerProfile playerProfile2, Task task, PlayerEditBookEvent it) {
        Intrinsics.checkNotNullParameter((Object)playerProfile2, (String)"<anonymous parameter 0>");
        Intrinsics.checkNotNullParameter((Object)task, (String)"<anonymous parameter 1>");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return it.getNewBookMeta().getPages().toString();
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
        INSTANCE.addCondition("position", "Location", IPlayerBook::_init_$lambda$1);
        INSTANCE.addCondition("signing", "Boolean", IPlayerBook::_init_$lambda$2);
        INSTANCE.addCondition("page", "Number", IPlayerBook::_init_$lambda$3);
        INSTANCE.addCondition("title", "String", IPlayerBook::_init_$lambda$4);
        INSTANCE.addCondition("content", "String", IPlayerBook::_init_$lambda$5);
        INSTANCE.addConditionVariable("page", IPlayerBook::_init_$lambda$6);
        INSTANCE.addConditionVariable("title", IPlayerBook::_init_$lambda$7);
        INSTANCE.addConditionVariable("content", IPlayerBook::_init_$lambda$8);
    }
}

